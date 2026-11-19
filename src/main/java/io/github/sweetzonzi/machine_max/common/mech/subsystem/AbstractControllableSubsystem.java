package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.mojang.serialization.Codec;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlBinding;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroup;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.MoveInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.RegularInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.signal.ViewInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.BasicSubsystemDynamicAttr;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.util.data.KeyInputMapping;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

abstract public class AbstractControllableSubsystem extends BasicSubsystem {
    @Getter
    protected ControlGroupSet controlGroupSet = ControlGroupSet.EMPTY;
    /** 主线程记录当前实体视点，-1 表示座椅虚拟视点；失效后释放全部控制组输出。 */
    private int viewSourcePart = -1;
    private String viewSourceName = "";


    protected AbstractControllableSubsystem(ISubsystemHost owner, String name, BasicSubsystemDynamicAttr attr) {
        super(owner, name, attr);
    }

    /** 主线程检查正在输出的实体视点是否有效，防止失效设备保留高频道信号。 */
    @Override
    public void onTick() {
        super.onTick();
        if (viewSourcePart >= 0 && !hasViewCandidate(viewSourcePart, viewSourceName)) {
            clearInputSignals();
        }
    }
    @Override
    public void onActive() {
        super.onActive();
        clearInputSignals();
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        clearInputSignals();
    }

    @Override
    public void onDestroyed() {
        super.onDestroyed();
        clearInputSignals();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        clearInputSignals();
    }

    @Override
    public void onVehicleStructurePreChanged() {
        super.onVehicleStructurePreChanged();
        clearInputSignals();
    }


    // ===== 摄像机发现 =====

    /**
     * 当前接线上可达的观察设备，只表达内容里的静态发现，按发现顺序去重。<br>
     * 每次结构变化整体替换为不可变快照：主线程写，主线程与渲染线程读。
     * <p>
     * 玩家实际能切到的镜头由 {@link #getViewCandidates()} 给出：它还并入了当前活动武器控制器
     * 发现的视点。本列表保留原义，供内容声明与既有读点区分“座位写了什么”与“此刻能看什么”。
     */
    @Getter
    protected volatile List<AbstractViewpointSubsystem> discoveredCameras = List.of();

    /** 当前激活的摄像机（炮镜模式时设置），null 表示座椅直发模式 */
    @Nullable
    protected AbstractViewpointSubsystem activeCamera = null;

    /**
     * 摄像机发现握手配置 {频道名 → [目标接收者名列表]}。
     * 子类（SeatSubsystem 等）在构造函数中从动态属性赋值。
     */
    protected Map<String, List<String>> cameraDiscoveryTargets = Map.of();

    /**
     * 从 {@link #cameraDiscoveryTargets} 声明的输出频道解析观察设备。<br>
     * 解析规则见 {@link AbstractSubsystem#resolveViewpoints(Map)}：只读接线、同装配体、按实例去重。
     */
    protected void resolveCameras() {
        this.discoveredCameras = resolveViewpoints(cameraDiscoveryTargets);
    }

    // ===== 视点候选 =====

    /**
     * 一个可切换视点，以及发现它的那台活动武器控制器。
     *
     * <p>两个字段都有读点：{@code viewpoint} 用于循环、有效性与服务端校验；
     * {@code controller} 用于固定炮镜的机构采样配对。它不是把已有对象的字段搬一遍，
     * 而是把“哪台视点属于哪台武器”这一发现结果固定成一次读取的值。</p>
     *
     * @param viewpoint  视点实例
     * @param controller 首次发现它的活动武器控制器；座位静态声明且没有控制器发现它时为 {@code null}
     */
    public record ViewCandidate(AbstractViewpointSubsystem viewpoint,
                                @Nullable WeaponControllerSubsystem controller) {
    }

