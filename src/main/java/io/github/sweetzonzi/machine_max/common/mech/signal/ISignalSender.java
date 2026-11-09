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
     * 首跳取自 {@link #getTargets()}；遇到 {@link SignalPort} 时沿对侧端口继续，并用对侧端口的转译表得到
     * 到达频道；遇到 {@link ISignalBus} 时按订阅者声明的接受条件展开 {@code global}。端口与总线转发按各自的
     * 接受规则（{@link ISignalReceiver#acceptAllRoutingInput()}、{@link ISignalReceiver#acceptAllBroadcastInput()}
     * 与 {@link ISignalReceiver#getAcceptedChannels()}）筛选，显式直连直接以配置的接收者为终端。
     * <p>
     * <b>只读：</b>只遍历目标表与连接状态，不写信号槽、不建频道、不写 MoLang 存储、不触发
     * {@link ISignalReceiver#onSignalUpdated}，因此可以在建立绑定前反复调用。同一对象在同一到达频道只出现
     * 一次，经不同到达频道可达时各保留一项；环路按「对象身份、频道」终止。返回的映射与列表不可变，
     * 频道顺序与频道内的终端顺序均为首次出现顺序。
     * <p>
     * <b>调用线程：</b>主线程（目标表与订阅表由主线程构建）。
     *
     * @param outputChannel 发送者的输出频道
     * @return 到达频道 → 终端列表；该频道没有首跳或线条全部断开时返回空映射
     */
    default Map<String, List<ISignalReceiver>> resolveTargets(String outputChannel) {
        if (outputChannel == null || outputChannel.isEmpty()) return Map.of();
        Map<String, ISignalReceiver> firstHop = getTargets().get(outputChannel);
        if (firstHop == null || firstHop.isEmpty()) return Map.of();

        Map<String, List<ISignalReceiver>> terminals = new LinkedHashMap<>();
        Map<ISignalReceiver, Set<String>> visited = new IdentityHashMap<>();
        for (ISignalReceiver receiver : firstHop.values()) {
            resolveSignalLine(receiver, outputChannel, terminals, visited);
        }
        if (terminals.isEmpty()) return Map.of();

        Map<String, List<ISignalReceiver>> frozen = new LinkedHashMap<>();
        terminals.forEach((channel, receivers) -> frozen.put(channel, List.copyOf(receivers)));
        return Collections.unmodifiableMap(frozen);
    }

    /** 从 {@code receiver} 开始沿一条线路推进；{@code channel} 是它实际收到的频道。 */
    private static void resolveSignalLine(ISignalReceiver receiver, String channel,
                                          Map<String, List<ISignalReceiver>> terminals,
                                          Map<ISignalReceiver, Set<String>> visited) {
        if (!visited.computeIfAbsent(receiver, k -> new HashSet<>(2)).add(channel)) return;

        if (receiver instanceof SignalPort port) {
            SignalPort other = port.getAttachedPort();
            if (other == null) return;
            String translated = other.translateChannel(channel);
            Map<String, ISignalReceiver> nextHop = other.getTargets().get(translated);
            if (nextHop == null) return;
            for (ISignalReceiver next : nextHop.values()) {
                if (next.acceptAllRoutingInput() || next.getAcceptedChannels().contains(translated)) {
                    resolveSignalLine(next, translated, terminals, visited);
                }
            }
            return;
        }
        if (receiver instanceof ISignalBus bus) {
            // 订阅集合本身没有业务顺序，按部件 UUID、SubPart 名、接收者寻址名固定候选顺序。
            List<ISignalReceiver> subscribers = new ArrayList<>(bus.getAllSubscribers());
            subscribers.sort(ISignalSender::compareBusSubscribers);
            for (ISignalReceiver subscriber : subscribers) {
                if (subscriber.acceptAllBroadcastInput() || subscriber.getAcceptedChannels().contains(channel)) {
                    resolveSignalLine(subscriber, channel, terminals, visited);
                }
            }
            return;
        }
        terminals.computeIfAbsent(channel, k -> new ArrayList<>()).add(receiver);
    }

    private static int compareBusSubscribers(ISignalReceiver a, ISignalReceiver b) {
        int result = ownerPartKey(a).compareTo(ownerPartKey(b));
        if (result != 0) return result;
        SubPart partA = subPartOf(a);
        SubPart partB = subPartOf(b);
        result = (partA == null ? "" : partA.name).compareTo(partB == null ? "" : partB.name);
        if (result != 0) return result;
        return a.getSignalAddress().compareTo(b.getSignalAddress());
    }

    private static String ownerPartKey(ISignalReceiver receiver) {
        SubPart subPart = subPartOf(receiver);
        return subPart == null ? "" : subPart.getPart().uuid.toString();
    }

    private static SubPart subPartOf(ISignalReceiver receiver) {
        return receiver instanceof ISignalSender sender ? sender.getSubPart() : null;
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
     * 先在局部表完成解析，再整体替换现有首跳表，配置里删掉的频道不会留在表中。目标表由主线程构建，
     * 物理线程不读取首跳表（反馈与状态投递在主线程 {@code onTick} 完成）。
     */
    default void setTargetFromNames() {
        if (getSubPart() != null) {
            Map<String, AbstractSubsystem> subSystems = getSubPart().subsystems;
            Map<String, InteractBox> interactBoxes = new HashMap<>();
            if (getSubPart().interactBoxes != null)
                interactBoxes.putAll(getSubPart().interactBoxes);
            Map<String, SignalPort> ports = new HashMap<>();
            getSubPart().connectors.forEach((name, connector) -> ports.put(name, connector.signalPort));
            Map<String, Map<String, ISignalReceiver>> rebuilt = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : getTargetNames().entrySet()) {
                // 保序：接收者列表的顺序就是 getTargetNames() 里声明的顺序。交互区按此顺序逐个尝试目标，
                // 座位一类的多目标交互区（同一扇门通往多个座位）依赖它决定先坐哪一个。
                Map<String, ISignalReceiver> signalReceivers = new LinkedHashMap<>(2);
                if (entry.getKey().isEmpty()) continue;
                getReceiversFromNames(entry.getValue(), getSubPart(), subSystems, interactBoxes, ports).forEach(receiver -> signalReceivers.put(receiver.getSignalAddress(), receiver));
                rebuilt.put(entry.getKey(), signalReceivers);
            }
            getTargets().clear();
            getTargets().putAll(rebuilt);
        }
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
                InteractBox interactBox = interactBoxes.get(targetName);
                targets.add(interactBox);
            } else if (ports.containsKey(targetName)) {
                SignalPort signalPort = ports.get(targetName);
                targets.add(signalPort);
            } else MachineMax.LOGGER.error("未在部件内找到目标端口、子系统或交互区: {}", targetName);
        }
        return targets;
    }
}
