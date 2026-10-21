package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.jme3.math.Vector3f;

import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.RegularInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.signal.ViewInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.SupplierRef;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.WeaponControllerSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.visual.HudLayout;
import io.github.sweetzonzi.machine_max.common.visual.HudView;
import io.github.sweetzonzi.machine_max.common.visual.IHudHost;
import io.github.sweetzonzi.machine_max.util.data.KeyInputMapping;

import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
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
public class WeaponControllerSubsystem extends BasicSubsystem implements IHudHost {

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
     * 当前是否有开火指令。<br>
     * 写点在 {@link #readInputSignals()} 与 {@link #updateRegularInputs()}（都在主线程的
     * {@link #onTick()} 里），读点在 {@link #onPrePhysicsTick()} 的物理步进，因此声明 volatile：
     * 主线程末尾定下的值要能被紧随其后的物理步进看到。
     */
    private volatile boolean firing = false;

    /**
     * 调试自动开火开关。开启后无视 fire_inputs 信号，持续保持 firing = true。<br>
     * 由 /mm debug autofire 命令控制，用于测试武器系统。
     */
    private volatile boolean debugAutoFire = false;

    /**
     * 调试自动开火：发射触发间隔（tick）。
     */
    private volatile int debugTriggerInterval = 20;
    /**
     * 调试自动开火：每次发射持续时长（tick）。
     * 若 >= debugTriggerInterval，则为持续开火。
     */
    private volatile int debugFireDuration = 10;
    /**
     * 调试自动开火：内部循环计数器。
     */
    private int debugFireTickCounter = 0;

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
     * 当前选中的弹链（展平列表）。<br>
     * 弹药池里有链时不会是 null：{@link #rebuildAmmoPool()} 建池后默认选中排序第一条
     * （顺序见 {@link #BELT_ORDER}），选中的链随结构变化消失时也由它改选。池为空时保持 null
     * ——那时没有可选的链，路由改走无弹链的兜底供给者（物理弹仓的 {@code AmmoLoaderSubsystem}）。
     * <p>
     * 写入点都在主线程：建池时的默认选中与改选、{@code ammo_switch} 频道、
     * {@code NEXT_AMMO_TYPE} / {@code PREV_AMMO_TYPE} 指令（{@link #updateRegularInputs()}）、
     * {@link #cycleSelectedBelt(boolean)}；读取点也在主线程（路由与 HUD 刷新）。
     */
    @Nullable
    private List<ResourceLocation> selectedBelt = null;

    /**
     * 弹链的排序顺序：按第一发弹种的资源地址升序。<br>
     * 默认选中（{@link #rebuildAmmoPool()} 建池后）、轮切（{@link #cycleSelectedBelt(boolean)}）
     * 与 HUD 列表（{@link #getAvailableBelts()}）共用它，三者因此总是指向同一条链。
     */
    private static final Comparator<List<ResourceLocation>> BELT_ORDER =
            Comparator.comparing(belt -> belt.isEmpty() ? "" : belt.getFirst().toString());

    /**
     * 选中弹链的同步镜像（服务端 → 客户端）。<br>
     * 值是按顺序拼接的弹种注册键（{@code id1,id2,...}），空串表示当前没有可选的弹链。
     * 选择本身只在服务端决定：客户端读到的 {@link #selectedBelt} 来自本条同步值，
     * 因此 HUD 高亮的选项卡与实际供弹的弹链总是同一条。
     */
    private static final EntityDataAccessor<String> SELECTED_BELT =
            SynchedEntityData.defineId(WeaponControllerSubsystem.class, EntityDataSerializers.STRING);

    /**
     * 客户端收到选择、但弹药池还没建起来时暂存的键串。<br>
     * 建池依赖装弹机握手，握手与同步值谁先到不确定；暂存后在 {@link #onTick()} 里重试落地，
     * 不因为一次"池里还没有这条链"就把选择丢掉。
     */
    @Nullable
    private String pendingSelectedBelt = null;

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

    // ==================== 同步数据 ====================

    /**
     * 定义选中弹链的同步项。<br>
     * 与基类的耐久/激活/摧毁位合并成同一张表，由基类每 tick 打包推送；载具刚在客户端建好时
     * 由 {@code SubsystemController.pushAllTo} 补一份全量快照。
     */
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SELECTED_BELT, "");
    }

    /**
     * 同步值落地（客户端）。<br>
     * 只跟随服务端的选择，不自行改选：池里还没有这条链时先暂存，由 {@link #onTick()} 重试。
     */
    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> dataAccessor) {
        super.onSyncedDataUpdated(dataAccessor);
        if (getLevel().isClientSide() && dataAccessor.equals(SELECTED_BELT)) {
            applySyncedBelt(getSynchedData().get(SELECTED_BELT));
        }
    }

    @Override
    public void onTick() {
        super.onTick();

        // 延迟重建弹药池：确保 handshake 回调已完成，supplierChannels 已就绪
        if (needsAmmoPoolUpdate) {
            rebuildAmmoPool();
            needsAmmoPoolUpdate = false;
        } else if (ammoPool.isEmpty() && tickCount % 20 == 0) {
            // 兜底：池为空说明本控制器没看到任何带弹链的供给者。装弹机的握手是异步完成的，
            // 若某次建池早于它（新部件刚接上、结构变化刚结束），一次空结果不能锁死整个会话——
            // 定期重试，握手一到位池就建起来。
            rebuildAmmoPool();
        }

        // 客户端：选择值可能先于弹药池到达（建池要等装弹机握手），池建好后把暂存的键串落地
        if (getLevel().isClientSide() && pendingSelectedBelt != null) {
            applySyncedBelt(pendingSelectedBelt);
        }

        readInputSignals();

        // 读取标准化的武器控制指令（AI/脚本可直接发送 RegularInputSignal 至此）。
        // 排在 readInputSignals 之后：它要覆盖通用扫描的结论（标准化指令压过通道扫描）。
        // 读信号与用信号分离——这里定下 firing，本 tick 的物理步进再拿它下开火指令。
        updateRegularInputs();

        // 弹药切换：readInputSignals 刚写下 ammoSwitchPressed，这里同 tick 消费，
        // 使路由与 HUD 立刻看到新弹链
        if (ammoSwitchPressed && ammoSwitchCooldown <= 0) {
            cycleSelectedBelt(false);
            ammoSwitchPressed = false;
            ammoSwitchCooldown = 10; // 10 tick 防抖
        }
        if (ammoSwitchCooldown > 0) ammoSwitchCooldown--;

        // 弹药路由：与装弹机的装填计时、发射器的取弹同在主线程
        routeAmmoToLaunchers();

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

        // 物理线程开始时读取一次 volatile 字段到局部变量，避免竞态
        ViewInputSignal vis = this.currentViewSignal;
        Vec3 target = this.targetPosition;
        if (!isActive() || isDestroyed()) {
            pushGuidanceTargets(null);
            releaseAllControl();
            return;
        }
        if (vis == null || target == null) {
            // 无目标时释放所有控制权
            pushGuidanceTargets(null);
            releaseAllControl();
            return;
        }

        var staticAttr = attr.staticAttribute;
        float tolDeg = staticAttr.getAimToleranceDeg();

        // ① 分轴驱动每个炮塔：稳定轴位置控制(aimPoint)，无稳轴增量偏移(turret current + offset)
        for (Map.Entry<TurretDriverSubsystem, String> entry : turrets.entrySet()) {
            TurretDriverSubsystem turret = entry.getKey();
            String channel = entry.getValue();
            if (turret.isDestroyed() || !turret.isActive()) {
                turret.setTargetAngle(channel, null);
                continue;
            }

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

            // 直接调用替代 sendCallbackToListener + RotationSignal
            turret.setTargetAngle(channel, targetAngle);
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
                case SALVO -> fireSalvo(aimedLaunchers, target);
                case RIPPLE -> fireRipple(aimedLaunchers, target);
            }
        } else {
            // 无开火指令或无可开火发射器 → 释放开火权并重置轮射状态
            for (Map.Entry<LauncherSubsystem, String> entry : launchers.entrySet()) {
                entry.getKey().setFireCommand(entry.getValue(), null);
            }
            rippleTickCounter = 0;
            rippleIndex = 0;
        }

        // ④ 向受控发射器推送制导目标（瞄准点）——在飞制导弹据此持续修正
        pushGuidanceTargets(target);
    }

    /**
     * 向所有受控发射器推送制导目标点，并让其转发给本发射器打出的在飞制导弹。
     * <p>
     * 目标点是<b>世界坐标</b>（不一定是实体），可能每物理步变化；{@code null} 表示无目标，
     * 此时在飞制导弹交回纯弹道。首期同一发射器打出的所有制导弹共享同一瞄准点。
     * <p>
     * <b>调用线程：</b>物理线程（{@link #onPrePhysicsTick()}）——与
     * {@code LauncherSubsystem.onPrePhysicsTick()} 同处物理线程 tick，
     * 因此目标推送是对投射物 SoA 的物理线程直写，无需队列。
     *
     * @param target 瞄准点世界坐标，{@code null} = 无目标
     */
    private void pushGuidanceTargets(@Nullable Vec3 target) {
        for (LauncherSubsystem launcher : launchers.keySet()) {
            launcher.setGuidanceTarget(target);
            launcher.updateGuidedTargets();
        }
    }

    // ==================== 弹药管理（弹链模式） ====================

    /**
     * 弹药池完整重建。<br>
     * 由 onVehicleStructureChanged() 和首次初始化触发，
     * 遍历所有 launcher 的 supplierChannels，按弹链（展平列表）分组重建 ammoPool。
     * <p>
     * 只有带弹链概念的供给者（再生装弹机）进入弹药池：池是"HUD 弹链选项卡 + 弹链选择"的数据源，
     * 而 {@code AmmoLoaderSubsystem} 的物理弹仓按装填顺序出弹，没有可枚举的弹链。
     * 无弹链的供给者仍可被路由选中，见 {@link #findSupplier}。
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

                    List<ResourceLocation> belt = beltOf(loader);
                    if (belt.isEmpty()) continue;
                    pool.computeIfAbsent(belt, k -> new ArrayList<>())
                            .add(new LoaderEntry(
                                    loader, belt,
                                    loader.getRemainingCount(),
                                    loader.getReloadTimeTicks(),
                                    channel));
                }
            }
        }

        // 每个弹链内：按 reloadTimeTicks 升序排序
        for (Map.Entry<List<ResourceLocation>, List<LoaderEntry>> entry : pool.entrySet()) {
            entry.getValue().sort(Comparator.comparingInt(LoaderEntry::reloadTimeTicks));
        }
        this.ammoPool = Collections.unmodifiableMap(pool);
        this.tickSincePoolRebuild = 0;
        selectDefaultBelt();
    }

    /**
     * 保证选中的弹链在池里：未选中或选中的链已消失（结构变化后它的供给者被拆掉）时改选第一条。<br>
     * 池为空时保持 null：没有可选的链，路由随后落到无弹链的兜底供给者。
     * <p>
     * <b>调用线程：</b>主线程（{@link #rebuildAmmoPool()}）。
     */
    private void selectDefaultBelt() {
        if (ammoPool.isEmpty() || ammoPool.containsKey(selectedBelt)) return;
        selectedBelt = ammoPool.keySet().stream().min(BELT_ORDER).orElse(null);
        pushSyncedBelt();
    }

    /**
     * 供给者的弹链（展平的弹种序列）。<br>
     * 无弹链概念的供给者（装弹机）返回空列表，因此不进弹药池。
     */
    private static List<ResourceLocation> beltOf(IAmmoSupplier supplier) {
        if (supplier instanceof RegenLoaderSubsystem regenLoader) {
            return regenLoader.getProjectileTypes();
        }
        // AmmoLoader 无弹链：物理弹仓按装填顺序出弹，弹种由 getSuppliedType() 逐发报告
        return List.of();
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
        List<List<ResourceLocation>> belts = getAvailableBelts();
        if (belts.isEmpty()) {
            selectedBelt = null;
        } else {
            int idx = belts.indexOf(selectedBelt);
            if (idx < 0) {
                // 未选中，或选中的链已不在池里：从第一条开始
                selectedBelt = belts.getFirst();
            } else {
                int step = reverse ? -1 : 1;
                selectedBelt = belts.get(Math.floorMod(idx + step, belts.size()));
            }
        }
        pushSyncedBelt();
    }

    /**
     * 把服务端的选择写进同步值（仅服务端）。<br>
     * 客户端读到后经 {@link #applySyncedBelt(String)} 落到本机的 {@link #selectedBelt}。
     */
    private void pushSyncedBelt() {
        if (getLevel().isClientSide()) return;
        getSynchedData().set(SELECTED_BELT, encodeBelt(selectedBelt));
    }

    /**
     * 客户端落地服务端的选择。<br>
     * 池里已经有这条链就改选；还没有（同步值先于握手到达）就暂存，等 {@link #onTick()} 重试。
     */
    private void applySyncedBelt(String encoded) {
        if (encoded.isEmpty()) {
            selectedBelt = null;
            pendingSelectedBelt = null;
            return;
        }
        List<ResourceLocation> matched = findBeltByKey(encoded);
        if (matched != null) {
            selectedBelt = matched;
            pendingSelectedBelt = null;
        } else {
            pendingSelectedBelt = encoded;
        }
    }

    /**
     * 在弹药池里按稳定键串找弹链。
     *
     * @return 对应的弹链；池里还没有这条链时返回 null
     */
    @Nullable
    private List<ResourceLocation> findBeltByKey(String encoded) {
        for (List<ResourceLocation> belt : ammoPool.keySet()) {
            if (encodeBelt(belt).equals(encoded)) return belt;
        }
        return null;
    }

    /**
     * 弹链的稳定键串：按顺序拼接弹种注册键，空表编码为空串。<br>
     * 用作同步值而不是下标——结构变化会改变池里弹链的顺序，键串只随弹链本身变化。
     */
    private static String encodeBelt(@Nullable List<ResourceLocation> belt) {
        if (belt == null || belt.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (ResourceLocation id : belt) {
            if (!builder.isEmpty()) builder.append(',');
            builder.append(id);
        }
        return builder.toString();
    }

    /**
     * 供给者是否可被某个发射器选中：运行中、能接受该弹种、且当前有弹药。<br>
     * 弹种尚未解码（内容包缺投射物定义）时不做否决，交由发射器取弹时按炮闩判定。
     *
     * @return true 表示可以选中该供给者
     */
    private static boolean canSupplyTo(IAmmoSupplier supplier, LauncherSubsystem launcher) {
        return supplyTypeAccepted(supplier, launcher) && supplier.hasAmmo();
    }

    /**
     * 供给者是否仍算这根管的合法供给者：运行中、正在供的弹种炮闩能吃。<br>
     * 与 {@link #canSupplyTo} 的区别是不要求"此刻有余弹"——装填中的装弹机弹仓里
     * 那一发已被认领，余弹数可能已经归零，但它仍在为这根管服务，不该被判为失效。
     */
    private static boolean supplyTypeAccepted(IAmmoSupplier supplier, LauncherSubsystem launcher) {
        if (!(supplier instanceof AbstractSubsystem sub) || !sub.isActive() || sub.isDestroyed()) return false;
        ProjectileType offered = supplier.getSuppliedType();
        return offered == null || launcher.canAccept(offered);
    }

    /**
     * 从弹药池里取指定弹链的全部供给者（按装填耗时升序，同耗时保持池内插入顺序）。<br>
     * 弹药池是每 10 tick 刷新一次的快照，这里只借用它的排序，命中判定仍以实时接口为准。
     *
     * @param belt 选中的弹链；{@code null}（未选弹链）时返回空列表
     */
    private static List<IAmmoSupplier> beltSuppliers(WeaponControllerSubsystem controller,
                                                     @Nullable List<ResourceLocation> belt) {
        if (belt == null) return List.of();
        List<LoaderEntry> entries = controller.ammoPool.get(belt);
        if (entries == null || entries.isEmpty()) return List.of();
        List<IAmmoSupplier> suppliers = new ArrayList<>(entries.size());
        for (LoaderEntry entry : entries) {
            suppliers.add(entry.loader());
        }
        return suppliers;
    }

    /**
     * 占用档位：0 = 本管现在就能用它（空闲，或本就允许多消费者并行），1 = 它正为别的管交付、只能等。<br>
     * 独占装弹机（{@code canSupplyMultiple=false}）同时只服务一根管，前一根管占走一台后，那台对
     * 后一根管就落到 1 档，因此 N 根管会自动摊到 N 台装弹机上。
     */
    private static int occupancyTier(IAmmoSupplier supplier, LauncherSubsystem launcher) {
        if (supplier.canSupplyMultiple()) return 0;
        return supplier.hasScheduledRound() && !supplier.isRoundScheduled(launcher) ? 1 : 0;
    }

    /**
     * 现在就能服务本管：有余弹，或已为本管在途/备好一发。<br>
     * 后者不可省：正在装填的那台弹仓计数可能已经归零，但那一发是给本管的。
     */
    private static boolean canServe(LauncherSubsystem launcher, IAmmoSupplier supplier) {
        return supplier.hasAmmo() || supplier.isRoundScheduled(launcher);
    }

    /**
     * 当前供给者是否值得留：拿它与最优候选逐级比。<br>
     * <ol>
     *   <li>弹链匹配（硬条件：玩家选的弹链优先于一切，包括"已经有一发在装填中"）</li>
     *   <li>炮闩吃得下它供的弹种（硬条件）</li>
     *   <li>现在就能服务本管——干着空转的那台在这里被降级，让位给有弹的候选</li>
     *   <li>到手的东西不放弃：已为本管备好的一发留住</li>
     *   <li>换人窗口：在途的这一发走过 2 tick 或更多就留着装完（判据
     *       {@code getDeliveryElapsedTicks(launcher) > 1}）。多让这一格是因为各子系统的 tick 顺序不定：
     *       装弹机在上一 tick 收到请求、本 tick 先步进计时器，武器控制器最后才判定，本方法读到
     *       经过时间为 1 时那一发其实刚起步，卡在 1 上等于换人没有机会。这一发是慢的也认了，
     *       下一发再从更好的机器开始</li>
     *   <li>占用档位：不去抢正在为别的管装填的机器（保住 N 管摊 N 台）</li>
     *   <li>装填耗时：更快的那台赢</li>
     *   <li>身份：稳定决胜，使同一份载具状态下结果可复现</li>
     * </ol>
     *
     * @param current   当前指认，可为 null（此时一律该换）
     * @param candidate {@link #findSupplier} 给出的最优候选，非 null 且不等于 current
     * @param belt      选中的弹链，null = 未选
     * @return true 表示留着当前供给者
     */
    private boolean isWorthKeeping(LauncherSubsystem launcher, @Nullable IAmmoSupplier current,
                                   IAmmoSupplier candidate, @Nullable List<ResourceLocation> belt) {
        if (current == null || !supplyTypeAccepted(current, launcher)) return false;
        if (belt != null && !beltSuppliers(this, belt).contains(current)) return false;
        if (!canServe(launcher, current)) return false;
        // 已备好的一发绝不放弃（isRoundReady 在"未排上"的分支里会顺手重发请求，所以先判 isRoundScheduled）
        if (current.isRoundScheduled(launcher) && current.isRoundReady(launcher)) return true;
        // 窗口已关：这一发算承诺给它的，等它装完；-1 表示根本没在为本管输送，没有承诺。
        // 阈值取字面量 1（经过时间 > 1 才关门）：装弹机可能在本 tick 先步进计时器、武器控制器后判定，
        // 读到的 1 其实还是刚起步的那一发，取 0 会让换人彻底没有机会。
        if (current.getDeliveryElapsedTicks(launcher) > 1) return true;
        int currentTier = occupancyTier(current, launcher);
        int candidateTier = occupancyTier(candidate, launcher);
        if (currentTier != candidateTier) return currentTier < candidateTier;
        int currentReload = Math.max(0, current.getReloadTimeTicks());
        int candidateReload = Math.max(0, candidate.getReloadTimeTicks());
        if (currentReload != candidateReload) return currentReload < candidateReload;
        return SupplierRef.of(current).compareTo(SupplierRef.of(candidate)) <= 0;
    }

    /**
     * 为发射器选定供给者：先取选中弹链的供给者，没有再退到任意可用供给者。<br>
     * 兜底覆盖面更广——{@code AmmoLoaderSubsystem} 不在弹药池里（物理弹仓没有弹链），
     * 只认池内条目的实现会让这类装弹机永远选不上。
     * <p>
     * 兜底还有一个前提：<b>选中弹链上确实没有能接这活的供给者</b>。弹链上有人、只是暂时没弹时应当
     * 等它，而不是跑去用别的弹链——否则每 tick 都会在两条弹链之间来回换（换人 → 取消在途交付 →
     * 下个 tick 又换回来），一发都装不上。守住当前供给者时同样要求它位于选中弹链上：选中链
     * 暂时空着就等它再生，不从别的链借弹，打出去的弹种因此总是选项卡上写的那一种。
     * <p>
     * 每条路都按"已轮到本管 → 空闲 → 被别人占着"排序，同档内再按装填耗时、最后按供给者身份
     * 排序。前两档让 N 根管自动落到 N 台装弹机上（前一根管选走一台后，那台对后一根管就降档成
     * "被别人占着"）；身份是稳定的决胜键，使同一份载具状态下每次摊派的结果一致。
     *
     * @return 选定的供给者；无可用供给者时返回 null
     */
    @Nullable
    private IAmmoSupplier findSupplier(LauncherSubsystem launcher,
                                       @Nullable List<ResourceLocation> selectedBelt) {
        List<IAmmoSupplier> usable = new ArrayList<>();
        List<IAmmoSupplier> beltLoaders = beltSuppliers(this, selectedBelt);
        for (IAmmoSupplier loader : beltLoaders) {
            if (loader != launcher.getCurrentSupplier() && canSupplyTo(loader, launcher)) usable.add(loader);
        }
        boolean beltHasTaker = beltLoaders.stream().anyMatch(loader -> supplyTypeAccepted(loader, launcher));
        if (usable.isEmpty() && !beltHasTaker) {
            Map<IAmmoSupplier, Boolean> seen = new HashMap<>();
            for (List<IAmmoSupplier> channelLoaders : launcher.getSupplierChannels().values()) {
                for (IAmmoSupplier loader : channelLoaders) {
                    if (seen.put(loader, Boolean.TRUE) != null) continue;
                    if (canSupplyTo(loader, launcher)) usable.add(loader);
                }
            }
        }
        if (usable.isEmpty()) {
            // 没有更该选的：当前供给者要同时满足"还能供弹"和"在选中弹链上"才守住它。
            // 只判前者会在切到一条暂时空着的链后继续从旧链取弹，打出去的弹种和选项卡对不上。
            IAmmoSupplier current = launcher.getCurrentSupplier();
            if (current == null || !supplyTypeAccepted(current, launcher)) return null;
            boolean onSelectedBelt = selectedBelt == null
                    || beltSuppliers(this, selectedBelt).contains(current);
            return onSelectedBelt ? current : null;
        }
        usable.sort(Comparator
                .comparingInt((IAmmoSupplier supplier) -> occupancyTier(supplier, launcher))
                .thenComparingInt(IAmmoSupplier::getReloadTimeTicks)
                .thenComparing(SupplierRef::of));
        return usable.getFirst();
    }

    /**
     * 弹药路由：为每根管选定它的装弹机（{@code currentSupplier}）。<br>
     * 由 {@link #onTick()} 在主线程调用，与装弹机的装填计时、发射器的取弹同步。
     * <p>
     * <b>仅服务端执行：</b>指认是权威决策，客户端经发射器上的供给者身份同步值跟随
     * （见 {@code LauncherSubsystem.SUPPLIER_ID}），不自行挑选——两端各自跑一遍路由会因为
     * 本机计时器与滞后一个往返的余弹读数而摊出不同的配对。
     */
    private void routeAmmoToLaunchers() {
        if (getLevel().isClientSide()) return;
        if (!isActive() || isDestroyed()) return;

        // 弹药池尚未重建（handshake 未完成），跳过路由，等待下一次 onTick 重建
        if (needsAmmoPoolUpdate) return;

        List<LauncherSubsystem> activeLaunchers = new ArrayList<>();
        for (LauncherSubsystem launcher : launchers.keySet()) {
            if (!launcher.isDestroyed() && launcher.isActive()) activeLaunchers.add(launcher);
        }
        // 按名字排序：launchers 是无序 Map，不定序会让分配结果每 tick 抖动
        activeLaunchers.sort(Comparator.comparing(l -> l.name));

        List<ResourceLocation> belt = selectedBelt;
        for (LauncherSubsystem launcher : activeLaunchers) {
            IAmmoSupplier current = launcher.getCurrentSupplier();
            IAmmoSupplier candidate = findSupplier(launcher, belt);

            // 当前就是候选（含"没有更好的，守住它"那条兜底）→ 不动
            if (candidate == current) continue;
            // 否则逐级比：当前不输给候选就留（判据见 isWorthKeeping）
            if (candidate != null && isWorthKeeping(launcher, current, candidate, belt)) continue;

            if (candidate == null) {
                // 没有任何可用供给者：清掉指认，膛内那发照打（退膛是显式操作，不在这里替玩家决定）
                launcher.setCurrentSupplier(null);
                continue;
            }
            // 换人：旧供给者那边的待交付由 setCurrentSupplier 内部 cancelRound 掉，
            // 那发仍在旧装弹机的弹仓里（见 RegenLoaderSubsystem#cancelRound），不会丢
            launcher.setCurrentSupplier(candidate);
        }
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
     * 设置调试自动开火开关。启用时重置内部计数器。
     */
    public void setDebugAutoFire(boolean v) {
        this.debugAutoFire = v;
        if (v) {
            this.debugFireTickCounter = 0;
        }
    }

    public void setDebugTriggerInterval(int v) {
        this.debugTriggerInterval = Math.max(1, v);
    }

    public void setDebugFireDuration(int v) {
        this.debugFireDuration = Math.max(1, v);
    }

    /**
     * 可用弹链列表，供 HUD 弹种选择菜单。<br>
     * 顺序同 {@link #BELT_ORDER}，也就是默认选中与轮切所用的顺序。
     */
    public List<List<ResourceLocation>> getAvailableBelts() {
        return ammoPool.keySet().stream().sorted(BELT_ORDER).toList();
    }

    // ==================== 齐射/轮射 ====================

    /**
     * 齐射：所有已瞄准的发射器同时开火。<br>
     * 先停火所有发射器（释放上次控制权），再让已瞄准的同时开火。
     */
    private void fireSalvo(List<LauncherSubsystem> aimedLaunchers, Vec3 targetPos) {
        float correctionDeg = attr.staticAttribute.getFireCorrectionAngleDeg();
        // 先停火所有发射器
        for (Map.Entry<LauncherSubsystem, String> entry : launchers.entrySet()) {
            entry.getKey().setFireCommand(entry.getValue(), null);
        }
        // 已瞄准的同时开火
        for (LauncherSubsystem launcher : aimedLaunchers) {
            String channel = launchers.get(launcher);
            launcher.setFireCommand(channel,
                new LauncherSubsystem.FireCommand(computeFireDirection(launcher, targetPos), correctionDeg, true));
        }
    }

    /**
     * 轮射：按顺序每次只让一个发射器开火，间隔由 rippleInterval 控制。
     */
    private void fireRipple(List<LauncherSubsystem> aimedLaunchers, Vec3 targetPos) {
        if (aimedLaunchers.isEmpty()) return;

        float correctionDeg = attr.staticAttribute.getFireCorrectionAngleDeg();
        int interval = (int) (attr.staticAttribute.getRippleInterval() * 20f); // 秒 → tick

        if (rippleTickCounter <= 0) {
            // 先停火所有发射器
            for (Map.Entry<LauncherSubsystem, String> entry : launchers.entrySet()) {
                entry.getKey().setFireCommand(entry.getValue(), null);
            }
            // 只让当前索引的发射器开火
            if (rippleIndex < aimedLaunchers.size()) {
                LauncherSubsystem launcher = aimedLaunchers.get(rippleIndex);
                String channel = launchers.get(launcher);
                launcher.setFireCommand(channel,
                    new LauncherSubsystem.FireCommand(computeFireDirection(launcher, targetPos), correctionDeg, true));
            }
            rippleIndex = (rippleIndex + 1) % aimedLaunchers.size();
            rippleTickCounter = interval;
        } else {
            rippleTickCounter--;
        }
    }

    /**
     * 计算从 launcher 枪口指向 target 的单位方向向量（JME Vector3f）。
     */
    private Vector3f computeFireDirection(LauncherSubsystem launcher, Vec3 targetPos) {
        Vec3 muzzlePos = launcher.getMuzzleWorldPosition();
        Vec3 toTarget = targetPos.subtract(muzzlePos).normalize();
        return new Vector3f((float) toTarget.x, (float) toTarget.y, (float) toTarget.z);
    }

    /**
     * 释放对所有已绑定 Consumer 的控制权。<br>
     * 遍历 turrets/launchers，为每个条目调用 setXxx(channel, null/false)。<br>
     * <b>调用线程：</b>可能从主线程（onTick releaseAllControl）或物理线程（onPrePhysicsTick）调用。
     * Consumer 侧均为 ConcurrentHashMap，线程安全。
     */
    private void releaseAllControl() {
        for (Map.Entry<TurretDriverSubsystem, String> entry : turrets.entrySet()) {
            entry.getKey().setTargetAngle(entry.getValue(), null);
        }
        for (Map.Entry<LauncherSubsystem, String> entry : launchers.entrySet()) {
            entry.getKey().setFireCommand(entry.getValue(), null);
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

        // 读取开火指令：频道里只有表达"开火"的信号才算开火，判据见 isFireSignal。
        // 弹种切换指令走的是同一批频道（座位的开火目标频道就是 fire_input_*），它不算开火，
        // 因此不能按"频道里有东西"来判，否则按一次切换弹种键就会打出一发。
        this.firing = false;
        fire: for (String signalKey : attr.staticAttribute.getFireInputs()) {
            SignalChannel channel = getSignalChannel(signalKey);
            for (Object signal : channel.values()) {
                if (isFireSignal(signal)) {
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

        // 调试自动开火：周期性发射，由 debugTriggerInterval / debugFireDuration 控制
        if (debugAutoFire) {
            this.firing = debugFireTickCounter < debugFireDuration;
            debugFireTickCounter++;
            if (debugFireTickCounter >= debugTriggerInterval) {
                debugFireTickCounter = 0;
            }
            if (this.targetPosition == null) {
                Vector3f subPartPos = getOwner().getSubPart().getPosition();
                this.targetPosition = new Vec3(subPartPos.x, subPartPos.y + 20, subPartPos.z - 30);
            }
            // 设置默认 ViewInputSignal，使 onPrePhysicsTick 通过 vis 非空校验并执行瞄准/开火
            if (this.currentViewSignal == null) {
                this.currentViewSignal = new ViewInputSignal(this.targetPosition, 0, 0, true, true);
            }
        }
    }

    /**
     * 信号是否表达"开火"。<br>
     * 开火频道与弹种切换指令共用线路（座位的开火目标频道就是 {@code fire_input_*}），
     * 所以判定必须看信号语义，不能只看"频道里有东西"：
     * <ul>
     *   <li>{@link RegularInputSignal}：主武器、副武器开火指令算开火；弹种切换等其余按键语义不算</li>
     *   <li>Boolean：仅 {@code true} 算开火</li>
     *   <li>数值（ControlBinding 的 HOLD/PRESS/TOGGLE 写入）：仅非 0 算开火，
     *       因此开关型绑定拨到关（0）不会残留开火</li>
     *   <li>其余（握手用的 String、{@link EmptySignal} 等）：不算开火</li>
     * </ul>
     *
     * @param signal 频道里的信号值
     * @return true 表示这条信号要求开火
     */
    private static boolean isFireSignal(Object signal) {
        if (signal instanceof RegularInputSignal regular) {
            KeyInputMapping type = regular.getInputType();
            return type == KeyInputMapping.MAIN_FIRE || type == KeyInputMapping.SECONDARY_FIRE;
        }
        if (signal instanceof Boolean firing) return firing;
        if (signal instanceof Number number) return number.doubleValue() != 0.0;
        return false;
    }

    /**
     * 从 fireInputs 频道读取标准化的 RegularInputSignal 武器控制指令。<br>
     * 在 {@link #readInputSignals()} 之后由 {@link #onTick()} 调用，与它同在主线程：
     * 两者都写 {@link #firing}，同线程才谈得上确定的覆盖顺序（标准化指令压过通道扫描）。
     * 指令由玩家按键（边沿触发，按下/松开各一包）或 AI 直发，速率远低于 tick，无需按物理步进频率轮询。
     * <p>
     * AI 实体可通过发送 RegularInputSignal 到此子系统直接控制武器，无需经过控制组绑定。
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
        // ① 快照 + 原子清理：物理线程此后遍历 turrets/launchers 为空，无法重新写入
        Map<TurretDriverSubsystem, String> oldTurrets = new HashMap<>(turrets);
        Map<LauncherSubsystem, String> oldLaunchers = new HashMap<>(launchers);
        turrets.clear();
        launchers.clear();

        // ② 从快照安全释放（物理线程无法干扰，因为 turrets/launchers 已空）
        for (Map.Entry<TurretDriverSubsystem, String> e : oldTurrets.entrySet()) {
            e.getKey().setTargetAngle(e.getValue(), null);
        }
        for (Map.Entry<LauncherSubsystem, String> e : oldLaunchers.entrySet()) {
            e.getKey().setFireCommand(e.getValue(), null);
        }

        // ③ 重新握手
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
        return new HashMap<>(attr.controlOutputTargets);
    }

    /**
     * 武器控制器的按视角布局来自动态属性，因此同一份 {@code definition} 的不同实例
     * 可以各配各的 HUD。字段缺省时取 {@link HudLayout#EMPTY}，解析结果为空。
     */
    @Override
    @Nullable
    public HudLayout hudLayoutFor(HudView view) {
        return IHudHost.resolve(switch (view) {
            case FIRST -> attr.getFirstHudLayout();
            case THIRD -> attr.getThirdHudLayout();
            case SCOPE -> attr.getScopeHudLayout();
        });
    }
}
