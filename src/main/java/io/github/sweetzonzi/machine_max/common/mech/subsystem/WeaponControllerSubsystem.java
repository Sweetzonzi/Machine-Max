package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.jme3.math.Vector3f;

import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.RegularInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.RotationSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.signal.ViewInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.WeaponControllerSubsystemAttr;
import io.github.sweetzonzi.machine_max.util.data.KeyInputMapping;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 武器控制器子系统。<br>
 * 接收目标坐标和开火指令，控制炮塔驱动子系统指向目标，
 * 并控制发射器子系统在瞄准完毕后开火。<br>
 * 弹药管理：弹链模式，按弹链（有序循环弹种序列）选择和路由弹药的决策。<br>
 * 通过握手（callback）自动发现并绑定同载具内的 TurretDriver 和 Launcher 子系统。
 */
@Getter
public class WeaponControllerSubsystem extends BasicSubsystem {

    public final WeaponControllerSubsystemAttr attr;

    /**
     * 通过握手发现的炮塔驱动子系统及其对应的控制频道名
     */
    private final Map<TurretDriverSubsystem, String> turrets = new HashMap<>();
    /**
     * 通过握手发现的发射器子系统及其对应的控制频道名
     */
    private final Map<LauncherSubsystem, String> launchers = new HashMap<>();

    /**
     * 从 ViewInputSignal 读到的目标世界坐标
     */
    private volatile Vec3 targetPosition = null;
    /**
     * 完整的视角输入信号（含分轴稳定/偏移信息）
     */
    private volatile ViewInputSignal currentViewSignal = null;
    /**
     * 当前是否有开火指令
     */
    private volatile boolean firing = false;

    /**
     * 弹药切换信号（来自座椅透传的按键信号）
     */
    private volatile boolean ammoSwitchPressed = false;

    /**
     * 弹药切换防抖计数器
     */
    private int ammoSwitchCooldown = 0;

    /**
     * 轮射模式下当前发射的索引
     */
    private int rippleIndex = 0;
    /**
     * 轮射模式下的tick计时器
     */
    private int rippleTickCounter = 0;

    // ==================== 弹药管理（弹链模式） ====================

    /**
     * 用户当前选中的弹链（展平列表），null = 无选择（不装填但可发射膛内已有弹药）。
     */
    @Nullable
    private List<ResourceLocation> selectedBelt = null;

    /**
     * 聚合弹药池快照。<br>
     * key = 弹链（展平的不可变列表），value = 提供该弹链的所有 Loader 条目（按装填速度升序）。
     * 载具结构变化时由 onVehicleStructureChanged 触发完整重建，volatile 保证跨线程可见。
     */
    private volatile Map<List<ResourceLocation>, List<LoaderEntry>> ammoPool = Map.of();

    /**
     * 自上次完整重建以来的 tick 计数，用于每 10 tick 轻量刷新 availableCount。
     */
    private int tickSincePoolRebuild = 0;

    /**
     * 是否需要重建弹药池。<br>
     * 载具结构变化或发现新 Launcher 时设为 true，在下一个 onTick() 中执行实际重建。<br>
     * 延迟重建确保 handshake 回调（Loader → Launcher 注册）已完成，
     * 避免路由时 supplierChannels 为空。
     */
    private boolean needsAmmoPoolUpdate = true;

    /**
     * Loader 条目（弹药池视图中的元素，供 HUD 和路由使用）。
     */
    public record LoaderEntry(
            IAmmoSupplier loader,
            List<ResourceLocation> belt,       // 弹链序列（不可变）
            int availableCount,                // 该 Loader 的可用弹药数
            int reloadTimeTicks,               // 装填耗时
            String channel                     // 所属频道名（HUD 显示用，不影响路由）
    ) {
    }

