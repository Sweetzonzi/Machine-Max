package io.github.sweetzonzi.machine_max.common.mech.vehicle.interact;

import cn.solarmoon.spark_core.molang.MolangContextRegistry;
import cn.solarmoon.spark_core.molang.runtime.MolangExpression;
import io.github.sweetzonzi.machine_max.MachineMax;
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
 * 点击时把 {@code signal_targets} 中声明为动画事件的频道投递给所属 Part 或装配体，并依次询问解析出的子系统。
 *
 * <p><b>能不能交互</b> = {@code condition} 成立，且（有动画事件目标，或至少一个终端子系统可交互）：</p>
 * <ul>
 *   <li>动画事件目标：{@code signal_targets} 直接写 {@code local} 的频道投给所属 Part，写 {@code global}
 *       的频道交给装配体广播给全部部件。纯动画交互区不依赖子系统可用性。</li>
 *   <li>子系统目标：结构解析出终端子系统，并有任一终端此刻返回 {@link AbstractSubsystem#isInteractable()}。
 *       座位被占、子系统损坏时提示随之消失，不需要内容里再接线把它"报"给交互区。</li>
 *   <li>内容门控：{@code condition} 的 MoLang 表达式主线程每 tick 求值一次；写错会使交互区恒不可用并留错误日志。</li>
 * </ul>
 *
 * <p><b>只发不收：</b>交互区不是信号接收者。玩家点击是一次事件：声明给 {@code local} 的频道作为动画事件
 * 发给所属 Part，声明给 {@code global} 的频道由装配体广播给全载具部件；子系统目标仍直接调用
 * {@link AbstractSubsystem#onInteract(LivingEntity)}。点击不会写入信号槽，也不会经连接点转译动画事件。</p>
 *
 * <p><b>线程：</b>{@link #setTargetFromNames()}、{@link #updateCondition()}、{@link #interact(LivingEntity)}
 * 与 {@link #canInteract()} 都在主线程；物理线程只做"这个子形状属于哪个交互区"的几何筛选，不读本类的状态。</p>
 */
@Getter
public class InteractBox implements ISignalSender {
    public final String name;
    public final InteractBoxAttr attr;
    public final Map<String, List<String>> targetNames;
    /** 首跳表：频道 → 目标名 → 目标实例，由 {@link #setTargetFromNames()} 保序写入。解析终端与调试都读它。 */
    public final Map<String, Map<String, ISignalReceiver>> targets = new LinkedHashMap<>();
    public final SubPart subPart;
    public final InteractMode interactMode;
    /** 配置中直接指向所属 Part 的频道，点击时作为本部件的动画状态机事件名。 */
    private volatile List<String> localAnimationEventChannels;
    /** 配置中指向装配体的频道，点击时交给装配体广播给全载具的动画状态机。 */
    private volatile List<String> globalAnimationEventChannels;

    /** {@code condition} 为空或字面量 {@code true}：不门控，恒可用。 */
    private final boolean alwaysActive;
    /** 预编译的门控表达式；不门控或编译失败时为 {@code null}。 */
    @Nullable
    private final MolangExpression condition;
    /** 门控表达式最近一次求值结果。编译失败时恒为 {@code false}，即该交互区永远不可交互。 */
    private volatile boolean conditionHolds;
    /** 解析后的交互目标：只保留子系统，按 {@code signal_targets} 的频道与目标声明序展平。 */
    private volatile List<AbstractSubsystem> interactTargets = List.of();
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
        List<String> localChannels = new ArrayList<>();
        List<String> globalChannels = new ArrayList<>();
        targetNames.forEach((channel, names) -> {
            if (channel.isEmpty()) return;
            if (names.contains("local")) localChannels.add(channel);
            if (names.contains("global")) globalChannels.add(channel);
        });
        this.localAnimationEventChannels = List.copyOf(localChannels);
        this.globalAnimationEventChannels = List.copyOf(globalChannels);

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
     * 结构变化时重建首跳表并重新解析终端。
     * <p>
     * 由 {@code VehicleCore#rebuildSignalTargets()} 在 {@code onStructureChanged(...)} 内统一调用：
     * 那一刻拓扑已经改完（attach/detach/merge/split 都已落地），所以这里解析到的是变化之后的结构。
     * <p>
     * <b>调用线程：</b>主线程。
     */
    @Override
    public void setTargetFromNames() {
        ISignalSender.super.setTargetFromNames();
        this.interactTargets = resolveInteractTargets();
    }

    /**
     * 沿首跳把每条频道推进到终端，只保留子系统，按声明序展平并去重。
     * <p>
     * 频道名对同 Part 直连只是标签；跨零件时它是连接点对侧要认的到达频道，因此逐频道解析而不是一次合并。
     * 同一子系统经两条频道可达时只保留第一次出现的位置（先到先试）。
     */
    private List<AbstractSubsystem> resolveInteractTargets() {
        LinkedHashSet<AbstractSubsystem> ordered = new LinkedHashSet<>();
        for (String channel : targets.keySet()) {
            for (List<ISignalReceiver> terminals : resolveTargets(channel).values()) {
                for (ISignalReceiver terminal : terminals) {
                    if (terminal instanceof AbstractSubsystem subsystem) {
                        ordered.add(subsystem);
                    }
                }
            }
        }
        if (ordered.isEmpty() && !targets.isEmpty()
                && localAnimationEventChannels.isEmpty() && globalAnimationEventChannels.isEmpty()) {
            MachineMax.LOGGER.debug("交互区 {} 没有解析到可交互的子系统，也没有动画事件目标（目标不可达或不是子系统）：{}",
                    name, targets.keySet());
        }
        return List.copyOf(ordered);
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
     * 此刻这个交互区能不能用：门控成立，且至少有一个终端子系统宣告自己可交互，或存在动画事件目标。
     * <p>
     * 客户端用它决定要不要亮提示，服务端用它挡住拿着过期状态的点击。纯动画交互区不依赖子系统可用性。
     * <p>
     * <b>调用线程：</b>主线程。
     */
    public boolean canInteract() {
        if (!conditionHolds) return false;
        if (!localAnimationEventChannels.isEmpty() || !globalAnimationEventChannels.isEmpty()) return true;
        for (AbstractSubsystem target : interactTargets) {
            if (target.isInteractable()) return true;
        }
        return false;
    }

    /**
     * 动画事件先投递一次，因此座位的 {@code CONSUME} 不会吞掉开门事件；子系统仍按解析顺序尝试，
     * 第一个 {@link SignalResult#CONSUME} 结束对子系统的遍历。
     * <p>
     * {@link SignalResult#PASS}（目标不处理）与 {@link SignalResult#FAIL}（目标该处理却接不了，如座位已被占）
     * 都继续试下一个：一扇门通往多个座位的交互区因此从列表第一个空位开始坐，前排被占不影响后排。
     * 顺序由 {@link ISignalSender#setTargetFromNames()} 保序写入的接收者表与终端的声明序共同保证，
     * 不能依赖 Map 的哈希顺序。
     * <p>
     * {@code local} 频道投给所属 Part，{@code global} 频道交给装配体广播给全载具部件：动画控制器挂在
     * 车体上时，点击长在盖板上的交互区也能让车体的状态机切换。
     * <p>
     * <b>调用线程：</b>主线程（服务端）。
     */
    public void interact(LivingEntity entity) {
        if (subPart != null) {
            for (String channel : localAnimationEventChannels) {
                subPart.part.triggerInteractionEvent(channel);
            }
            if (!globalAnimationEventChannels.isEmpty() && subPart.part.assembly != null) {
                for (String channel : globalAnimationEventChannels) {
                    subPart.part.assembly.broadcastInteractionEvent(channel);
                }
            }
        }
        for (AbstractSubsystem target : interactTargets) {
            if (target.onInteract(entity) == SignalResult.CONSUME) return;
        }
    }

    /** 主线程。丢弃解析结果与首跳表，不再有任何引用。 */
    public void destroy() {
        interactTargets = List.of();
        localAnimationEventChannels = List.of();
        globalAnimationEventChannels = List.of();
        targets.clear();
    }
}
