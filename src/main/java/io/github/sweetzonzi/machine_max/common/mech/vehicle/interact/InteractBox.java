package io.github.sweetzonzi.machine_max.common.mech.vehicle.interact;

import cn.solarmoon.spark_core.molang.MolangContextRegistry;
import cn.solarmoon.spark_core.molang.runtime.MolangExpression;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalBus;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.InteractBoxAttr;
import lombok.Getter;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 玩家交互判定区：一块模型骨骼包围出的空间，配合射线（{@code accurate}）或碰撞箱接触（{@code fast}）被选中，
 * 点击时把 {@code signal_targets} 解析成两档并分别处理：到达总线的频道沿事件车道投递，到达子系统的终端
 * 依次询问 {@link AbstractSubsystem#onInteract(LivingEntity)}。
 *
 * <p><b>两档由一次投递点遍历（{@code resolveTargets(channel, true)}）分开：</b></p>
 * <ul>
 *   <li><b>事件线</b>：结果里出现总线实例（{@code local} = 所属 Part、{@code global} = 装配体，或经连接点
 *       到达的对侧总线）。点击时经 {@code emitSignal(频道)} 沿实际接线投递，负载为 {@code null}，不写输入槽；
 *       频道名就是动画状态机的事件名。多次点击每次都到达。</li>
 *   <li><b>交互目标</b>：结果是终端子系统（{@code signal_targets} 直接写子系统名，或经连接点转译后到达
 *       子系统）。结构变化时按声明序展平去重，点击时依次询问 {@code onInteract}：第一个 {@link SignalResult#CONSUME}
 *       结束遍历，{@link SignalResult#FAIL} / {@link SignalResult#PASS} 继续试下一个。</li>
 * </ul>
 *
 * <p><b>能不能交互</b> = {@code condition} 成立，且（至少一条频道到达总线，或至少一个终端子系统此刻
 * {@link AbstractSubsystem#isInteractable()} 可交互）。判据读的是解析结果而不是 {@code signal_targets} 里的
 * 字面量，因此跨零件的事件线只要接上了就算数，没接上就不亮提示。内容门控 {@code condition} 的 MoLang 表达式
 * 主线程每 tick 求值一次；写错会使交互区恒不可用并留错误日志。</p>
 *
 * <p><b>只发不收：</b>交互区不是信号接收者。点击只投递事件并调用 {@code onInteract}，不写任何人的输入槽，
 * 也不在槽里留下一个持有实体的交互值。</p>
 *
 * <p><b>线程：</b>{@link #setTargetFromNames()}、{@link #updateCondition()}、{@link #interact(LivingEntity)}
 * 与 {@link #canInteract()} 都在主线程；物理线程只做"这个子形状属于哪个交互区"的几何筛选，不读本类的状态。</p>
 */
@Getter
public class InteractBox implements ISignalSender {
    public final String name;
    public final InteractBoxAttr attr;
    public final Map<String, List<String>> targetNames;
    /** 首跳表：频道 → 目标名 → 目标实例，由 {@link #setTargetFromNames()} 保序写入。解析投递点与调试都读它。 */
    public final Map<String, Map<String, ISignalReceiver>> targets = new LinkedHashMap<>();
    public final SubPart subPart;
    public final InteractMode interactMode;
    /** 到达总线的频道：点击时作为事件名沿接线投递，并计入激活判据。 */
    private volatile List<String> eventChannels = List.of();
    /** 解析后的交互目标：只保留子系统，按 {@code signal_targets} 的频道与目标声明序展平。 */
    private volatile List<AbstractSubsystem> interactTargets = List.of();

    /** {@code condition} 为空或字面量 {@code true}：不门控，恒可用。 */
    private final boolean alwaysActive;
    /** 预编译的门控表达式；不门控或编译失败时为 {@code null}。 */
    @Nullable
    private final MolangExpression condition;
    /** 门控表达式最近一次求值结果。编译失败时恒为 {@code false}，即该交互区永远不可交互。 */
    private volatile boolean conditionHolds;
    private boolean conditionErrorLogged;

    public enum InteractMode {
        FAST,
        ACCURATE
    }

    public InteractBox(SubPart subPart, String name, InteractBoxAttr attr) {
        this.subPart = subPart;
        this.name = name;
        this.attr = attr;
        this.targetNames = attr.getSignalTargets();
        this.interactMode = InteractMode.valueOf(attr.getMode().toUpperCase(Locale.ROOT));

        String source = attr.getCondition();
        this.alwaysActive = source == null || source.isBlank() || source.equalsIgnoreCase(InteractBoxAttr.ALWAYS);
        MolangExpression compiled = null;
        if (!alwaysActive) {
            try {
                compiled = MolangContextRegistry.compile(source, subPart.part.getSparkMolangContext());
            } catch (Exception e) {
                // 内容写错不该崩游戏：这个交互区从此恒为不可交互，并在装配时留下一条可查的日志。
                MachineMax.LOGGER.error("交互区 {}-{} 的门控表达式无法编译，将永远不可交互：{}（{}）",
                        subPart.part.name, name, source, e.getMessage());
            }
        }
        this.condition = compiled;
        this.conditionHolds = alwaysActive;
    }

    /**
     * 结构变化时重建首跳表并重新解析投递点与交互目标。
     * <p>
     * 由 {@code VehicleCore#rebuildSignalTargets()} 在 {@code onStructureChanged(...)} 内统一调用：
     * 那一刻拓扑已经改完（attach/detach/merge/split 都已落地），所以这里解析到的是变化之后的结构。
     * <p>
     * <b>调用线程：</b>主线程。
     */
    @Override
    public void setTargetFromNames() {
        ISignalSender.super.setTargetFromNames();
        resolveDeliveryPoints();
    }

    /**
     * 沿首跳把每条频道推进到投递点（{@link ISignalSender#resolveTargets(String, boolean)} 的 {@code true} 档）：
     * 途经的总线实例记入事件线，终端子系统按声明序展平去重记入交互目标。
     * <p>
     * 频道名对同 Part 直连只是标签；跨零件时它是连接点对侧要认的到达频道，因此逐频道解析而不是一次合并。
     * 同一子系统经两条频道可达时只保留第一次出现的位置（先到先试）。一条频道两档都命中时两档都保留：
     * 事件先投递、再走子系统交互，{@code CONSUME} 不影响事件。
     */
    private void resolveDeliveryPoints() {
        LinkedHashSet<AbstractSubsystem> targets = new LinkedHashSet<>();
        List<String> events = new ArrayList<>();
        for (String channel : this.targets.keySet()) {
            boolean reachesBus = false;
            for (List<ISignalReceiver> receivers : resolveTargets(channel, true).values()) {
                for (ISignalReceiver receiver : receivers) {
                    if (receiver instanceof AbstractSubsystem subsystem) {
                        targets.add(subsystem);
                    } else if (receiver instanceof ISignalBus) {
                        reachesBus = true;
                    }
                }
            }
            if (reachesBus) events.add(channel);
        }
        this.interactTargets = List.copyOf(targets);
        this.eventChannels = List.copyOf(events);
        if (targets.isEmpty() && events.isEmpty() && !this.targets.isEmpty()) {
            MachineMax.LOGGER.debug("交互区 {} 没有解析到可交互的子系统或事件线（目标不可达）：{}",
                    name, this.targets.keySet());
        }
    }

    /**
     * 主线程：求值门控表达式。不门控时没有开销；编译失败时保持 {@code false}，即永远不可交互。
     * <p>
     * 由 {@code SubPart#preTick()} 每 tick 调一次——本方法的两个读点（提示与点击）都在主线程，
     * 没必要按物理子步重复求值。
     */
    public void updateCondition() {
        if (alwaysActive) return;
        MolangExpression expr = condition;
        if (expr == null) return;// 编译失败：conditionHolds 恒为 false
        try {
            conditionHolds = expr.evaluate(subPart.part.getSparkMolangContext()) > 0;
        } catch (Exception e) {
            conditionHolds = false;
            if (!conditionErrorLogged) {
                conditionErrorLogged = true;
                MachineMax.LOGGER.error("交互区 {}-{} 的门控表达式求值失败，按不可交互处理：{}（{}）",
                        subPart.part.name, name, attr.getCondition(), e.getMessage());
            }
        }
    }

    /**
     * 此刻这个交互区能不能用：门控成立，且至少有一条频道到达总线，或至少一个终端子系统宣告自己可交互。
     * <p>
     * 客户端用它决定要不要亮提示，服务端用它挡住拿着过期状态的点击。纯动画/机构交互区不依赖子系统可用性。
     * <p>
     * <b>调用线程：</b>主线程。
     */
    public boolean canInteract() {
        if (!conditionHolds) return false;
        if (!eventChannels.isEmpty()) return true;
        for (AbstractSubsystem target : interactTargets) {
            if (target.isInteractable()) return true;
        }
        return false;
    }

    /**
     * 先把事件线各投递一次，再按解析顺序询问子系统。事件先投递，因此座位的 {@code CONSUME} 不会吞掉开合事件；
     * 子系统仍按解析顺序尝试，第一个 {@link SignalResult#CONSUME} 结束对子系统的遍历。
     * <p>
     * {@link SignalResult#PASS}（目标不处理）与 {@link SignalResult#FAIL}（目标该处理却接不了，如座位已被占）
     * 都继续试下一个：一扇门通往多个座位的交互区因此从列表第一个空位开始坐，前排被占不影响后排。
     * 顺序由 {@link ISignalSender#setTargetFromNames()} 保序写入的接收者表与终端的声明序共同保证，
     * 不能依赖 Map 的哈希顺序。
     * <p>
     * 事件经事件车道按名投递：{@code local} 到达所属 Part、{@code global} 到达装配体总线并由宿主广播全零件，
     * 经连接点的频道按对侧转译表继续。投递不写输入槽，重复点击每次都到达。
     * <p>
     * <b>调用线程：</b>主线程（服务端）。
     */
    public void interact(LivingEntity entity) {
        for (String channel : eventChannels) {
            emitSignal(channel, null);
        }
        for (AbstractSubsystem target : interactTargets) {
            if (target.onInteract(entity) == SignalResult.CONSUME) return;
        }
    }

    /** 主线程。丢弃解析结果与首跳表，不再有任何引用。 */
    public void destroy() {
        interactTargets = List.of();
        eventChannels = List.of();
        targets.clear();
    }
}