    public WeaponControllerSubsystem(ISubsystemHost owner, String name, WeaponControllerSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    @Override
    public void onTick() {
        super.onTick();

        // 延迟重建弹药池：确保 handshake 回调已完成，supplierChannels 已就绪
        if (needsAmmoPoolUpdate) {
            rebuildAmmoPool();
            needsAmmoPoolUpdate = false;
        }

        readInputSignals();

        // 弹药切换防抖递减
        if (ammoSwitchCooldown > 0) ammoSwitchCooldown--;

        // 每 10 tick 轻量刷新弹药池 availableCount
        tickSincePoolRebuild++;
        if (tickSincePoolRebuild >= 10) {
            tickSincePoolRebuild = 0;
            refreshAmmoPoolCounts();
        }
    }

    @Override
    public void onPrePhysicsTick() {
        super.onPrePhysicsTick();

        // 0) 读取标准化的武器控制指令（AI/脚本可直接发送 RegularInputSignal 至此）
        updateRegularInputs();

        // 1) 弹药路由：在瞄准/开火之前执行
        routeAmmoToLaunchers();

        // 物理线程开始时读取一次 volatile 字段到局部变量，避免竞态
        ViewInputSignal vis = this.currentViewSignal;
        Vec3 target = this.targetPosition;
        if (!isActive() || isDestroyed() || vis == null || target == null) {
            resetSignalOutputs();
            return;
        }

        var staticAttr = attr.staticAttribute;
        float tolDeg = staticAttr.getAimToleranceDeg();

        // ① 分轴驱动每个炮塔：稳定轴位置控制(aimPoint)，无稳轴增量偏移(turret current + offset)
        for (Map.Entry<TurretDriverSubsystem, String> entry : turrets.entrySet()) {
            TurretDriverSubsystem turret = entry.getKey();
            String channel = entry.getValue();
            if (turret.isDestroyed() || !turret.isActive()) continue;

            Vector3f targetAngle = new Vector3f(turret.getRelativeAngle());
            boolean computed = false;
            Vector3f aimAngles = null;

            // 俯仰轴（x）
            if (vis.pitchStabilized) {
                aimAngles = turret.computeAimAngles(target);
                computed = true;
                targetAngle.x = aimAngles.x;
            } else if (vis.pitchOffsetDeg != 0) {
                targetAngle.x += (float) Math.toRadians(vis.pitchOffsetDeg);
            }
            // 无稳且 offset=0 → targetAngle.x 保持当前角度（炮塔锁死）

            // 偏航轴（y）
            if (vis.yawStabilized) {
                if (!computed) {
                    aimAngles = turret.computeAimAngles(target);
                }
                targetAngle.y = aimAngles.y;
            } else if (vis.yawOffsetDeg != 0) {
                targetAngle.y += (float) Math.toRadians(vis.yawOffsetDeg);
            }
            // 无稳且 offset=0 → targetAngle.y 保持当前角度（炮塔锁死）

            sendCallbackToListener(channel, turret, new RotationSignal(targetAngle));
        }

        // ② 筛选瞄准目标的发射器
        List<LauncherSubsystem> aimedLaunchers = new ArrayList<>();
        for (LauncherSubsystem launcher : launchers.keySet()) {
            if (launcher.isDestroyed() || !launcher.isActive()) continue;
            if (launcher.isAimedAt(target, tolDeg)) {
                aimedLaunchers.add(launcher);
            }
        }

        // ③ 按射击模式开火
        if (firing && !aimedLaunchers.isEmpty()) {
            switch (staticAttr.getDefaultFireMode()) {
                case SALVO -> fireSalvo(aimedLaunchers);
                case RIPPLE -> fireRipple(aimedLaunchers);
            }
        } else {
            // 无开火指令或无可开火发射器 → 发送空信号停止射击
            for (Map.Entry<LauncherSubsystem, String> entry : launchers.entrySet()) {
                sendCallbackToListener(entry.getValue(), entry.getKey(), EmptySignal.INSTANCE);
            }
            rippleTickCounter = 0;
            rippleIndex = 0;
        }
    }

    // ==================== 弹药管理（弹链模式） ====================

    /**
     * 弹药池完整重建。<br>
     * 由 onVehicleStructureChanged() 和首次初始化触发，
     * 遍历所有 launcher 的 supplierChannels，按弹链（展平列表）分组重建 ammoPool。
     */
    private void rebuildAmmoPool() {
        Map<List<ResourceLocation>, List<LoaderEntry>> pool = new HashMap<>();
        Set<IAmmoSupplier> visited = new HashSet<>();

        for (LauncherSubsystem launcher : launchers.keySet()) {
            if (launcher.isDestroyed() || !launcher.isActive()) continue;
            for (Map.Entry<String, List<IAmmoSupplier>> channelEntry : launcher.getSupplierChannels().entrySet()) {
                String channel = channelEntry.getKey();
                for (IAmmoSupplier loader : channelEntry.getValue()) {
                    if (!visited.add(loader)) continue; // 去重

                    if (loader instanceof RegenLoaderSubsystem regenLoader) {
                        List<ResourceLocation> belt = regenLoader.getProjectileTypes();
                        if (belt.isEmpty()) continue;
                        pool.computeIfAbsent(belt, k -> new ArrayList<>())
                                .add(new LoaderEntry(
                                        regenLoader, belt,
                                        regenLoader.getRemainingCount(),
                                        regenLoader.getReloadTimeTicks(),
                                        channel));
                    }
                    // AmmoLoader 后续按弹链模式扩展
                }
            }
        }

        // 每个弹链内：按 reloadTimeTicks 升序排序
        for (Map.Entry<List<ResourceLocation>, List<LoaderEntry>> entry : pool.entrySet()) {
            entry.getValue().sort(Comparator.comparingInt(LoaderEntry::reloadTimeTicks));
        }
        this.ammoPool = Collections.unmodifiableMap(pool);
        this.tickSincePoolRebuild = 0;
    }

    /**
     * 轻量刷新弹药池 availableCount（不改变结构，仅更新数量）。<br>
     * 每 10 tick 调用一次，通过 LoaderEntry 中保存的 IAmmoSupplier 引用获取实时计数。
     */
    private void refreshAmmoPoolCounts() {
        Map<List<ResourceLocation>, List<LoaderEntry>> pool = this.ammoPool;
        if (pool.isEmpty()) return;
        Map<List<ResourceLocation>, List<LoaderEntry>> updated = new HashMap<>();
        for (Map.Entry<List<ResourceLocation>, List<LoaderEntry>> entry : pool.entrySet()) {
            List<ResourceLocation> belt = entry.getKey();
            List<LoaderEntry> updatedEntries = new ArrayList<>(entry.getValue().size());
            for (LoaderEntry le : entry.getValue()) {
                int count = le.loader().getRemainingCount();
                updatedEntries.add(new LoaderEntry(
                        le.loader(), le.belt(), count, le.reloadTimeTicks(), le.channel()));
            }
            updated.put(belt, Collections.unmodifiableList(updatedEntries));
        }
        this.ammoPool = Collections.unmodifiableMap(updated);
    }

    /**
     * 在可用弹链列表中循环切换选中的弹链。
     */
    private void cycleSelectedBelt(boolean reverse) {
        List<List<ResourceLocation>> belts = new ArrayList<>(ammoPool.keySet());
        if (belts.isEmpty()) {
            selectedBelt = null;
            return;
        }
        // 按第一发弹种排序（保持稳定顺序）
        belts.sort(Comparator.comparing(b -> b.isEmpty() ? "" : b.getFirst().toString()));

        if (selectedBelt == null) {
            selectedBelt = belts.getFirst();
            return;
        }
        int idx = belts.indexOf(selectedBelt);
        if (idx < 0) idx = -1; // 当前弹链已不存在，从头开始
        int step = reverse ? -1 : 1;
        int next = (idx + step + belts.size()) % belts.size();
        selectedBelt = belts.get(next);
    }

    /**
     * 为指定 launcher 查找能提供指定弹链的最优 Loader。<br>
     * 按 launcher.supplierChannels 的迭代顺序（= LauncherStaticAttr.ammo_inputs 列表顺序），
     * 在第一个有匹配弹链的频道中选 reloadTimeTicks 最小的 Loader。
     *
     * @return 最优 Loader，若无任何 loader 能提供选中弹链则返回 null
     */
    @Nullable
    private IAmmoSupplier findBestLoaderFor(LauncherSubsystem launcher, List<ResourceLocation> belt) {
        if (belt == null) return null;
        for (Map.Entry<String, List<IAmmoSupplier>> channelEntry :
                launcher.getSupplierChannels().entrySet()) {
            for (IAmmoSupplier loader : channelEntry.getValue()) {
                if (!(loader instanceof AbstractSubsystem sub) || !sub.isActive()) continue;
                if (loader instanceof RegenLoaderSubsystem rl) {
                    if (rl.getProjectileTypes().equals(belt)) {
                        return loader;
                    }
                }
                // AmmoLoader 后续扩展：if (loader instanceof AmmoLoaderSubsystem al) { ... }
            }
        }
        return null;
    }

    /**
     * 弹药路由：为每个 launcher 设置最优 currentSupplier。<br>
     * 若膛内弹种不属于选中弹链，自动退膛。<br>
     * 在 onPrePhysicsTick 中瞄准/开火逻辑之前调用。
     */
    private void routeAmmoToLaunchers() {
        if (!isActive() || isDestroyed()) return;

        // 弹药池尚未重建（handshake 未完成），跳过路由，等待 onTick 中重建
        if (needsAmmoPoolUpdate) return;

        // 处理弹药切换输入
        if (ammoSwitchPressed && ammoSwitchCooldown <= 0) {
            cycleSelectedBelt(false);
            ammoSwitchPressed = false;
            ammoSwitchCooldown = 10; // 10 tick 防抖
        }

        for (LauncherSubsystem launcher : launchers.keySet()) {
            if (launcher.isDestroyed() || !launcher.isActive()) continue;

            if (selectedBelt == null) {
                // 无选中弹链：保留 handshake 建立的默认供给关系
                // 若 currentSupplier 为 null 但 supplierChannels 有 loader，自动选第一个
                if (launcher.getCurrentSupplier() == null) {
                    IAmmoSupplier first = findFirstLoader(launcher);
                    if (first != null) {
                        launcher.setCurrentSupplier(first);
                    }
                }
                continue;
            }

            IAmmoSupplier best = findBestLoaderFor(launcher, selectedBelt);
            if (best == null) {
                // 该 launcher 没有能提供选中弹链的 loader → 退膛
                if (launcher.getChamberedType() != null) launcher.ejectRound();
                launcher.setCurrentSupplier(null);
            } else if (launcher.getCurrentSupplier() != best) {
                // 膛内弹种是否在选中弹链中 → 不在则退膛
                ProjectileType chambered = launcher.getChamberedType();
                ResourceLocation chamberedKey = chambered != null ? chambered.getRegistryKey() : null;
                if (chamberedKey != null && !selectedBelt.contains(chamberedKey)) {
                    launcher.ejectRound();
                }
                launcher.setCurrentSupplier(best);
            }
        }
    }

    /**
     * 查找 launcher 的第一个可用 Loader。<br>
     * 按 supplierChannels 迭代顺序返回第一个频道的第一个 loader，无可用时返回 null。
     */
    @Nullable
    private IAmmoSupplier findFirstLoader(LauncherSubsystem launcher) {
        for (List<IAmmoSupplier> channelLoaders : launcher.getSupplierChannels().values()) {
            for (IAmmoSupplier loader : channelLoaders) {
                if (loader instanceof AbstractSubsystem sub && sub.isActive()) {
                    return loader;
                }
            }
        }
        return null;
    }

    // ==================== 公开查询方法 ====================

    /**
     * 当前选中的弹链，null = 无选择。
     */
    @Nullable
    public List<ResourceLocation> getSelectedBelt() {
        return selectedBelt;
    }

    /**
     * 可用弹链列表（排序后），供 HUD 弹种选择菜单。
     */
    public List<List<ResourceLocation>> getAvailableBelts() {
        return ammoPool.keySet().stream()
                .sorted(Comparator.comparing(b -> b.isEmpty() ? "" : b.getFirst().toString()))
                .toList();
    }

    // ==================== 齐射/轮射 ====================

    /**
     * 齐射：所有已瞄准的发射器同时开火
     */
    private void fireSalvo(List<LauncherSubsystem> aimedLaunchers) {
        for (LauncherSubsystem launcher : aimedLaunchers) {
            String channel = launchers.get(launcher);
            sendCallbackToListener(channel, launcher, 1.0f);
        }
    }

    /**
     * 轮射：按顺序每次只让一个发射器开火，间隔由 rippleIntervalTick 控制
     */
    private void fireRipple(List<LauncherSubsystem> aimedLaunchers) {
        if (aimedLaunchers.isEmpty()) return;

        int interval = (int) (attr.staticAttribute.getRippleInterval() * 20f); // 秒 → tick

        if (rippleTickCounter <= 0) {
            // 发送空信号给所有发射器，先停止上轮射击
            for (Map.Entry<LauncherSubsystem, String> entry : launchers.entrySet()) {
                sendCallbackToListener(entry.getValue(), entry.getKey(), EmptySignal.INSTANCE);
            }

            // 只让当前索引的发射器开火
            if (rippleIndex < aimedLaunchers.size()) {
                LauncherSubsystem launcher = aimedLaunchers.get(rippleIndex);
                String channel = launchers.get(launcher);
                sendCallbackToListener(channel, launcher, 1.0f);
            }

            rippleIndex = (rippleIndex + 1) % aimedLaunchers.size();
            rippleTickCounter = interval;
        } else {
            rippleTickCounter--;
        }
    }

    // ==================== 输入信号 ====================

    /**
     * 从输入信号频道读取目标坐标和开火指令
     */
    private void readInputSignals() {
        // 读取目标坐标：优先尝试 ViewInputSignal（完整的分轴信息），否则 fallback 到 Vec3
        ViewInputSignal newVis = null;
        Vec3 pos = null;
        aim: for (String signalKey : attr.staticAttribute.getAimInputs()) {
            SignalChannel channel = getSignalChannel(signalKey);
            for (Object signal : channel.values()) {
                if (signal instanceof ViewInputSignal vis) {
                    newVis = vis;
                    pos = vis.aimPoint;
                    break aim;
                } else if (signal instanceof Vec3 vec3) {
                    pos = vec3;
                    break aim;
                } else if (signal instanceof Vector3f jmeVec) {
                    pos = new Vec3(jmeVec.x, jmeVec.y, jmeVec.z);
                    break aim;
                }
            }
        }
        this.targetPosition = pos;
        this.currentViewSignal = newVis;

        // 读取开火指令：仅接受 RegularInputSignal（控制组/AI按键）或 Number（ControlBinding HOLD/PRESS），
        // 过滤握手信号（String 类型的 groupKey）防止误触发
        this.firing = false;
        fire: for (String signalKey : attr.staticAttribute.getFireInputs()) {
            SignalChannel channel = getSignalChannel(signalKey);
            for (Object signal : channel.values()) {
                if (signal instanceof RegularInputSignal || signal instanceof Number) {
                    this.firing = true;
                    break fire;
                }
            }
        }

        // 读取弹药切换指令：轮询 ammo_switch 频道
        this.ammoSwitchPressed = false;
        SignalChannel ammoSwitchChannel = getSignalChannel("ammo_switch");
        if (!ammoSwitchChannel.isEmpty() && !(ammoSwitchChannel.getFirstSignal() instanceof EmptySignal)) {
            this.ammoSwitchPressed = true;
        }
    }

    /**
     * 从 fireInputs 频道读取标准化的 RegularInputSignal 武器控制指令。<br>
     * 在 readInputSignals() 之后调用，由 onPrePhysicsTick() 驱动。<br>
     * AI 实体可通过发送 RegularInputSignal 到此子系统直接控制武器，
     * 无需经过控制组绑定。
     */
    private void updateRegularInputs() {
        RegularInputSignal activeRegular = null;
        SignalChannel activeChannel = null;
        ISignalSender activeSender = null;

        for (String signalKey : attr.staticAttribute.getFireInputs()) {
            SignalChannel channel = getSignalChannel(signalKey);
            for (Map.Entry<ISignalSender, Object> entry : channel.entrySet()) {
                if (entry.getValue() instanceof RegularInputSignal ris) {
                    activeRegular = ris;
                    activeChannel = channel;
                    activeSender = entry.getKey();
                    break;
                }
            }
            if (activeRegular != null) break;
        }

        if (activeRegular != null) {
            int tickCount = activeRegular.getInputTickCount();
            switch (activeRegular.getInputType()) {
                case MAIN_FIRE:
                case SECONDARY_FIRE:
                    // hold 语义：tickCount==0 表示按下中，非0表示松开
                    this.firing = tickCount == 0;
                    break;
                case NEXT_AMMO_TYPE:
                    // 一次性事件：仅 tickCount==0（按下瞬间）触发
                    if (tickCount == 0) {
                        cycleSelectedBelt(false);
                        activeChannel.put(activeSender, EmptySignal.INSTANCE);
                    }
                    break;
                case PREV_AMMO_TYPE:
                    // 循环反向切换弹链
                    if (tickCount == 0) {
                        cycleSelectedBelt(true);
                        activeChannel.put(activeSender, EmptySignal.INSTANCE);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 获取已瞄准目标的发射器数量。
     *
     * @param target 目标世界坐标
     * @param tolDeg 瞄准容差（度）
     * @return 已瞄准且可用的发射器数量
     */
    public int getAimedLauncherCount(Vec3 target, float tolDeg) {
        if (target == null) return 0;
        int count = 0;
        for (LauncherSubsystem launcher : launchers.keySet()) {
            if (!launcher.isDestroyed() && launcher.isActive() && launcher.isAimedAt(target, tolDeg)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void loadData(CompoundTag data) {
        super.loadData(data);
        if (data.contains("target_x") && data.contains("target_y") && data.contains("target_z")) {
            this.targetPosition = new Vec3(
                    data.getDouble("target_x"),
                    data.getDouble("target_y"),
                    data.getDouble("target_z")
            );
        }
    }

    @Override
    public CompoundTag saveData(CompoundTag data) {
        super.saveData(data);
        if (targetPosition != null) {
            data.putDouble("target_x", targetPosition.x);
            data.putDouble("target_y", targetPosition.y);
            data.putDouble("target_z", targetPosition.z);
        }
        return data;
    }

    @Override
    public void onAttach() {
        super.onAttach();
        handShake();
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        turrets.clear();
        launchers.clear();
        handShake();
        // 载具结构变化后标记重建弹药池，实际重建延迟到下一个 onTick
        needsAmmoPoolUpdate = true;
    }

    /**
     * 握手：发送空信号到所有控制输出频道，根据回调发现并绑定下属子系统。<br>
     * Handshake: send empty signals to all control output channels,
     * discover and bind sub-subsystems via callbacks.<br>
     * 回调自动由 ISignalSender.sendSignalToTarget 基础设施处理。
     */
    protected void handShake() {
        for (String signalChannel : attr.controlOutputTargets.keySet()) {
            sendSignalToAllTargetsWithCallback(signalChannel, EmptySignal.INSTANCE, false);
        }
    }

    /**
     * 处理回调信号：<br>
     * - "callback" 频道且值为 String（控制频道名），根据发送者类型存入对应集合<br>
     * - 同载具外的子系统（通过连接点穿透）会被过滤移除
     */
    @Override
    public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
        super.onSignalUpdated(channelName, sender);
        Object signalValue = getSignalChannel(channelName).get(sender);
        if (channelName.equals("callback") && signalValue instanceof String controlChannel) {
            if (sender instanceof TurretDriverSubsystem turret) {
                turrets.put(turret, controlChannel);
                addCallbackTarget(controlChannel, turret);
            } else if (sender instanceof LauncherSubsystem launcher) {
                launchers.put(launcher, controlChannel);
                addCallbackTarget(controlChannel, launcher);
                // 发现新 launcher → 标记弹药池待重建，实际在 onTick 中执行
                needsAmmoPoolUpdate = true;
            }
        }
        return SignalResult.PASS;
    }

    @Override
    public List<String> getAcceptedChannels() {
        var staticAttr = attr.staticAttribute;
        List<String> channels = new ArrayList<>(staticAttr.getAimInputs().size() + staticAttr.getFireInputs().size() + 1);
        channels.addAll(staticAttr.getAimInputs());
        channels.addAll(staticAttr.getFireInputs());
        channels.add("ammo_switch");
        return channels;
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(attr.controlOutputTargets);
        return result;
    }
}
