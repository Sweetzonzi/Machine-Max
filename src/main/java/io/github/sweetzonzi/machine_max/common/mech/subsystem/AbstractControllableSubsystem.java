package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.mojang.serialization.Codec;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlBinding;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroup;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
        if (viewSourcePart >= 0 && discoveredCameras.stream().noneMatch(view ->
                view.getSubPart().getId() == viewSourcePart && view.getName().equals(viewSourceName)
                        && view.isActive() && !view.isDestroyed() && !view.getSubPart().isRemoved
                        && view.getSubPart().getPart().assembly == getSubPart().getPart().assembly)) {
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

    /** 握手发现的摄像机列表（线程安全）
     * -- GETTER --
     * 返回握手发现的摄像机列表
     */
    @Getter
    protected final List<AbstractViewpointSubsystem> discoveredCameras = new CopyOnWriteArrayList<>();

    /** 当前激活的摄像机（炮镜模式时设置），null 表示座椅直发模式 */
    @Nullable
    protected AbstractViewpointSubsystem activeCamera = null;

    /**
     * 摄像机发现握手配置 {频道名 → [目标接收者名列表]}。
     * 子类（SeatSubsystem 等）在构造函数中从动态属性赋值。
     */
    protected Map<String, List<String>> cameraDiscoveryTargets = Map.of();

    /**
     * 摄像机发现握手，向 cameraDiscoveryTargets 配置的每个频道发送空信号（带回调），
     * AbstractViewpointSubsystem 收到后通过 callback 回复，座椅在 onSignalUpdated 中缓存。
     */
    protected void cameraDiscoveryHandshake() {
        for (String signalChannel : cameraDiscoveryTargets.keySet()) {
            sendSignalToAllTargetsWithCallback(signalChannel, EmptySignal.INSTANCE, false);
        }
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
     * 武器控制器发现握手：遍历所有控制组的全部输出频道，通过回调发现 WeaponController。<br>
     * 利用回调的信号值携带控制组名，实现精确归属归类。
     */
    protected void weaponControllerDiscoveryHandshake() {
        weaponControllersByGroup.clear();
        // baseGroup 固定 key = "base"
        discoverWeaponControllersForGroup("base", controlGroupSet.baseGroup);
        // 子控制组 key = 组名
        for (ControlGroup group : controlGroupSet.groups) {
            discoverWeaponControllersForGroup(group.name, group);
        }
    }

    /**
     * 对单个控制组执行武器控制器发现握手。
     *
     * @param groupKey 控制组标识（"base" 或子组名）
     * @param group    控制组对象
     */
    private void discoverWeaponControllersForGroup(String groupKey, ControlGroup group) {
        weaponControllersByGroup.put(groupKey, new ArrayList<>());

        // 收集该控制组全部输出频道
        Set<String> channels = new HashSet<>();
        channels.addAll(group.moveTargets.keySet());
        channels.addAll(group.viewTargets.keySet());
        channels.addAll(group.regularTargets.keySet());
        channels.addAll(group.mainWeaponTargets.keySet());
        channels.addAll(group.secondaryWeaponTargets.keySet());
        for (ControlBinding binding : group.bindings) {
            channels.add(binding.channel);
        }

        // ★ 以控制组名作为信号值，callbackReturnsSignalValue=true，
        //    回调中拿到的 callbackValue 就是 groupKey，从而精确归类。
        for (String channel : channels) {
            if (channel.isEmpty()) continue;
            sendSignalToAllTargetsWithCallback(channel, groupKey, true);
        }
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
        cameraDiscoveryHandshake();
        weaponControllerDiscoveryHandshake();
    }

    /** 主线程记录活动实体视点；null 表示座椅虚拟视点。输入模式按载荷所指设备解析。 */
    public void setActiveCamera(@Nullable AbstractViewpointSubsystem camera) {
        this.activeCamera = camera;
    }

    /**
     * 设置控制组集合<br>
     * 切换前先清除当前控制组的输出信号，防止高优先级信号残留。
     */
    public void setControlGroupSet(ControlGroupSet cgs) {
        // 先清除当前控制组所有频道的信号（使用旧 controlGroupSet 的频道列表）
        if (this.controlGroupSet != ControlGroupSet.EMPTY) {
            clearInputSignals();
        }
        this.controlGroupSet = cgs != null ? cgs : ControlGroupSet.EMPTY;
    }

    /**
     * 收集所有控制组和 GUI 元素的全部目标名称并集（baseGroup + 全部子组 + 全部绑定 + GUI 动作）。
     * 用于注册和显示所有可能的目标频道与接收者，实际发送信号时应使用 getMerged*Targets() 按激活态发送。
     */
    public Map<String, List<String>> setUpTargets(Map<String, List<String>> map) {
        map.putAll(controlGroupSet.getAllMoveTargets());
        map.putAll(controlGroupSet.getAllRegularTargets());
        map.putAll(controlGroupSet.getAllViewTargets());
        map.putAll(controlGroupSet.getAllMainWeaponTargets());
        map.putAll(controlGroupSet.getAllSecondaryWeaponTargets());
        map.putAll(controlGroupSet.getAllBindingTargets());
        map.putAll(controlGroupSet.getAllGuiActionTargets());
        map.putAll(cameraDiscoveryTargets);
        return map;
    }

    @Override
    public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
        if (channelName.equals("callback") && sender instanceof AbstractViewpointSubsystem camera) {
            if (camera.getOwner().getSubPart().getPart().assembly
                    == this.getOwner().getSubPart().getPart().assembly) {
                if (!discoveredCameras.contains(camera)) {
                    discoveredCameras.add(camera);
                }
            }
        } else if (channelName.equals("callback") && sender instanceof WeaponControllerSubsystem wc) {
            if (wc.getOwner().getSubPart().getPart().assembly
                    == this.getOwner().getSubPart().getPart().assembly) {
                // 从回调信号值中取出控制组名
                Object callbackValue = getSignalChannel("callback").get(sender);
                if (callbackValue instanceof String groupKey) {
                    List<WeaponControllerSubsystem> list =
                            weaponControllersByGroup.computeIfAbsent(groupKey, k -> new ArrayList<>());
                    if (!list.contains(wc)) {
                        list.add(wc);
                    }
                }
            }
        }
        return super.onSignalUpdated(channelName, sender);
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        discoveredCameras.clear();
        cameraDiscoveryHandshake();
        weaponControllersByGroup.clear();
        weaponControllerDiscoveryHandshake();
    }

    /**
     * 清除当前控制组所有输出频道的信号，包括武器频道。<br>
     * 切换控制组时必须调用，防止上一组的高优先级信号残留。
     */
    public void clearInputSignals() {
        viewSourcePart = -1;
        viewSourceName = "";
        Map<String, List<String>> all = new HashMap<>();
        all.putAll(controlGroupSet.getAllMoveTargets());
        all.putAll(controlGroupSet.getAllRegularTargets());
        all.putAll(controlGroupSet.getAllViewTargets());
        all.putAll(controlGroupSet.getAllMainWeaponTargets());
        all.putAll(controlGroupSet.getAllSecondaryWeaponTargets());
        for (String signalKey : all.keySet()) {
            this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
        }
    }

    public void setMoveInputSignal(byte[] inputs, byte[] conflicts) {
        Map<String, List<String>> targets = controlGroupSet.getMergedMoveTargets();
        if (!targets.isEmpty() && this.isActive()) {
            for (String signalKey : targets.keySet()) {
                this.sendSignalToAllTargets(signalKey, new MoveInputSignal(inputs, conflicts));
            }
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
            for (String signalKey : targets.keySet()) {
                this.sendSignalToAllTargets(signalKey, new RegularInputSignal(inputType, tickCount));
            }
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
        for (String channel : controlGroupSet.getMergedViewTargets().keySet()) {
            sendSignalToAllTargets(channel, input);
        }
        getSubPart().getPart().assembly.activatePhysics();
        return true;
    }

    /**
     * 设置主武器控制信号，发送到当前控制组的 mainWeaponTargets 频道。<br>
     * 复用 RegularInputSignal，支持 MAIN_FIRE / NEXT_AMMO_TYPE 等按键语义。<br>
     * 可被 AI 实体直接调用而不需经过控制组绑定。
     *
     * @param inputType 武器控制按键类型（如 MAIN_FIRE、NEXT_AMMO_TYPE）
     * @param tickCount 0=按下/按下中, 非0=松开
     */
    public void setMainWeaponInputSignal(KeyInputMapping inputType, int tickCount) {
        Map<String, List<String>> targets = controlGroupSet.getMergedMainWeaponTargets();
        if (!targets.isEmpty() && this.isActive()) {
            for (String signalKey : targets.keySet()) {
                // hold 类型按键松开时(tickCount!=0)发送 EmptySignal 清除频道，防止走火
                Object signal = (inputType == KeyInputMapping.MAIN_FIRE && tickCount != 0)
                        ? EmptySignal.INSTANCE
                        : new RegularInputSignal(inputType, tickCount);
                this.sendSignalToAllTargets(signalKey, signal);
            }
            this.getOwner().getSubPart().part.assembly.activatePhysics();
        } else {
            for (String signalKey : targets.keySet()) {
                this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
            }
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
            for (String signalKey : targets.keySet()) {
                // hold 类型按键松开时(tickCount!=0)发送 EmptySignal 清除频道，防止走火
                Object signal = (inputType == KeyInputMapping.SECONDARY_FIRE && tickCount != 0)
                        ? EmptySignal.INSTANCE
                        : new RegularInputSignal(inputType, tickCount);
                this.sendSignalToAllTargets(signalKey, signal);
            }
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
