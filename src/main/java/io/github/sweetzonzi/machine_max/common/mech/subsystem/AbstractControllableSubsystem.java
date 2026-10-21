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
import io.github.sweetzonzi.machine_max.util.data.KeyInputMapping;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
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

    protected AbstractControllableSubsystem(ISubsystemHost owner, String name, BasicSubsystemDynamicAttr attr) {
        super(owner, name, attr);
    }

    // ===== 摄像机发现 =====

    /** 握手发现的摄像机列表（线程安全）
     * -- GETTER --
     * 返回握手发现的摄像机列表
     */
    @Getter
    protected final List<CameraSubsystem> discoveredCameras = new CopyOnWriteArrayList<>();

    /** 当前激活的摄像机（炮镜模式时设置），null 表示座椅直发模式 */
    @Nullable
    protected CameraSubsystem activeCamera = null;

    /**
     * 摄像机发现握手配置 {频道名 → [目标接收者名列表]}。
     * 子类（SeatSubsystem 等）在构造函数中从动态属性赋值。
     */
    protected Map<String, List<String>> cameraDiscoveryTargets = Map.of();

    /**
     * 摄像机发现握手，向 cameraDiscoveryTargets 配置的每个频道发送空信号（带回调），
     * CameraSubsystem 收到后通过 callback 回复，座椅在 onSignalUpdated 中缓存。
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
     * 武器控制器在控制组中承担的角色。<br>
     * 由发现握手按“该控制器接受了哪一类武器频道的握手”判定：接受主武器频道的是主武器，
     * 只接受副武器频道的是副武器。这一判定与开火指令的走线同源，因此角色总是与“哪个扳机
     * 能打响它”一致。
     */
    public enum WeaponRole {
        /** 主武器：由控制组的 main_weapon_targets 频道握手发现 */
        MAIN,
        /** 副武器：只由控制组的 secondary_weapon_targets 频道握手发现 */
        SECONDARY
    }

    /**
     * 武器控制器 → 角色。<br>
     * 一个控制器可能被同一控制组的主、副武器频道同时发现（既响应主扳机也响应副扳机），
     * 此时记录 {@link WeaponRole#MAIN}；未被任何武器频道发现（只经由瞄准、移动或绑定
     * 频道发现）时不在本表中，查询侧按 {@link WeaponRole#MAIN} 兜底。
     */
    private final Map<WeaponControllerSubsystem, WeaponRole> weaponControllerRoles =
            new ConcurrentHashMap<>();

    /**
     * 当前正在握手的频道所属的武器角色，null = 该频道不是武器频道。<br>
     * 仅在 {@link #discoverWeaponControllersForGroup} 的发送循环内非空：握手回调
     * 在同一次发送调用中同步完成，回调侧因此读到的总是当前频道的角色。
     */
    @Nullable
    private volatile WeaponRole handshakingRole = null;

    /**
     * 武器控制器发现握手：遍历所有控制组的全部输出频道，通过回调发现 WeaponController。<br>
     * 利用回调的信号值携带控制组名，实现精确归属归类；同时按武器频道记录角色。
     */
    protected void weaponControllerDiscoveryHandshake() {
        weaponControllersByGroup.clear();
        weaponControllerRoles.clear();
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
        //    回调在同一次发送调用内同步完成，因此 handshakingRole 描述的正是当前频道所属的角色。
        for (String channel : channels) {
            if (channel.isEmpty()) continue;
            handshakingRole = roleOfWeaponChannel(group, channel);
            try {
                sendSignalToAllTargetsWithCallback(channel, groupKey, true);
            } finally {
                handshakingRole = null;
            }
        }
    }

    /**
     * 频道在控制组中承担的武器角色。<br>
     * 主武器频道的键 → {@link WeaponRole#MAIN}；副武器频道的键 → {@link WeaponRole#SECONDARY}；
     * 其余频道（瞄准、移动、常规、绑定等）→ null。同一频道同时出现在两个表中时取主武器。
     */
    @Nullable
    private static WeaponRole roleOfWeaponChannel(ControlGroup group, String channel) {
        if (group.mainWeaponTargets.containsKey(channel)) return WeaponRole.MAIN;
        if (group.secondaryWeaponTargets.containsKey(channel)) return WeaponRole.SECONDARY;
        return null;
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
     * 当前激活控制组对应的 WeaponController 列表，按角色分组（HUD 显示用）。<br>
     * 键只包含实际出现过的角色，顺序固定为 {@link WeaponRole#MAIN} → {@link WeaponRole#SECONDARY}；
     * 组内顺序与 {@link #getActiveWeaponControllers()} 一致。未被武器频道发现的控制器
     * 归入 {@link WeaponRole#MAIN}。
     * <p>
     * <b>调用线程：</b>主线程（HUD 刷新）。
     *
     * @return 角色 → 该角色的控制器列表（各组为可变列表，调用方不修改）
     */
    public Map<WeaponRole, List<WeaponControllerSubsystem>> getActiveWeaponControllersByRole() {
        Map<WeaponRole, List<WeaponControllerSubsystem>> byRole = new EnumMap<>(WeaponRole.class);
        for (WeaponControllerSubsystem wc : getActiveWeaponControllers()) {
            WeaponRole role = weaponControllerRoles.getOrDefault(wc, WeaponRole.MAIN);
            byRole.computeIfAbsent(role, k -> new ArrayList<>()).add(wc);
        }
        return byRole;
    }

    @Override
    public void onAttach() {
        super.onAttach();
        // 暂不允许自定义控制组，每次从注册表读取预设
        restoreControlGroupPreset();
        cameraDiscoveryHandshake();
        weaponControllerDiscoveryHandshake();
    }

    /**
     * 设置当前激活的摄像机（炮镜模式切换时由 CameraController 调用）。
     * 客户端侧用于读取稳定标志构造网络包；服务端侧暂无用途（信号由 handler 直接发送）。
     *
     * @param camera 激活的摄像机，null 表示退出炮镜回到座椅直发模式
     */
    public void setActiveCamera(@Nullable CameraSubsystem camera) {
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
        if (channelName.equals("callback") && sender instanceof CameraSubsystem camera) {
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
                // 记录角色：主武器频道覆盖副武器频道，未被角色频道发现时不入表
                WeaponRole role = handshakingRole;
                if (role == WeaponRole.MAIN) {
                    weaponControllerRoles.put(wc, WeaponRole.MAIN);
                } else if (role == WeaponRole.SECONDARY) {
                    weaponControllerRoles.putIfAbsent(wc, WeaponRole.SECONDARY);
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
        Map<String, List<String>> all = new HashMap<>();
        all.putAll(controlGroupSet.getMergedMoveTargets());
        all.putAll(controlGroupSet.getMergedRegularTargets());
        all.putAll(controlGroupSet.getMergedViewTargets());
        all.putAll(controlGroupSet.getMergedMainWeaponTargets());
        all.putAll(controlGroupSet.getMergedSecondaryWeaponTargets());
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

    /**
     * 设置视角输入信号（携带无稳轴鼠标增量偏移）。<br>
     * 炮镜模式下由 onTick 从 activeCamera volatile 字段直接读取后调用。
     *
     * @param aimPoint         玩家瞄准点的世界坐标，null 表示无有效瞄准目标
     * @param pitchOffsetDeg   本 tick 俯仰鼠标增量（度）
     * @param yawOffsetDeg     本 tick 偏航鼠标增量（度）
     * @param pitchStabilized  俯仰轴是否稳定
     * @param yawStabilized    偏航轴是否稳定
     */
    public void setViewInputSignal(@Nullable Vec3 aimPoint,
                                    float pitchOffsetDeg, float yawOffsetDeg,
                                    boolean pitchStabilized, boolean yawStabilized) {
        Map<String, List<String>> targets = controlGroupSet.getMergedViewTargets();
        if (!targets.isEmpty() && this.isActive()) {
            for (String signalKey : targets.keySet()) {
                this.sendSignalToAllTargets(signalKey,
                        aimPoint != null
                                ? new ViewInputSignal(aimPoint, pitchOffsetDeg, yawOffsetDeg, pitchStabilized, yawStabilized)
                                : EmptySignal.INSTANCE);
            }
            if (aimPoint != null) {
                this.getOwner().getSubPart().part.assembly.activatePhysics();
            }
        } else {
            for (String signalKey : targets.keySet()) {
                this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
            }
        }
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
                setControlGroupSet(preset);
            }
        }
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
