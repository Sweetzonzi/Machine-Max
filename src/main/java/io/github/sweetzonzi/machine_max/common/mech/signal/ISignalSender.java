package io.github.sweetzonzi.machine_max.common.mech.signal;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SubsystemController;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBox;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public interface ISignalSender {
    /**
     * 在此填入各个信号名对应的接收者名列表，用于自动组织信号传输关系。<p>
     * Return a map of signal names to a list of receiver names here, to automatically organize signal transfer.
     *
     * @return 信号频道->接收者名称列表 Map of signal channels to a list of receiver names.
     */
    Map<String, List<String>> getTargetNames();

    Map<String, Map<String, ISignalReceiver>> getTargets();//信号频道名称->接收者名称->接收者 Signal channel name -> receiver name -> receiver

    /**
     * 解析本发送者在某个输出频道上可达的全部终端，按终端实际到达的频道分组。<br>
     * 展开规则见 {@link #resolveTargets(String, List)}。
     *
     * @param outputChannel 发送者的输出频道
     * @return 到达频道 → 终端列表；该频道没有首跳或线条全部断开时返回空映射
     */
    default Map<String, List<ISignalReceiver>> resolveTargets(String outputChannel) {
        return resolveTargets(outputChannel, false);
    }

    /**
     * 同上，并决定总线实例本身算不算结果。<br>
     * {@code false} 返回叶子接收者（执行者 / 发现目标），总线只作为分发点被穿过；{@code true} 返回线网上的
     * <b>投递点</b>：叶子 + 途经的总线实例本身，端口永远不出现。两者的差集恰好是总线实例。
     * <p>
     * 投递点这一档用于事件车道与交互区的激活判据：一条频道只到达总线（例如 {@code local} / {@code global}）
     * 时，事件在总线上就会产生动作（部件交给状态机、装配体广播全零件），所以总线本身就是终点。值车道
     * 的执行器发现仍用 {@code false}：总线没有可执行的东西。
     *
     * @param outputChannel 发送者的输出频道
     * @param includeBuses  是否把途经的总线实例也记为终端
     * @return 到达频道 → 投递点列表；该频道没有首跳或线条全部断开时返回空映射
     */
    default Map<String, List<ISignalReceiver>> resolveTargets(String outputChannel, boolean includeBuses) {
        if (outputChannel == null || outputChannel.isEmpty()) return Map.of();
        return resolveFirstHop(getTargets().get(outputChannel), outputChannel, includeBuses);
    }

    /**
     * 同上，但只沿 {@code firstHopNames} 列出的那几条首跳推进。<br>
     * 用于同一个频道被不同控制组指向不同接收者时，按组查看各自的终端；名字按给定顺序保留，
     * 目标表里不存在的名字直接跳过（建表期已记录）。空列表表示这条频道在本组不到达任何终端。
     * <p>
     * <b>只读：</b>只遍历目标表与连接状态，不写信号槽、不建频道、不写 MoLang 存储、不触发
     * {@link ISignalReceiver#onSignalUpdated}，因此可以在建立绑定前反复调用。
     *
     * @param outputChannel 发送者的输出频道
     * @param firstHopNames 本次要考虑的首跳目标名，不可为 {@code null}
     * @return 到达频道 → 终端列表
     */
    default Map<String, List<ISignalReceiver>> resolveTargets(String outputChannel, List<String> firstHopNames) {
        return resolveTargets(outputChannel, firstHopNames, false);
    }

    /**
     * 白名单与投递点两档的组合：只沿 {@code firstHopNames} 列出的首跳推进，并在结果里计入途经的总线实例。
     *
     * @param outputChannel 发送者的输出频道
     * @param firstHopNames 本次要考虑的首跳目标名，不可为 {@code null}
     * @param includeBuses  是否把途经的总线实例也记为终端，语义见 {@link #resolveTargets(String, boolean)}
     * @return 到达频道 → 投递点列表
     */
    default Map<String, List<ISignalReceiver>> resolveTargets(String outputChannel, List<String> firstHopNames,
                                                              boolean includeBuses) {
        Objects.requireNonNull(firstHopNames, "firstHopNames");
        if (outputChannel == null || outputChannel.isEmpty()) return Map.of();
        Map<String, ISignalReceiver> union = getTargets().get(outputChannel);
        if (union == null || union.isEmpty()) return Map.of();
        Map<String, ISignalReceiver> firstHop = new LinkedHashMap<>(firstHopNames.size());
        for (String name : firstHopNames) {
            ISignalReceiver receiver = union.get(name);
            if (receiver != null) firstHop.put(name, receiver);
        }
        return resolveFirstHop(firstHop, outputChannel, includeBuses);
    }

    /**
     * 从一组已解析的首跳推进到终端：遇到 {@link SignalPort} 时沿对侧端口继续，并用对侧端口的转译表得到
     * 到达频道；遇到 {@link ISignalBus} 时按订阅者声明的接受条件展开 {@code global}。端口与总线转发按各自的
     * 接受规则（{@link ISignalReceiver#acceptAllRoutingInput()}、{@link ISignalReceiver#acceptAllBroadcastInput()}
     * 与 {@link ISignalReceiver#getAcceptedChannels()}）筛选，显式直连直接以配置的接收者为终端。
     * <p>
     * 同一对象在同一到达频道只出现一次，经不同到达频道可达时各保留一项；环路按「对象身份、频道」终止。
     * 返回的映射与列表不可变，频道顺序与频道内的终端顺序均为首次出现顺序。
     *
     * @param firstHop      首跳实例，{@code null} 或空表示这条线路不存在
     * @param outputChannel 首跳的到达频道
     * @param includeBuses  是否把途经的总线实例也记为终端；总线先记自己，再展开订阅者
     * @return 不可变的「到达频道 → 终端列表」
     */
    private static Map<String, List<ISignalReceiver>> resolveFirstHop(
            @Nullable Map<String, ISignalReceiver> firstHop, String outputChannel, boolean includeBuses) {
        if (firstHop == null || firstHop.isEmpty()) return Map.of();

        Map<String, List<ISignalReceiver>> terminals = new LinkedHashMap<>();
        Map<ISignalReceiver, Set<String>> visited = new IdentityHashMap<>();
        for (ISignalReceiver receiver : firstHop.values()) {
            resolveSignalLine(receiver, outputChannel, terminals, visited, includeBuses);
        }
        if (terminals.isEmpty()) return Map.of();

        Map<String, List<ISignalReceiver>> frozen = new LinkedHashMap<>();
        terminals.forEach((channel, receivers) -> frozen.put(channel, List.copyOf(receivers)));
        return Collections.unmodifiableMap(frozen);
    }

    /** 从 {@code receiver} 开始沿一条线路推进；{@code channel} 是它实际收到的频道。 */
    private static void resolveSignalLine(ISignalReceiver receiver, String channel,
                                          Map<String, List<ISignalReceiver>> terminals,
                                          Map<ISignalReceiver, Set<String>> visited, boolean includeBuses) {
        if (!visited.computeIfAbsent(receiver, k -> new HashSet<>(2)).add(channel)) return;

        if (receiver instanceof SignalPort port) {
            SignalPort other = port.getAttachedPort();
            if (other == null) return;
            String translated = other.translateChannel(channel);
            Map<String, ISignalReceiver> nextHop = other.getTargets().get(translated);
            if (nextHop == null) return;
            for (ISignalReceiver next : nextHop.values()) {
                if (next.acceptAllRoutingInput() || next.getAcceptedChannels().contains(translated)) {
                    resolveSignalLine(next, translated, terminals, visited, includeBuses);
                }
            }
            return;
        }
        if (receiver instanceof ISignalBus bus) {
            // 总线自身也是投递点：事件在这里就会产生动作（部件交给状态机、装配体广播全零件）。
            if (includeBuses) terminals.computeIfAbsent(channel, k -> new ArrayList<>()).add(bus);
            // 订阅集合本身没有业务顺序，按部件 UUID、SubPart 名、接收者寻址名固定候选顺序。
            List<ISignalReceiver> subscribers = new ArrayList<>(bus.getAllSubscribers());
            subscribers.sort(ISignalSender::compareBusSubscribers);
            for (ISignalReceiver subscriber : subscribers) {
                if (subscriber.acceptAllBroadcastInput() || subscriber.getAcceptedChannels().contains(channel)) {
                    resolveSignalLine(subscriber, channel, terminals, visited, includeBuses);
                }
            }
            return;
        }
        terminals.computeIfAbsent(channel, k -> new ArrayList<>()).add(receiver);
    }

    /** 稳定身份比较：部件 UUID → SubPart 名 → 寻址名，与哈希顺序无关。用于总线订阅者。 */
    public static int compareBusSubscribers(ISignalReceiver a, ISignalReceiver b) {
        return compareIdentity(a, b);
    }

    /** 同一把尺子，用于频道槽位排序（槽位键是 {@link ISignalSender}）。 */
    public static int compareSenders(ISignalSender a, ISignalSender b) {
        return compareIdentity(a, b);
    }

    private static int compareIdentity(Object a, Object b) {
        int result = ownerPartKey(a).compareTo(ownerPartKey(b));
        if (result != 0) return result;
        result = subPartName(a).compareTo(subPartName(b));
        if (result != 0) return result;
        return addressOf(a).compareTo(addressOf(b));
    }

    private static String ownerPartKey(Object receiver) {
        SubPart subPart = subPartOf(receiver);
        return subPart == null ? "" : subPart.getPart().uuid.toString();
    }

    private static String subPartName(Object receiver) {
        SubPart subPart = subPartOf(receiver);
        return subPart == null ? "" : subPart.name;
    }

    private static SubPart subPartOf(Object receiver) {
        return receiver instanceof ISignalSender sender ? sender.getSubPart() : null;
    }

    private static String addressOf(Object receiver) {
        return receiver instanceof ISignalReceiver r ? r.getSignalAddress() : receiver.getClass().getName();
    }

    /**
     * 将发送的信号输出类型全部重置为空信号
     */
    default void resetSignalOutputs() {
        if (this instanceof SignalPort) return;//信号端口只应转发信号，不应对其输出信号进行操作
        for (Map.Entry<String, Map<String, ISignalReceiver>> entry : getTargets().entrySet()) {
            entry.getValue().forEach((receiverName, signalReceiver) -> {
                var emptySignal = EmptySignal.INSTANCE;
                signalReceiver.getSignalInputChannels().computeIfAbsent(entry.getKey(), k -> new SignalChannel()).put(this, emptySignal);
                signalReceiver.onSignalUpdated(entry.getKey(), this);
            });
        }
    }

    /**
     * 将信号发送到所有接收此信号的目标，所有目标收到同名同数值的信号
     *
     * @param signalChannel 信号频道名称
     * @param signalValue   信号值
     */
    default void sendSignalToAllTargets(String signalChannel, Object signalValue) {
        Map<String, ISignalReceiver> targets = getTargets().get(signalChannel);
        if (targets == null) return;
        for (ISignalReceiver target : targets.values()) {
            sendSignalToTarget(signalChannel, target, signalValue);
        }
    }

    /**
     * 将信号发送到已解析的接收者实例，用于发送者自己保存了执行器引用的场景。<br>
     * 不查首跳表、不按名字重新寻址，直接写该实例的输入槽并同步处理。
     * <p>
     * <b>结构变化契约：</b>旧引用在同一轮结构变化后失效。发送者必须先在主线程重新
     * {@link #resolveTargets(String) 解析}并保存接收者引用，再继续发送；物理线程只允许使用
     * 当前已发布的绑定快照向执行器发送命令。
     *
     * @param signalChannel 接收者的输入频道（到达频道）
     * @param target        已解析的接收者实例
     * @param signalValue   信号值
     * @return 接收者的处理结果
     */
    default SignalResult sendSignalToTarget(String signalChannel, ISignalReceiver target, Object signalValue) {
        return deliverSignal(signalChannel, target, signalValue);
    }

    /**
     * 将信号发送到指定接收者，可用于发送同名不同值信号给不同目标
     *
     * @param signalChannel 信号频道
     * @param targetName    接收者名称
     * @param signalValue   信号值
     * @return 接收者的处理结果
     */
    default SignalResult sendSignalToTarget(String signalChannel, String targetName, Object signalValue) {
        Map<String, ISignalReceiver> targets = getTargets().get(signalChannel);
        if (targets == null) return SignalResult.PASS;
        ISignalReceiver signalReceiver = targets.get(targetName);
        if (signalReceiver == null) return SignalResult.PASS;
        return deliverSignal(signalChannel, signalReceiver, signalValue);
    }

    /**
     * 在频道 {@code signalChannel} 上投递一次事件：沿该频道的全部首跳推进，凡是接收者都收，
     * 传输节点额外转发。投递不写任何输入槽、不写存储表、不参与 MoLang。
     * <p>
     * 与 {@link #sendSignalToAllTargets(String, Object)} 的区别是语义而不是目标：值车道的槽里只留
     * "这个来源现在是多少"，相同值重复发送不产生投递；事件车道的每一次调用都是一次新事件，
     * 同样的负载连发两次就到达两次。只读的终端发现仍然是 {@link #resolveTargets(String)}。
     * <p>
     * 一次顶层调用打开一个投递会话（见 {@code EventDispatch}），重访的 {@code (接收者, 到达频道)}
     * 组合被抑制，因此环路与菱形接线都不会重复投递。负载原样交给接收者，纯触发传 {@code null}。
     * <p>
     * <b>线程：</b>首跳表由主线程整体替换，因此按名投递只在主线程调用；物理步只允许用
     * {@link #emitSignal(String, ISignalReceiver, Object)} 按实例发给不触碰动画状态机的接收者。
     *
     * @param signalChannel 发送者的输出频道
     * @param payload       事件负载，可为 {@code null}
     */
    default void emitSignal(String signalChannel, Object payload) {
        if (signalChannel == null || signalChannel.isEmpty()) return;
        Map<String, ISignalReceiver> firstHop = getTargets().get(signalChannel);
        if (firstHop == null || firstHop.isEmpty()) return;
        EventDispatch session = EventDispatch.current();
        session.begin();
        try {
            for (ISignalReceiver target : firstHop.values()) {
                EventDispatch.dispatch(target, signalChannel, this, payload);
            }
        } finally {
            session.end();
        }
    }

    /**
     * 同上，但只沿首跳表里的 {@code targetName} 这一条推进。表里没有该名字时静默返回。
     *
     * @param signalChannel 发送者的输出频道
     * @param targetName    该频道某个首跳的目标名
     * @param payload       事件负载，可为 {@code null}
     */
    default void emitSignal(String signalChannel, String targetName, Object payload) {
        if (signalChannel == null || signalChannel.isEmpty() || targetName == null) return;
        Map<String, ISignalReceiver> firstHop = getTargets().get(signalChannel);
        if (firstHop == null) return;
        ISignalReceiver target = firstHop.get(targetName);
        if (target == null) return;
        emitSignal(signalChannel, target, payload);
    }

    /**
     * 向已解析的接收者实例投递一次事件，不查首跳表、不按名字重新寻址。
     * <p>
     * 接收者是总线时先收下再分发订阅者，是端口时按对侧表继续转发，是叶子时直接回调。物理步
     * 向执行器投递反馈事件走这一档：实例引用在结构变化后失效，发送者须先在主线程重新解析并保存。
     *
     * @param signalChannel 接收者的到达频道
     * @param target        已解析的接收者实例
     * @param payload       事件负载，可为 {@code null}
     */
    default void emitSignal(String signalChannel, ISignalReceiver target, Object payload) {
        if (signalChannel == null || signalChannel.isEmpty() || target == null) return;
        EventDispatch session = EventDispatch.current();
        session.begin();
        try {
            EventDispatch.dispatch(target, signalChannel, this, payload);
        } finally {
            session.end();
        }
    }

    /** 写接收者输入槽并同步触发接收者处理。 */
    private SignalResult deliverSignal(String signalChannel, ISignalReceiver signalReceiver, Object signalValue) {
        signalReceiver.getSignalInputChannels().computeIfAbsent(signalChannel, k -> new SignalChannel()).put(this, signalValue);
        // 存储写入由接收者自述（RegularPart / SubsystemController 覆写 getSignalStorage）
        Map<String, Object> storage = signalReceiver.getSignalStorage();
        if (storage != null) storage.put(signalChannel, signalValue);
        return signalReceiver.onSignalUpdated(signalChannel, this);
    }

    /**
     * 获取此发送者所属的 SubPart。
     * 没有所属 SubPart 的发送者（如 {@link SubsystemController}）返回 null。
     */
    @Nullable
    default SubPart getSubPart(){
        return null;
    }

    /**
     * 设置信号传输目标。
     * <p>
     * 名字的作用域是发送者所在的<b>部件（Part）</b>：子系统 / 交互区 / 连接点三类名字都在整部件内解析，
     * 跨子零件的具名引用因此可达。名字在 Part 内唯一（解码期校验保证），三类名字共用一套命名空间。
     * <p>
     * 聚合一趟 {@code subParts} 现场得到三张表，不保留常驻索引：解析只在建首跳表时发生（attach、换组、
     * 结构变化），不需要缓存，也没有需要在子零件销毁/重建时清理的清理点。
     * <p>
     * 先在局部表完成解析，再整体替换现有首跳表，配置里删掉的频道不会留在表中。目标表由主线程构建，
     * 物理线程不读取首跳表（反馈与状态投递在主线程 {@code onTick} 完成）。
     */
    default void setTargetFromNames() {
        SubPart owner = getSubPart();
        if (owner == null) return;
        Map<String, AbstractSubsystem> subSystems = new HashMap<>();
        Map<String, InteractBox> interactBoxes = new HashMap<>();
        Map<String, SignalPort> ports = new HashMap<>();
        for (SubPart subPart : owner.part.subParts.values()) {
            for (AbstractSubsystem subsystem : subPart.ownSubsystems()) {
                subSystems.put(subsystem.getName(), subsystem);
            }
            if (subPart.interactBoxes != null) interactBoxes.putAll(subPart.interactBoxes);
            subPart.connectors.forEach((name, connector) -> ports.put(name, connector.signalPort));
        }
        Map<String, Map<String, ISignalReceiver>> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : getTargetNames().entrySet()) {
            // 保序：接收者列表的顺序就是 getTargetNames() 里声明的顺序。交互区按此顺序逐个尝试目标，
            // 座位一类的多目标交互区（同一扇门通往多个座位）依赖它决定先坐哪一个。
            Map<String, ISignalReceiver> signalReceivers = new LinkedHashMap<>(2);
            if (entry.getKey().isEmpty()) continue;
            getReceiversFromNames(entry.getValue(), owner, subSystems, interactBoxes, ports).forEach(receiver -> signalReceivers.put(receiver.getSignalAddress(), receiver));
            rebuilt.put(entry.getKey(), signalReceivers);
        }
        getTargets().clear();
        getTargets().putAll(rebuilt);
    }

    default List<ISignalReceiver> getReceiversFromNames(
            List<String> targetNames,
            SubPart ownerPart,
            Map<String, AbstractSubsystem> subSystems,
            Map<String, InteractBox> interactBoxes,
            Map<String, SignalPort> ports) {
        List<ISignalReceiver> targets = new ArrayList<>();
        for (String targetName : targetNames) {
            if (targetName.equals("global")) {
                if (ownerPart.part.assembly != null)
                    targets.add(ownerPart.part.assembly.getSubsystemController());
            } else if (targetName.equals("local")) {
                targets.add(ownerPart.part);
            } else if (subSystems.containsKey(targetName)) {
                AbstractSubsystem subSystem = subSystems.get(targetName);
                if (subSystem != null) {
                    targets.add(subSystem);
                }
            } else if (interactBoxes.containsKey(targetName)) {
                // 交互区只发不收：它是玩家点击的入口，点击由 InteractBox#interact 直接调子系统的 onInteract，
                // 不经过信号槽。把它写成信号目标的内容需要改成指向真正的接收者。
                MachineMax.LOGGER.error("目标 {} 是交互区，交互区只发不收（发送者 {}，所在 SubPart {}）",
                        targetName,
                        this instanceof AbstractSubsystem subsystem ? subsystem.getName() : getClass().getSimpleName(),
                        ownerPart.name);
            } else if (ports.containsKey(targetName)) {
                SignalPort signalPort = ports.get(targetName);
                targets.add(signalPort);
            } else MachineMax.LOGGER.error("未在部件内找到目标端口、子系统或交互区: {}（发送者 {}，所在 SubPart {}）",
                    targetName,
                    this instanceof AbstractSubsystem subsystem ? subsystem.getName() : getClass().getSimpleName(),
                    ownerPart.name);
        }
        return targets;
    }
}