    /**
     * 此刻可切换的视点：静态发现 ∪ 当前活动武器控制器发现的视点。
     *
     * <p>构成与顺序：静态 {@link #discoveredCameras} 按声明顺序在前；随后按活动集合
     * （base → 当前子组）顺序展开每台控制器的发现结果。活动集合取
     * {@link #getActiveWeaponControllers()}，因此副武器线上的设备（遥控武器站一类）同样算数。
     * 同一实例只出现一次，保留首次出现的位置。</p>
     *
     * <p>过滤四条——与座椅同装配体、{@code isActive()}、未摧毁、宿主子零件未移除——对视点与
     * 武器控制器都成立。这是各读点判定的并集（最严的一份），因此被拆下的子零件上的视点不会
     * 留在切换列表里。</p>
     *
     * <p>配对取首次发现该视点的活动控制器；静态列表里的视点若同时被某台活动控制器发现，
     * 同样填该控制器，否则为 {@code null}。</p>
     *
     * <p>每次调用按当前控制组现算，不缓存到结构变化点：活动组索引两端已同步，所以客户端与服务端
     * 由同一段代码得出同一结论。</p>
     *
     * @return 不可变候选列表，没有可切换视点时为空列表
     */
    public List<ViewCandidate> getViewCandidates() {
        List<WeaponControllerSubsystem> controllers = getActiveWeaponControllers();
        Map<AbstractViewpointSubsystem, WeaponControllerSubsystem> discoveredBy = new IdentityHashMap<>();
        for (WeaponControllerSubsystem controller : controllers) {
            if (!isViewCandidateController(controller)) continue;
            for (AbstractViewpointSubsystem viewpoint : controller.getDiscoveredViewpoints()) {
                discoveredBy.putIfAbsent(viewpoint, controller);
            }
        }

        List<ViewCandidate> candidates = new ArrayList<>(discoveredCameras.size() + discoveredBy.size());
        Set<AbstractViewpointSubsystem> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (AbstractViewpointSubsystem viewpoint : discoveredCameras) {
            if (isViewCandidate(viewpoint) && seen.add(viewpoint)) {
                candidates.add(new ViewCandidate(viewpoint, discoveredBy.get(viewpoint)));
            }
        }
        for (WeaponControllerSubsystem controller : controllers) {
            if (!isViewCandidateController(controller)) continue;
            for (AbstractViewpointSubsystem viewpoint : controller.getDiscoveredViewpoints()) {
                if (isViewCandidate(viewpoint) && seen.add(viewpoint)) {
                    candidates.add(new ViewCandidate(viewpoint, controller));
                }
            }
        }
        return List.copyOf(candidates);
    }

    /**
     * 按实例判定：该视点此刻是否在候选里。服务端校验与切换路径用它。
     *
     * @param viewpoint 视点实例
     * @return 在候选里为 true
     */
    public boolean hasViewCandidate(AbstractViewpointSubsystem viewpoint) {
        for (ViewCandidate candidate : getViewCandidates()) {
            if (candidate.viewpoint() == viewpoint) return true;
        }
        return false;
    }

    /**
     * 按结构地址判定：只记录 {@code (子零件 ID, 名字)} 的读点用它。
     *
     * @param subPartId 视点所属子零件 ID
     * @param name      视点子系统名
     * @return 在候选里为 true
     */
    public boolean hasViewCandidate(int subPartId, String name) {
        for (ViewCandidate candidate : getViewCandidates()) {
            AbstractViewpointSubsystem viewpoint = candidate.viewpoint();
            if (viewpoint.getSubPart().getId() == subPartId && viewpoint.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 该视点在候选里的配对控制器；不在候选、或没有被活动武器控制器发现时为 {@code null}。
     *
     * @param viewpoint 视点实例
     * @return 配对的武器控制器或 {@code null}
     */
    @Nullable
    public WeaponControllerSubsystem getViewCandidateController(AbstractViewpointSubsystem viewpoint) {
        for (ViewCandidate candidate : getViewCandidates()) {
            if (candidate.viewpoint() == viewpoint) return candidate.controller();
        }
        return null;
    }

    /** 候选里的视点四条：同装配体、启用、未摧毁、宿主子零件未移除。 */
    private boolean isViewCandidate(AbstractViewpointSubsystem viewpoint) {
        return viewpoint.getSubPart().getPart().assembly == getSubPart().getPart().assembly
                && viewpoint.isActive() && !viewpoint.isDestroyed() && !viewpoint.getSubPart().isRemoved;
    }

    /** 候选里的武器控制器三条：启用、未摧毁、宿主子零件未移除。 */
    private static boolean isViewCandidateController(WeaponControllerSubsystem controller) {
        return controller.isActive() && !controller.isDestroyed() && !controller.getSubPart().isRemoved;
    }

    // ===== 武器控制器发现 =====

    /**
     * 按控制组索引的武器控制器发现结果。<br>
     * key = 控制组名（baseGroup 固定 "base"，子组用 group.name），
     * value = 该控制组通过握手发现的 WeaponController 列表。
     * <p>
     * 握手通过遍历控制组全部输出频道，以控制组名作为回调信号值来实现精确归类。
     * 一个 WeaponController 可能被多个控制组共用，此时出现在多个组列表中。
     */
    @Getter
    protected final Map<String, List<WeaponControllerSubsystem>> weaponControllersByGroup =
            new ConcurrentHashMap<>();

    /**
     * 按控制组解析武器控制器：遍历该组启用的输出频道，用 {@link ISignalSender#resolveTargets(String)}
     * 取终端并筛出同类控制器，不再依赖回调频道携带组名。
     */
    protected void resolveWeaponControllers() {
        weaponControllersByGroup.clear();
        // baseGroup 固定 key = "base"
        resolveWeaponControllersForGroup("base", controlGroupSet.baseGroup);
        // 子控制组 key = 组名
        for (ControlGroup group : controlGroupSet.groups) {
            resolveWeaponControllersForGroup(group.name, group);
        }
    }

    /**
     * 对单个控制组执行发现。<br>
     * 只沿该组自己声明的目标推进：同一频道被两个组指向不同接收者时，各组只看到自己的那些。
     *
     * @param groupKey 控制组标识（"base" 或子组名）
     * @param group    控制组对象
     */
    private void resolveWeaponControllersForGroup(String groupKey, ControlGroup group) {
        List<WeaponControllerSubsystem> found = new ArrayList<>();
        Set<WeaponControllerSubsystem> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<String, List<String>> entry : discoveryTargetNames(group).entrySet()) {
            String channel = entry.getKey();
            if (channel.isEmpty()) continue;
            for (List<ISignalReceiver> terminals : resolveTargets(channel, entry.getValue()).values()) {
                for (ISignalReceiver terminal : terminals) {
                    if (terminal instanceof WeaponControllerSubsystem wc
                            && wc.getOwner().getSubPart().getPart().assembly
                            == this.getOwner().getSubPart().getPart().assembly
                            && seen.add(wc)) {
                        found.add(wc);
                    }
                }
            }
        }
        weaponControllersByGroup.put(groupKey, List.copyOf(found));
    }

    /**
     * 一个控制组的输出声明：频道名 → 该组声明的目标名。<br>
     * 五类目标表与按键绑定按类别顺序取有序并集，同一频道在多个类别里出现时目标名合并而不是覆盖。
     */
    private static Map<String, List<String>> discoveryTargetNames(ControlGroup group) {
        Map<String, List<String>> names = new LinkedHashMap<>();
        for (Map<String, List<String>> map : List.of(group.moveTargets, group.viewTargets, group.regularTargets,
                group.mainWeaponTargets, group.secondaryWeaponTargets)) {
            map.forEach((channel, targets) ->
                    names.merge(channel, targets, AbstractControllableSubsystem::unionTargets));
        }
        for (ControlBinding binding : group.bindings) {
            names.merge(binding.channel, binding.targets, AbstractControllableSubsystem::unionTargets);
        }
        return names;
    }

    /**
     * 按 base、子组配置顺序收集当前座椅全部组发现的 HUD 候选，按控制器实例去重。
     */
    public List<WeaponControllerSubsystem> getHudWeaponCandidates() {
        Set<WeaponControllerSubsystem> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<WeaponControllerSubsystem> result = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        keys.add("base");
        for (ControlGroup group : controlGroupSet.groups) keys.add(group.name);
        for (String key : keys) {
            for (WeaponControllerSubsystem controller : weaponControllersByGroup.getOrDefault(key, List.of())) {
                if (seen.add(controller)) result.add(controller);
            }
        }
        return List.copyOf(result);
    }

    /** 当前选中集合为 base 与当前子组的控制器并集。 */
    public List<WeaponControllerSubsystem> getActiveWeaponControllers() {
        List<WeaponControllerSubsystem> base =
                weaponControllersByGroup.getOrDefault("base", List.of());
        List<WeaponControllerSubsystem> result = new ArrayList<>(base);

        ControlGroup active = controlGroupSet.getActiveGroup();
        if (active != null) {
            List<WeaponControllerSubsystem> sub =
                    weaponControllersByGroup.getOrDefault(active.name, List.of());
            for (WeaponControllerSubsystem wc : sub) {
                if (!result.contains(wc)) result.add(wc);
            }
        }
        return result;
    }

    /**
     * 主线程／渲染线程按当前主武器输出频道取得全部有效 WC。<br>
     * 候选来自 {@link #getActiveWeaponControllers()}，以主武器开火频道区分主／副控制器，
     * 保留发现顺序，同一控制器在 base 和当前子组中出现时只返回一次。
     */
    public List<WeaponControllerSubsystem> getActiveMainWeaponControllers() {
        Set<String> channels = controlGroupSet.getMergedMainWeaponTargets().keySet();
        List<WeaponControllerSubsystem> result = new ArrayList<>();
        for (WeaponControllerSubsystem controller : getActiveWeaponControllers()) {
            if (controller.isActive() && !controller.isDestroyed() && !controller.getSubPart().isRemoved
                    && controller.attr.staticAttribute.getFireInputs().stream().anyMatch(channels::contains)) {
                result.add(controller);
            }
        }
        return result;
    }

    /** 主线程取发现顺序中的第一台有效主 WC，供炮镜选择机构参考；无匹配时返回 null。 */
    @Nullable
    public WeaponControllerSubsystem getActiveMainWeaponController() {
        List<WeaponControllerSubsystem> controllers = getActiveMainWeaponControllers();
        return controllers.isEmpty() ? null : controllers.getFirst();
    }

    @Override
    public void onAttach() {
        super.onAttach();
        // 暂不允许自定义控制组，每次从注册表读取预设
        restoreControlGroupPreset();
        resolveCameras();
        resolveWeaponControllers();
    }

    /** 主线程记录活动实体视点；null 表示座椅虚拟视点。输入模式按载荷所指设备解析。 */
    public void setActiveCamera(@Nullable AbstractViewpointSubsystem camera) {
        this.activeCamera = camera;
    }

    /**
     * 设置控制组集合<br>
     * 切换前先清除当前控制组的输出信号，防止高优先级信号残留。控制组决定本子系统的输出频道，
     * 因此同时重建首跳表与按组发现结果。
     */
    public void setControlGroupSet(ControlGroupSet cgs) {
        // 先清除当前控制组所有频道的信号（使用旧 controlGroupSet 的频道列表）
        if (this.controlGroupSet != ControlGroupSet.EMPTY) {
            clearInputSignals();
        }
        this.controlGroupSet = cgs != null ? cgs : ControlGroupSet.EMPTY;
        // 座位在构造器里就装载预设，此时同一 SubPart 中排在它后面的子系统还没建出来，
        // 按名解析只会落空。目标表以 attach 为界构建：构造期只记录配置，attach 之后
        // （含运行期换组与面板编辑）才重建首跳表与按组发现，onAttach 会再走一遍这里。
        if (!isAttached()) return;
        setTargetFromNames();
        resolveWeaponControllers();
    }

    /**
     * 把同一频道的多类目标按固定类别顺序取有序并集。<br>
     * 同一频道可能同时出现在移动、视角、武器或按键绑定里；后来者不能覆盖先前的目标名。
     */
    public Map<String, List<String>> setUpTargets(Map<String, List<String>> map) {
        mergeTargets(map, controlGroupSet.getAllMoveTargets());
        mergeTargets(map, controlGroupSet.getAllRegularTargets());
        mergeTargets(map, controlGroupSet.getAllViewTargets());
        mergeTargets(map, controlGroupSet.getAllMainWeaponTargets());
        mergeTargets(map, controlGroupSet.getAllSecondaryWeaponTargets());
        mergeTargets(map, controlGroupSet.getAllBindingTargets());
        mergeTargets(map, controlGroupSet.getAllGuiActionTargets());
        mergeTargets(map, cameraDiscoveryTargets);
        return map;
    }

    private static void mergeTargets(Map<String, List<String>> into, Map<String, List<String>> from) {
        from.forEach((channel, targets) -> into.merge(channel, targets, AbstractControllableSubsystem::unionTargets));
    }

    /**
     * 同一频道的两组目标名取有序并集，重复项只保留首次出现。<br>
     * 座位合并控制组各类目时用它，武器控制器合并命令输出与视点发现时也用它。
     */
    static List<String> unionTargets(List<String> first, List<String> second) {
        Set<String> union = new LinkedHashSet<>(first.size() + second.size());
        union.addAll(first);
        union.addAll(second);
        return List.copyOf(union);
    }

    /**
     * 按合并表声明的目标逐个投递：只有 base 与活动子组共同声明的接收者会拿到值。<br>
     * 频道名取自合并表，接收者名逐条经首跳表换算成实例；声明了但解不出接收者的名字由
     * {@link ISignalSender#sendSignalToTarget(String, String, Object)} 跳过。目标名与首跳表的键
     * 同为接收者的寻址名（子系统名、{@code "local"}、{@code "global"}、交互区名、连接点名）。
     */
    private void sendPerTarget(Map<String, List<String>> targets, Function<String, Object> valueOf) {
        for (Map.Entry<String, List<String>> entry : targets.entrySet()) {
            Object value = valueOf.apply(entry.getKey());
            for (String targetName : entry.getValue()) {
                this.sendSignalToTarget(entry.getKey(), targetName, value);
            }
        }
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        resolveCameras();
        resolveWeaponControllers();
    }

    /**
     * 清除当前控制组所有输出频道的信号，包括视点、移动与武器。<br>
     * 操作者离座、子系统失活、摧毁、装机变化、控制组切换与"视点设备已失效"时调用，防止上一组的高优先级
     * 信号残留。只是换视点或换主武器控制器时不要用这个：那会连玩家仍按着的油门与扳机一起清掉，
     * 见 {@link #clearViewSignals()}。
     */
    public void clearInputSignals() {
        clearViewSignals();
        Map<String, List<String>> all = new HashMap<>();
        all.putAll(controlGroupSet.getAllMoveTargets());
        all.putAll(controlGroupSet.getAllRegularTargets());
        all.putAll(controlGroupSet.getAllMainWeaponTargets());
        all.putAll(controlGroupSet.getAllSecondaryWeaponTargets());
        clearTargets(all);
    }

    /**
     * 只清除视点输出（记录中的视点身份与视点频道），保留移动、常规与武器频道。<br>
     * 换视点、换主武器控制器时用这个：视点必须按新机构重新起算，但玩家手上的油门与扳机没有松开，
     * 一并清掉只会让"按住"这件事在本端消失——服务端仍按它自己的频道继续开火，本端要等到下一次按键
     * 边沿才恢复，中间那几发就成了"投射物飞出去了，本端这一发没有声音与炮口特效"。
     * <p><b>调用线程：</b>客户端主线程（{@code CameraController} 换视点时）。
     */
    public void clearViewSignals() {
        viewSourcePart = -1;
        viewSourceName = "";
        clearTargets(controlGroupSet.getAllViewTargets());
    }

    /**
     * 把一组频道置空，并记录实际被丢弃的非空信号。<br>
     * 插桩只在打开的档位里多一次空表检查：它把"按键没丢、频道被清"与"输入包丢了"区分开——两者在开火侧
     * 观察到的现象相同（本端不再有开火指令），只有知道是哪一端、清了哪条频道才分得清。
     */
    private void clearTargets(Map<String, List<String>> targets) {
        for (String signalKey : targets.keySet()) {
            if (WeaponTrace.isEnabled() && !getSignalChannel(signalKey).isEmpty()) {
                WeaponTrace.log(getLevel(), "input-clear subsystem={} channel={}", name, signalKey);
            }
            this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
        }
    }

    public void setMoveInputSignal(byte[] inputs, byte[] conflicts) {
        Map<String, List<String>> targets = controlGroupSet.getMergedMoveTargets();
        if (!targets.isEmpty() && this.isActive()) {
            sendPerTarget(targets, channel -> new MoveInputSignal(inputs, conflicts,
                    controlGroupSet.priorityOf(channel)));
            this.getOwner().getSubPart().part.assembly.activatePhysics();
        } else {
            for (String signalKey : targets.keySet()) {
                this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
            }
        }
    }

    public void setRegularInputSignal(KeyInputMapping inputType, int tickCount) {
        Map<String, List<String>> targets = controlGroupSet.getMergedRegularTargets();
        if (!targets.isEmpty() && this.isActive()) {
            sendPerTarget(targets, channel -> new RegularInputSignal(inputType, tickCount,
                    controlGroupSet.priorityOf(channel)));
            this.getOwner().getSubPart().part.assembly.activatePhysics();
        } else {
            for (String signalKey : targets.keySet()) {
                this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
            }
        }
    }

    /** 主线程记录当前视点，并经控制组派发完整目标。 */
    public boolean setViewInputSignal(ViewInputSignal input, int sourcePart, String sourceName) {
        if (!isActive() || isDestroyed()) {
            return false;
        }
        viewSourcePart = sourcePart;
        viewSourceName = sourceName;
        sendPerTarget(controlGroupSet.getMergedViewTargets(),
                channel -> input.withPriority(controlGroupSet.priorityOf(channel)));
        getSubPart().getPart().assembly.activatePhysics();
        return true;
    }

    /**
     * 设置主武器控制信号，发送到当前控制组的 mainWeaponTargets 频道。<br>
     * 复用 RegularInputSignal，支持 MAIN_FIRE / NEXT_AMMO_TYPE 等按键语义。<br>
     * 弹种切换不改写这些频道：它是一条一次性指令，走已发现的主武器控制器的
     * {@link WeaponControllerSubsystem#AMMO_SWITCH_CHANNEL 弹种切换线}，
     * 见 {@link #sendAmmoSwitch(KeyInputMapping)}。<br>
     * 可被 AI 实体直接调用而不需经过控制组绑定。
     *
     * @param inputType 武器控制按键类型（如 MAIN_FIRE、NEXT_AMMO_TYPE）
     * @param tickCount 0=按下/按下中, 非0=松开
     */
    public void setMainWeaponInputSignal(KeyInputMapping inputType, int tickCount) {
        if (inputType == KeyInputMapping.NEXT_AMMO_TYPE || inputType == KeyInputMapping.PREV_AMMO_TYPE) {
            sendAmmoSwitch(inputType);
            return;
        }
        Map<String, List<String>> targets = controlGroupSet.getMergedMainWeaponTargets();
        if (!targets.isEmpty() && this.isActive()) {
            // hold 类型按键松开时(tickCount!=0)发送 EmptySignal 清除频道，防止走火
            sendPerTarget(targets, channel -> (inputType == KeyInputMapping.MAIN_FIRE && tickCount != 0)
                    ? EmptySignal.INSTANCE
                    : new RegularInputSignal(inputType, tickCount, controlGroupSet.priorityOf(channel)));
            this.getOwner().getSubPart().part.assembly.activatePhysics();
        } else {
            for (String signalKey : targets.keySet()) {
                this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
            }
        }
    }

    /**
     * 把一次弹种切换发送给本座位当前的主武器控制器。<br>
     * 走控制器的专用线而不借用开火频道：一条频道对每个发送者只有一格，切弹指令借走那一格，
     * 同一发送者仍按着的扳机就从"按下中"变成了别的东西，开火要等下一次按键边沿才恢复。
     * 控制器收到后在 {@code updateRegularInputs} 里消费掉并把槽位清空，因此一次按键只切一次。
     */
    private void sendAmmoSwitch(KeyInputMapping inputType) {
        if (!isActive() || isDestroyed()) return;
        List<WeaponControllerSubsystem> controllers = getActiveMainWeaponControllers();
        if (controllers.isEmpty()) return;
        RegularInputSignal command = new RegularInputSignal(inputType, 0);
        for (WeaponControllerSubsystem controller : controllers) {
            sendSignalToTarget(WeaponControllerSubsystem.AMMO_SWITCH_CHANNEL, controller, command);
        }
    }

    /**
     * 设置副武器控制信号，发送到当前控制组的 secondaryWeaponTargets 频道。<br>
     * 复用 RegularInputSignal，支持 SECONDARY_FIRE / NEXT_AMMO_TYPE 等按键语义。<br>
     * 可被 AI 实体直接调用而不需经过控制组绑定。
     *
     * @param inputType 武器控制按键类型（如 SECONDARY_FIRE、NEXT_AMMO_TYPE）
     * @param tickCount 0=按下/按下中, 非0=松开
     */
    public void setSecondaryWeaponInputSignal(KeyInputMapping inputType, int tickCount) {
        Map<String, List<String>> targets = controlGroupSet.getMergedSecondaryWeaponTargets();
        if (!targets.isEmpty() && this.isActive()) {
            // hold 类型按键松开时(tickCount!=0)发送 EmptySignal 清除频道，防止走火
            sendPerTarget(targets, channel -> (inputType == KeyInputMapping.SECONDARY_FIRE && tickCount != 0)
                    ? EmptySignal.INSTANCE
                    : new RegularInputSignal(inputType, tickCount, controlGroupSet.priorityOf(channel)));
            this.getOwner().getSubPart().part.assembly.activatePhysics();
        } else {
            for (String signalKey : targets.keySet()) {
                this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
            }
        }
    }

    /**
     * 处理来自 ControlBinding 按键事件的信号发送。
     * 根据 BindingAction 类型（PRESS/HOLD/TOGGLE）和按键事件类型（按下/松开），
     * 将信号值发送到 binding 定义的目标频道与接收者。
     *
     * @param bindingIndex 合并绑定列表中的索引
     * @param eventType    0=按下, 1=松开
     */
    public void sendBindingSignal(int bindingIndex, int eventType) {
        ControlBinding binding = controlGroupSet.findBindingByIndex(bindingIndex);
        if (binding == null || !this.isActive()) return;

        Object value = switch (binding.action) {
            case PRESS  -> (eventType == 0) ? 1.0f : null;
            case HOLD   -> (eventType == 0) ? 1.0f : EmptySignal.INSTANCE;
            case TOGGLE -> {
                if (eventType != 0) yield null;
                yield binding.flipToggleState() ? 1.0f : 0.0f;
            }
        };

        if (value != null) {
            for (String targetName : binding.targets) {
                this.sendSignalToTarget(binding.channel, targetName, value);
            }
            this.getOwner().getSubPart().part.assembly.activatePhysics();
        }
    }

    /**
     * 恢复至 JSON 预设的控制组配置。
     * 玩家编辑后被覆盖时调用此方法重置。
     */
    public void restoreControlGroupPreset() {
        ResourceLocation rl = this.attr.getControlGroupPresetRl();
        if (!rl.equals(AbstractSubsystemAttr.NO_CONTROL_GROUP_PRESET)) {
            ControlGroupSet preset = MMDynamicRes.CONTROL_GROUP_PRESETS.get(rl);
            if (preset != null) {
                setControlGroupSet(new ControlGroupSet(preset.baseGroup, preset.groups, preset.getGuiActions(), preset.getActiveIndex()));
            }
        }
    }

    /**
     * 把活动子组复位到该子系统预设的默认值，返回复位后的索引。
     *
     * <p>目标值取预设自身的 {@code active_index}，与 {@link #restoreControlGroupPreset()} 同源：
     * 内容包里的常量决定，两端读同一份数据，所以可以直接当作复位目标，不需要同步。
     * 没有预设时目标为 {@code -1}（base 组，等价于“组列表为空”：{@code getActiveGroup()} 返回 null，
     * {@code getMerged*} 回退 base 组）。</p>
     *
     * <p>用新实例而不是就地 {@code activate(target)}：构造器可能把注册表里的预设实例直接存进来，
     * 就地改会波及共用同一预设的其它座位；{@code activate} 还会拒绝越界索引，
     * 而玩家用面板编辑过的组列表可能比预设短。{@code setControlGroupSet} 会先用旧集合的
     * 全部频道清一遍输入信号，正是“别留着上一位乘客的信号”。</p>
     *
     * <p>幂等：当前值已经等于目标时直接返回，不重建集合、不重复清信号。</p>
     */
    public int resetControlGroupToPreset() {
        ControlGroupSet current = getControlGroupSet();
        if (current == null || current == ControlGroupSet.EMPTY) return -1;

        int target = -1;
        ResourceLocation rl = this.attr.getControlGroupPresetRl();
        if (!rl.equals(AbstractSubsystemAttr.NO_CONTROL_GROUP_PRESET)) {
            ControlGroupSet preset = MMDynamicRes.CONTROL_GROUP_PRESETS.get(rl);
            if (preset != null) target = preset.getActiveIndex();
            else MachineMax.LOGGER.debug("控制组预设 {} 未找到，活动组复位为 -1", rl);
        }
        if (current.getActiveIndex() == target) return target;

        setControlGroupSet(new ControlGroupSet(current.baseGroup, current.groups, current.getGuiActions(), target));
        return target;
    }

    /**
     * 输入来源校验：该玩家此刻是否仍“在这个子系统上”。
     *
     * <p>换座请求发出到结果包到达之间，客户端仍按旧座位注册着按键；服务端处理完换座之后
     * 才收到这些包时，会把刚清掉的信号重新写回旧座位。判据与 {@code ViewInputPayload}
     * 的来源校验一致：控制关系指向本子系统，且（对座位而言）乘客、可骑乘实体、实际载具三者
     * 都必须仍然成立。</p>
     */
    public static boolean isControlledBy(AbstractControllableSubsystem subsystem, Player player) {
        if (subsystem == null || player == null) return false;
        if (!subsystem.isActive() || subsystem.isDestroyed()) return false;
        if (((IEntityMixin) player).machine_Max$getControllingSubsystem() != subsystem) return false;
        if (subsystem instanceof SeatSubsystem seat) {
            if (seat.passenger != player) return false;
            if (seat.getSubPart() == null || seat.getSubPart().getEntity() == null) return false;
            if (player.getVehicle() != seat.getSubPart().getEntity()) return false;
        }
        return true;
    }

    @Override
    public void loadData(CompoundTag data) {
        super.loadData(data);
        // TODO: 控制组仅从注册表读取预设，自定义控制组的 NBT 加载处于禁用状态
//        if (data.contains("control_group_set", CompoundTag.TAG_COMPOUND)) {
//            ControlGroupSet.CODEC.parse(NbtOps.INSTANCE, data.get("control_group_set"))
//                    .resultOrPartial(e -> MachineMax.LOGGER.warn("无法加载控制组数据: {}", e))
//                    .ifPresent(cgs -> this.controlGroupSet = cgs);
//        }
    }

    @Override
    public CompoundTag saveData(CompoundTag data) {
        super.saveData(data);
        ControlGroupSet.CODEC.encodeStart(NbtOps.INSTANCE, controlGroupSet)
                .resultOrPartial(e -> MachineMax.LOGGER.warn("无法保存控制组数据: {}", e))
                .ifPresent(tag -> data.put("control_group_set", tag));
        return data;
    }
}
