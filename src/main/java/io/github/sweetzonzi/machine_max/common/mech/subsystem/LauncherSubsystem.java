package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.particle.common.IParticleAnchor;
import cn.solarmoon.spark_core.sound.ISoundSpreader;
import cn.solarmoon.spark_core.api.SpreadingSoundHelper;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.projectile.IProjectile;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.LauncherSubsystemAttr;
import jme3utilities.math.MyQuaternion;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractModuleAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module.BarrelModuleAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module.CapacitorModuleAttr;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 发射器子系统。<br>
 * 代表炮闩、导弹挂架、火箭发射管等单个发射口。<br>
 * 从locator位置沿其朝向发射数据驱动的投射物，初速和精度受子系统属性与投射物类型共同影响。<br>
 * 实现 {@link IAmmoConsumer} 接口以支持弹药消耗与供给，膛内弹药状态由 chamberedType 管理。<br>
 * 投射物类型由当前供给者提供，而非静态属性直接指定。<br>
 * 弹药管理已由 {@link WeaponControllerSubsystem} 接管——Controller 负责弹种选择和路由决策，
 * Launcher 仅执行 currentSupplier 上的装填和发射。
 */
public class LauncherSubsystem extends ModularSubsystem implements IAmmoConsumer, ISoundSpreader, IParticleAnchor {

    public final LauncherSubsystemAttr attr;

    // ——— 计时器累积发射（物理线程重构） ———

    /**
     * 发射计时器累积（秒）。
     * <p>
     * 每 tick 累积 0.05s，除以单发间隔得到应发射数。
     * 停止开火时清零，防止"蓄力"。
     * <p>
     * <b>调用线程：</b>仅主线程（{@link #onTick()} 内部读写）。
     */
    private double fireAccumulator = 0.0;

    /**
     * 上一 tick 是否正在开火。
     * <p>
     * 用于检测"刚按下开火键"的首帧——首帧将 accumulator 预填充为
     * {@code intervalSec} 而非从 0 累积 0.05s，确保 RPM < 1200 的武器立即发射第一发。
     * <p>
     * <b>调用线程：</b>仅主线程（{@link #onTick()} 内部读写）。
     */
    private boolean wasFiring = false;

    /**
     * 上次成功开火的 tick 数（{@link #tickCount} 的值）。
     * <p>
     * 防止连按超射：首帧预填充仅在距离上次开火已过至少 {@code intervalSec} 时允许。
     * 连按（间隔不足）时拒绝预填充，退回正常累积。
     * 初始 -1 表示从未开火，允许首帧无条件预填充。
     * <p>
     * <b>调用线程：</b>仅主线程（{@link #onTick()} 内部读写）。
     */
    private int lastFireTick = -1;

    /**
     * 待物理线程发射的弹药类型队列。
     * <p>
     * <b>生产者：</b>主线程 onTick（弹药消费循环）。<br>
     * <b>消费者：</b>物理线程 {@link #onPrePhysicsTick()}（{@link #fireSingle(ProjectileType)}）。<br>
     * 使用 {@link ConcurrentLinkedQueue} 保证无锁安全。
     */
    private final ConcurrentLinkedQueue<ProjectileType> pendingFires = new ConcurrentLinkedQueue<>();

    // ——— 音效状态（仅客户端有效，由 onTick 管理） ———

    /** 当前活跃的连射循环音效UUID */
    @Nullable
    private UUID currentAutoFireUuid;

    /** 当前活跃的弹壳循环音效UUID */
    @Nullable
    private UUID currentShellSoundUuid;

    /** 当前连射音效档位key（用于检测RPM档位变化） */
    @Nullable
    private String currentFireRateTierKey;

    /** 当前弹壳音效档位key */
    @Nullable
    private String currentShellRateTierKey;

    /** 本轮连射已打的弹数（用于检测首发射击） */
    private int roundsFiredThisBurst = 0;

    // ——— 弹药状态 ———

    /** 膛内当前弹药类型。null = 空膛 */
    @Nullable
    @Getter
    private ProjectileType chamberedType;

    /**
     * 当前选中的供给者引用（由 WeaponController 的路由决策设置）。<br>
     * null 表示无供给者（不装填，可发射膛内已有弹药）。
     */
    @Nullable
    private IAmmoSupplier currentSupplier = null;

    /**
     * 按频道分组的供给者映射。<br>
     * key = 发现频道名（对应 LauncherStaticAttr.ammo_inputs 声明顺序）。<br>
     * LinkedHashMap 保证迭代顺序与 ammo_inputs 一致。
     */
    private final Map<String, List<IAmmoSupplier>> supplierChannels = new LinkedHashMap<>();

    // ——— 直接调用控制（替代信号轮询） ———

    /**
     * 发射控制指令：多控制器通过此 record 原子写入方向、修正角和开火指令。<br>
     * 由 {@link #setFireCommand} 写入，{@link #getActiveFireCommand} 按优先级读取。
     */
    public record FireCommand(
        @Nullable Vector3f direction,   // null = 使用 locator 朝向
        float correctionAngleDeg,       // 火控修正角（度），0 = 不修正
        boolean firing                  // 是否开火
    ) {}

    /**
     * 多控制器指令映射。<br>
     * key = controlInputs 频道名，value = 发射指令。<br>
     * 物理线程写（WC.onPrePhysicsTick），主线程读（onTick.isFiring）和物理线程读（fireSingle）。
     */
    private final ConcurrentHashMap<String, FireCommand> fireCommands = new ConcurrentHashMap<>();

    /** 当前是否正在等待装填（requestRound 已调用但弹药未就绪） */
    private boolean reloading = false;

    public LauncherSubsystem(ISubsystemHost owner, String name, LauncherSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        if (attr.locator == null || attr.locator.isEmpty()) {
            MachineMax.LOGGER.error("发射器子系统 {} 未配置发射点locator", name);
        }
    }

    // ==================== 模块化子系统 ====================

    @Override
    protected Map<String, ? extends AbstractModuleAttr> defineModules() {
        // 从显式字段构建模块映射，与 LauncherSubsystemStaticAttr 的结构保持一致
        Map<String, AbstractModuleAttr> modules = new LinkedHashMap<>();
        modules.put("barrel", attr.staticAttribute.getBarrel());
        modules.put("breech", attr.staticAttribute.getBreech());
        if (attr.staticAttribute.getCapacitor() != null) {
            modules.put("capacitor", attr.staticAttribute.getCapacitor());
        }
        return modules;
    }

    // ——— IAmmoConsumer 实现 ———

    @Override
    public boolean canAcceptAmmo() {
        return chamberedType == null || isActive();
    }

    @Override
    public int getFreeCapacity() {
        return chamberedType == null ? 1 : 0;
    }

    @Override
    public boolean receiveAmmo(ProjectileType type) {
        if (chamberedType == null && isActive()) {
            chamberedType = type;
            reloading = false;
            return true;
        }
        return false;
    }

    @Override
    @Nullable
    public IAmmoSupplier getCurrentSupplier() {
        return currentSupplier;
    }

    @Override
    public void setCurrentSupplier(@Nullable IAmmoSupplier supplier) {
        this.currentSupplier = supplier;
    }

    @Override
    public List<IAmmoSupplier> getSuppliers() {
        // 返回所有频道的扁平列表
        List<IAmmoSupplier> all = new ArrayList<>();
        for (List<IAmmoSupplier> channelList : supplierChannels.values()) {
            all.addAll(channelList);
        }
        return all;
    }

    @Override
    public Map<String, List<IAmmoSupplier>> getSupplierChannels() {
        return supplierChannels;
    }

    @Override
    public void addSupplier(IAmmoSupplier supplier) {
        addSupplier(supplier, "unknown");
    }

    /**
     * 带频道名的供给者注册。<br>
     * 由 Loader 在 handshake 回调中调用。
     * 频道名对应 LauncherStaticAttr.ammo_inputs 中声明的发现频道。
     */
    @Override
    public void addSupplier(IAmmoSupplier supplier, String channelName) {
        supplierChannels.computeIfAbsent(channelName, k -> new ArrayList<>()).add(supplier);
        if (currentSupplier == null) {
            currentSupplier = supplier;
        }
    }

    @Override
    public boolean canAccept(ProjectileType type) {
        return attr.staticAttribute.getBreech().isAmmoCompatible(type);
    }

    /**
     * 直接装填一发弹药到膛内。<br>
     * 由外部调用（如 AmmoLoader 直接压弹），绕过 requestRound 流程。
     *
     * @return true 表示装填成功
     */
    public boolean loadRound(ProjectileType type) {
        if (chamberedType != null || !isActive()) return false;
        if (!canAccept(type)) return false;
        chamberedType = type;
        reloading = false;
        return true;
    }

    /**
     * 退膛：返回膛内弹药并清空。<br>
     * 若当前供给者可接收退弹则归还，否则返回 null（后续可扩展为生成 ItemEntity）。
     *
     * @return 退出的弹药类型，空膛返回 null
     */
    @Nullable
    public ProjectileType ejectRound() {
        ProjectileType round = chamberedType;
        if (round == null) return null;
        chamberedType = null;
        reloading = false;

        // 尝试归还给当前供给者
        IAmmoSupplier supplier = getCurrentSupplier();
        if (supplier != null && supplier.canEject()) {
            supplier.returnRound(round);
        }
        // TODO: 若无法归还，生成 ItemEntity 掉落于发射器 locator 位置
        return round;
    }

    /**
     * 主线程 tick：计时器累积 + 弹药消费 + 入队 pendingFires + 客户端音效管理。
     * <p>
     * <b>调用线程：</b>主线程（20tps）。
     * <p>
     * 逻辑：
     * <ol>
     *   <li>若停止开火 → 处理停火音效，{@code fireAccumulator = 0}，防止蓄力</li>
     *   <li>刚按下开火键的首帧 → 预填充 accumulator 为 {@code intervalSec}，保证立即发射</li>
     *   <li>后续帧 → 正常 {@code fireAccumulator += 0.05s}，计算应发射数 {@code N}</li>
     *   <li>弹药消费循环（最多 N 发）：取弹 → 兼容校验 → 膛内 → 入队 {@link #pendingFires} → 装填下一发</li>
     *   <li>客户端音效处理：遍历 pendingFires，首发射击用单发音效，后续连射启动/维持循环音效</li>
     *   <li>扣除已发射数对应的时间：{@code fireAccumulator -= N × intervalSec}</li>
     * </ol>
     */
    @Override
    public void onTick() {
        super.onTick();
        // 若膛内无弹，尝试从供给者取弹
        if (chamberedType == null) tryLoadChamber();

        if (!isActive() || isDestroyed()) {
            handleCeaseFire();
            resetFireState();
            return;
        }

        if (getActiveFireCommand() == null || chamberedType == null) {
            // 停止开火 → 处理停火音效 + 清零累积，防止下次开火"蓄力"
            if (wasFiring) {
                handleCeaseFire();
            }
            resetFireState();
            return;
        }

        // ★ 记录是否为本burst首帧（在 wasFiring 被置 true 之前）
        boolean burstJustStarted = !wasFiring;
        float intervalSec = 60f / attr.staticAttribute.getBreech().fireRate();

        // ① 计时器累积：首帧预填充 intervalSec 以立即发射，后续帧正常 +0.05s
        if (!wasFiring) {
            // 刚按下开火键 → 检查距离上次开火是否足够远，防止连按超射
            int elapsed = tickCount - lastFireTick;
            float elapsedSec = elapsed * 0.05f;
            if (lastFireTick < 0 || elapsedSec >= intervalSec) {
                // 从未开火或距离上次开火已超过间隔 → 允许预填充
                fireAccumulator = intervalSec;
            } else {
                // 连按（间隔不足 intervalSec）→ 正常累积，不预填充
                fireAccumulator = 0.05;
            }
        } else {
            fireAccumulator += 0.05; // 1 tick = 0.05s
        }
        wasFiring = true;
        int rounds = (int) (fireAccumulator / intervalSec);

        if (rounds < 1) return;

        // ② 弹药消费循环
        int firedCount = 0;
        // 本地快照：缓存本 tick 发射的弹药类型，供客户端特效处理使用。
        // 不直接遍历 pendingFires（物理线程可能同时 poll 消费），避免并发遍历问题。
        List<ProjectileType> firedTypes = new ArrayList<>(rounds);
        for (int i = 0; i < rounds; i++) {
            // 若膛内无弹，尝试从供给者取弹
            if (chamberedType == null) {
                if (!tryLoadChamber()) {
                    break; // 弹药未就绪，等待下一 tick
                }
            }

            // ③ 入队 pendingFires（物理线程将消费）
            pendingFires.add(chamberedType);
            firedTypes.add(chamberedType);
            firedCount++;
            chamberedType = null;

            // ④ 预请求下一发弹药：射击后立即向供给者发起请求，
            //    使输送计时器与射击并发运行，避免单膛室阻塞射速。
            if (!tryRequestNextRound()) {
                break; // 供给者无法接受请求（无弹药且无再生能力）
            }
        }

        // ⑤ 客户端音效特效处理（主线程，使用本地快照避免与物理线程并发访问 pendingFires）
        if (getLevel().isClientSide() && firedCount > 0) {
            handleFiringSounds(firedTypes, burstJustStarted);
            for (ProjectileType type : firedTypes) {
                type.playFireEffect(getLevel(), this, attr.locator);
            }
        }

        // ⑥ 扣除已发射数对应的时间
        fireAccumulator -= firedCount * intervalSec;
        if (fireAccumulator < 0) fireAccumulator = 0;
        // 记录上次开火 tick（用于防连按超射）
        if (firedCount > 0) lastFireTick = tickCount;
    }

    /**
     * 重置发射相关状态。
     */
    private void resetFireState() {
        fireAccumulator = 0.0;
        wasFiring = false;
    }

    /**
     * 尝试从供给者装填一发弹药到膛室。<br>
     * 已移除 handleIncompatibleAmmo 逻辑——Controller 负责路由决策，
     * 若收到不兼容弹药则防御性归还并等待 Controller 下 tick 重路由。
     *
     * @return true 表示膛室已有弹药（可立即发射）
     */
    private boolean tryLoadChamber() {
        IAmmoSupplier supplier = getCurrentSupplier();
        if (supplier == null) return false;

        if (supplier.isRoundReady(this)) {
            ProjectileType offered = supplier.consumeReadyRound(this);
            if (offered != null && canAccept(offered)) {
                chamberedType = offered;
                reloading = false;
                return true;
            } else {
                // 防御性处理：Controller 不应选不兼容的 Loader
                if (offered != null && supplier.canEject()) {
                    MachineMax.LOGGER.warn("Launcher {} 收到不兼容弹药 {}，归还并等待 Controller 重路由", name, offered);
                    supplier.returnRound(offered);
                }
                reloading = false;
                return false;
            }
        }

        // 弹药未就绪 → 若未在装填中则发起请求
        if (!reloading) {
            // ★ 仅当 requestRound 成功接受时才设 reloading=true，
            //    防止供给者无弹药时陷入死锁（reloading 永远不会变为 false）
            boolean accepted = supplier.requestRound(this);
            if (accepted) {
                reloading = true;
            }
            // 无论是否成功，等待下一 tick 重试
        }
        return false;
    }

    /**
     * 预请求下一发弹药。<br>
     * 在每发射击后立即调用，启动供给者的输送计时器，使弹药输送与射击并发进行。<br>
     * 对于 {@code reloadTimeTicks > 0} 的供给者，这能显著减少有效射击间隔。
     *
     * @return true 表示预请求成功（或已在装填中），false 表示供给者拒绝请求
     */
    private boolean tryRequestNextRound() {
        if (reloading) return true; // 已在装填中，供给者的请求幂等

        IAmmoSupplier supplier = getCurrentSupplier();
        if (supplier == null) return false;

        boolean accepted = supplier.requestRound(this);
        if (accepted) {
            reloading = true;
        }
        // 即使请求被拒绝（供给者无弹药），也不阻塞：下个 tick 会重试
        // 若供给者为 RegenLoader，弹药再生产后 requestRound 会重新成功
        return accepted;
    }

    /**
     * 物理线程 pre-tick：从 {@link #pendingFires} 逐发出队并执行发射。
     * <p>
     * <b>调用线程：</b>物理线程（Bullet 物理步进前）。
     * <p>
     * 每发调用 {@link #fireSingle(ProjectileType)}，在该方法内完成：
     * <ul>
     *   <li>读取刚体实时枪口位姿（物理线程，与物理体同步无延迟）</li>
     *   <li>散布 + 初速计算</li>
     *   <li>投射物 SoA 写入 + 刚体注册</li>
     *   <li>后坐力 applyImpulse（直接操作物理体）</li>
     * </ul>
     */
    @Override
    public void onPrePhysicsTick() {
        super.onPrePhysicsTick();
        if (getOwner() == null) return;

        ProjectileType type;
        while ((type = pendingFires.poll()) != null) {
            fireSingle(type);
        }
    }

    /**
     * 物理线程单发发射。
     * <p>
     * <b>调用线程：</b>物理线程（由 {@link #onPrePhysicsTick()} 调用）。
     * <p>
     * 逻辑：
     * <ol>
     *   <li>读取刚体实时 {@link #getMuzzleWorldTransform()} 获取枪口位姿（与物理体同步）</li>
     *   <li>散布 + 最终初速计算</li>
     *   <li>继承发射平台速度</li>
     *   <li>调用 {@link ProjectileType#create} 创建投射物（内部进入 SoA 写入）</li>
     *   <li>后坐力 {@code applyImpulse} 直接作用到发射平台刚体</li>
     * </ol>
     */
    private void fireSingle(ProjectileType type) {
        // ① 枪口位姿
        Transform muzzleTransform = getMuzzleWorldTransform();
        Vector3f jmePos = muzzleTransform.getTranslation();
        Quaternion jmeRot = muzzleTransform.getRotation();

        // ② 计算发射方向：按优先级使用火控指令中的期望方向和修正角
        Vector3f locatorForward = MyQuaternion.rotate(jmeRot, new Vector3f(0, 0, -1), null).normalize();
        Vector3f direction;
        FireCommand cmd = getActiveFireCommand();
        if (cmd != null && cmd.direction() != null && cmd.correctionAngleDeg() > 0.001f) {
            direction = clampToCone(locatorForward, cmd.direction(), cmd.correctionAngleDeg());
        } else {
            direction = locatorForward;
        }

        // ③ 客户端：不创建投射物，音效已在 onTick() 中处理
        if (getLevel().isClientSide()) {
            return;
        }

        // ④ 服务端：开火——ProjectileType 内部处理 bullet_num、散布、速度
        // 计算炮管模块对散布和初速的衰减乘数
        float spreadMult = 1.0f;
        float velMult = attr.staticAttribute.getBarrel().velocityMultiplier();
        SubModule barrelModule = getModule("barrel");
        if (barrelModule != null) {
            BarrelModuleAttr barrelAttr = (BarrelModuleAttr) barrelModule.getAttr();
            spreadMult = barrelAttr.getSpreadMultiplier(barrelModule.getDurabilityRatio());
            velMult *= barrelAttr.getMuzzleVelocityMultiplier(barrelModule.getDurabilityRatio());
        }
        List<IProjectile> projectiles = type.fire(
            getLevel(), jmePos, direction,
            velMult,
            attr.staticAttribute.getBarrel().velocityBonus(),
            attr.staticAttribute.getBarrel().horizontalAccuracyMultiplier() * spreadMult,
            attr.staticAttribute.getBarrel().verticalAccuracyMultiplier() * spreadMult,
            getSubPart().getLinearVelocity()
        );

        // ⑤ 后坐力——总弹丸质量 × 速度
        float finalSpeed = type.getBaseVelocity() * attr.staticAttribute.getBarrel().velocityMultiplier()
                         + attr.staticAttribute.getBarrel().velocityBonus();
        float totalMass = type.getMass() * projectiles.size();
        float absorption = attr.staticAttribute.getBreech().recoilAbsorption();
        float recoilImpulse = totalMass * finalSpeed * (1.0f - absorption);
        if (recoilImpulse > 1e-6f) {
            Vector3f impulseWorld = direction.mult(-recoilImpulse);
            var body = getSubPart().getBody();
            Vector3f bodyWorldPos = body.getPhysicsLocation(new Vector3f());
            body.applyImpulse(impulseWorld, jmePos.subtract(bodyWorldPos));
        }
    }

    // ——— 直接调用控制 API（由 WeaponController 调用） ———

    /**
     * 由 WeaponController 直接调用，原子设置发射指令。<br>
     * 替代旧的 setFiring + setFireDirection 双调，消除竞态。<br>
     * <b>调用线程：</b>物理线程。
     *
     * @param channel 控制频道名（对应 controlInputs 中的条目）
     * @param cmd     发射指令，null 或 firing=false = 释放控制权
     */
    public void setFireCommand(String channel, @Nullable FireCommand cmd) {
        if (cmd == null || !cmd.firing()) fireCommands.remove(channel);
        else fireCommands.put(channel, cmd);
    }

    /**
     * 按 controlInputs 频道优先级获取最高优先级的有效发射指令。
     *
     * @return 最高优先级的有效 FireCommand，null = 无控制器持有开火权
     */
    @Nullable
    private FireCommand getActiveFireCommand() {
        for (String channel : attr.staticAttribute.getControlInputs()) {
            FireCommand cmd = fireCommands.get(channel);
            if (cmd != null && cmd.firing()) return cmd;
        }
        return null;
    }

    /**
     * 将期望方向限制在以 locator 朝向为轴的锥角内。<br>
     * 若期望方向与 locator 朝向的夹角 ≤ maxAngleDeg，则直接使用期望方向；<br>
     * 否则将 locator 方向向期望方向旋转 maxAngleDeg 度。<br>
     * <b>调用线程：</b>物理线程（fireSingle 内部调用）。
     *
     * @param forward      locator 前方单位向量（锥轴方向）
     * @param preferred    期望方向（单位向量）
     * @param maxAngleDeg  最大允许偏离角度（度）
     * @return 修正后的发射方向（单位向量）
     */
    private static Vector3f clampToCone(Vector3f forward, Vector3f preferred, float maxAngleDeg) {
        // 手算夹角：Vector3f 无 angleBetween 方法
        float dot = forward.dot(preferred);
        float angle = (float) Math.acos(Math.clamp(dot, -1.0f, 1.0f));
        if (angle <= (float) Math.toRadians(maxAngleDeg)) return preferred;
        // 将 forward 向 preferred 方向旋转 maxAngleDeg
        // 使用 cross(..., null) 创建新实例，避免修改 forward
        // normalize() 返回新实例（Vector3f 无 normalizeLocal 实例方法）
        Vector3f axis = forward.cross(preferred, null).normalize();
        // fromAngleNormalAxis: Quaternion 的正确方法名
        Quaternion rot = new Quaternion().fromAngleNormalAxis((float) Math.toRadians(maxAngleDeg), axis);
        return MyQuaternion.rotate(rot, forward, null);
    }

    // ——— 音效管理（仅在客户端 onTick 中调用） ———

    /**
     * 处理停火：淡出循环音效，播放停火尾音，重置计数。
     * <p>
     * <b>调用线程：</b>主线程（{@link #onTick()}）。
     * </p>
     */
    private void handleCeaseFire() {
        if (!getLevel().isClientSide()) return;

        // 淡出连射循环音效
        if (currentAutoFireUuid != null) {
            SpreadingSoundHelper.fadeSound(getLevel(), currentAutoFireUuid);
            currentAutoFireUuid = null;
            currentFireRateTierKey = null;
        }

        // 淡出弹壳循环音效
        if (currentShellSoundUuid != null) {
            SpreadingSoundHelper.fadeSound(getLevel(), currentShellSoundUuid);
            currentShellSoundUuid = null;
            currentShellRateTierKey = null;
        }

        // 播放停火尾音
        ProjectileType ammo = getCurrentAmmoType();
        if (ammo != null) {
            SoundEvent ceaseSound = ammo.getCeaseFireSound();
            if (ceaseSound != null && ceaseSound != ProjectileType.ProjectileSoundAttr.NO_SOUND) {
                playSpreadingSound(getLevel(), ceaseSound, SoundSource.NEUTRAL);
            }

            // 停火时播放最后一枚弹壳音效（单发）
            SoundEvent shellSound = ammo.getShellSounds().get("0.0");
            if (shellSound != null) {
                playSpreadingSound(getLevel(), shellSound, SoundSource.NEUTRAL);
            }
        }

        roundsFiredThisBurst = 0;
    }

    /**
     * 处理开火音效：遍历本 tick 发射的弹药类型，按首发/连发分别播放音效。
     * <p>
     * 使用调用方传入的本地快照列表 {@code firedTypes}，而非直接遍历
     * {@link #pendingFires}，避免与物理线程的并发 poll 冲突。
     * </p>
     * <p>
     * <b>调用线程：</b>主线程（{@link #onTick()}）。
     * </p>
     *
     * @param firedTypes      本 tick 发射的弹药类型快照
     * @param burstJustStarted 是否为本 burst 首帧
     */
    private void handleFiringSounds(List<ProjectileType> firedTypes, boolean burstJustStarted) {
        for (ProjectileType type : firedTypes) {
            if (type == null) continue;

            roundsFiredThisBurst++;
            boolean isFirstRound = burstJustStarted && roundsFiredThisBurst == 1;

            tickFireSound(type, isFirstRound);
            tickShellSound(type, isFirstRound);
        }
    }

    /**
     * 处理单发/连发开火音效。
     * <p>
     * 首发射击（或未配置循环音效）→ 逐发播放 {@code "0.0"} 键的单发音效（不循环）。<br>
     * 后续连射 → 启动/维持循环音效，通过 {@link ISoundSpreader} 追踪枪口，RPM 跨档位时交叉淡入。
     * </p>
     *
     * @param type         弹药类型
     * @param isFirstRound 是否为首发
     */
    private void tickFireSound(ProjectileType type, boolean isFirstRound) {
        Map<String, SoundEvent> fireSounds = type.getFireSounds();
        if (fireSounds.isEmpty()) return;

        // 检查是否有非0.0的循环音效key
        boolean hasAutoKeys = fireSounds.keySet().stream().anyMatch(k -> !"0.0".equals(k));

        if (isFirstRound || !hasAutoKeys) {
            // ★ 首发射击 或 无循环音效配置 → 逐发播放单发音效
            SoundEvent singleSound = fireSounds.get("0.0");
            if (singleSound != null) {
                this.playSpreadingSound(getLevel(), singleSound, SoundSource.NEUTRAL);
            }
        } else {
            // ★ 连射 → 循环音效（通过 ISoundSpreader 追踪枪口位置/速度）
            String tierKey = findBestFireSoundKey(fireSounds);
            SoundEvent autoSound = fireSounds.get(tierKey);
            if (autoSound == null) return;

            if (currentAutoFireUuid == null) {
                // 首次连射：创建循环实例
                currentAutoFireUuid = playSpreadingSound(getLevel(), autoSound, SoundSource.NEUTRAL, 0, 0, true);
                currentFireRateTierKey = tierKey;
            } else if (!tierKey.equals(currentFireRateTierKey)) {
                // RPM 跨档位：交叉淡入新音效
                currentAutoFireUuid = transitionSound(getLevel(), currentAutoFireUuid, autoSound,
                    SoundSource.NEUTRAL, 0, 0, true);
                currentFireRateTierKey = tierKey;
            }
        }
    }

    /**
     * 处理弹壳音效，逻辑与开火音效相同。
     */
    private void tickShellSound(ProjectileType type, boolean isFirstRound) {
        Map<String, SoundEvent> shellSounds = type.getShellSounds();
        if (shellSounds.isEmpty()) return;

        boolean hasAutoKeys = shellSounds.keySet().stream().anyMatch(k -> !"0.0".equals(k));

        if (isFirstRound || !hasAutoKeys) {
            SoundEvent singleSound = shellSounds.get("0.0");
            if (singleSound != null) {
                playSpreadingSound(getLevel(), singleSound, SoundSource.NEUTRAL);
            }
        } else {
            String tierKey = findBestFireSoundKey(shellSounds);
            SoundEvent autoSound = shellSounds.get(tierKey);
            if (autoSound == null) return;

            if (currentShellSoundUuid == null) {
                currentShellSoundUuid = playSpreadingSound(getLevel(), autoSound, SoundSource.NEUTRAL, 0, 5, true);
                currentShellRateTierKey = tierKey;
            } else if (!tierKey.equals(currentShellRateTierKey)) {
                currentShellSoundUuid = transitionSound(getLevel(), currentShellSoundUuid, autoSound,
                    SoundSource.NEUTRAL, 0, 5, true);
                currentShellRateTierKey = tierKey;
            }
        }
    }

    /**
     * 根据当前发射器射速，查找最匹配的音效档位key。
     * <p>
     * 排除 {@code "0.0"} 键，在剩余key中找数值 ≤ 当前RPM 的最大者。
     * 若无匹配则回退到 {@code "0.0"}。
     * </p>
     *
     * @param sounds 音效映射（key=RPM字符串）
     * @return 最佳匹配的档位key
     */
    private String findBestFireSoundKey(Map<String, SoundEvent> sounds) {
        float rpm = attr.staticAttribute.getBreech().fireRate();
        return sounds.keySet().stream()
            .filter(k -> !"0.0".equals(k))
            .map(k -> new AbstractMap.SimpleEntry<>(k, parseRpm(k)))
            .filter(e -> !Float.isNaN(e.getValue()) && e.getValue() <= rpm)
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("0.0");
    }

    /** 安全解析RPM字符串 */
    private static float parseRpm(String key) {
        try {
            return Float.parseFloat(key);
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    // ——— ISoundSpreader 实现 ———

    @Override
    @NotNull
    public Vec3 getPosition(UUID uuid, SoundEvent event) {
        return getMuzzleWorldPosition();
    }

    @Override
    @NotNull
    public Vec3 getSpeed(UUID uuid, SoundEvent event) {
        return SparkMathKt.toVec3(getSubPart().getLinearVelocity());
    }

    @Override
    public float getPitch(UUID uuid, SoundEvent event) {
        // 根据当前RPM与档位设计RPM的比值调制音高
        String tierKey = null;
        if (uuid.equals(currentAutoFireUuid)) {
            tierKey = currentFireRateTierKey;
        } else if (uuid.equals(currentShellSoundUuid)) {
            tierKey = currentShellRateTierKey;
        }
        if (tierKey != null) {
            float designRPM = parseRpm(tierKey);
            if (!Float.isNaN(designRPM) && designRPM > 0) {
                float actualRPM = attr.staticAttribute.getBreech().fireRate();
                return Math.clamp(actualRPM / designRPM, 0.5f, 2.0f);
            }
        }
        return 1.0f;
    }

    @Override
    public boolean shouldApplyInteriorEffect(@Nullable Entity listener, SoundEvent event, boolean isFirstPerson) {
        if (listener == null || !isFirstPerson) return false;
        return ((IEntityMixin) listener).machine_Max$getControllingSubsystem() != null;
    }

    /**
     * 获取当前弹药类型（按优先级）。<br>
     * 优先返回膛内已装填的弹药类型；若空膛则尝试从当前选中的供给者获取正在装填的弹药类型。
     *
     * @return 当前弹药类型，完全无弹药信息时返回 null
     */
    @Nullable
    public ProjectileType getCurrentAmmoType() {
        if (chamberedType != null) return chamberedType;
        IAmmoSupplier supplier = getCurrentSupplier();
        return supplier != null ? supplier.getSuppliedType() : null;
    }

    /**
     * 获取发射点在世界空间中的位姿。
     */
    public Transform getMuzzleWorldTransform() {
        return getOwner().getSubPart().getLocatorWorldTransform(attr.locator);
    }

    // ——— IParticleAnchor 实现 ———

    @Override
    @Nullable
    public Transform getLocatorTransform(@NotNull UUID instanceId, @NotNull String locatorName) {
        // 仅响应自己的 locator
        if (!locatorName.equals(attr.locator)) return null;
        return getMuzzleWorldTransform();
    }

    @Override
    @Nullable
    public Vec3 getAnchorVelocity(@NotNull UUID instanceId, @NotNull String locatorName) {
        return SparkMathKt.toVec3(getSubPart().getLinearVelocity());
    }

    /**
     * 获取发射点在世界空间中的位置。
     */
    public Vec3 getMuzzleWorldPosition() {
        Vector3f pos = getMuzzleWorldTransform().getTranslation();
        return new Vec3(pos.x, pos.y, pos.z);
    }

    /**
     * 获取发射方向在世界空间中的单位向量（JME前方为-Z方向）。
     */
    public Vec3 getMuzzleDirection() {
        Transform tf = getMuzzleWorldTransform();
        Quaternion rot = tf.getRotation();
        Vector3f forward = MyQuaternion.rotate(rot, new Vector3f(0, 0, -1), null);
        return new Vec3(forward.x, forward.y, forward.z).normalize();
    }

    /**
     * 获取当前有效的发射方向（包含火控修正后的实际弹道方向）。<br>
     * 若有活跃 FireCommand 指定了期望方向和修正角，则返回 clampToCone 修正后的方向；<br>
     * 否则返回原始 locator 朝向。<br>
     * <b>用途：</b>HUD 准星绘制，体现火控修正量对实际弹道的影响。<br>
     * <b>调用线程：</b>渲染线程。
     *
     * @return 世界空间单位方向向量
     */
    public Vec3 getEffectiveFireDirection() {
        Transform tf = getMuzzleWorldTransform();
        Quaternion rot = tf.getRotation();
        Vector3f locatorForward = MyQuaternion.rotate(rot, new Vector3f(0, 0, -1), null).normalize();

        FireCommand cmd = getActiveFireCommand();
        if (cmd != null && cmd.direction() != null && cmd.correctionAngleDeg() > 0.001f) {
            Vector3f corrected = clampToCone(locatorForward, cmd.direction(), cmd.correctionAngleDeg());
            return new Vec3(corrected.x, corrected.y, corrected.z);
        }
        return new Vec3(locatorForward.x, locatorForward.y, locatorForward.z);
    }

    /**
     * 判断发射器当前是否已对准目标（使用修正后的有效发射方向）。<br>
     * 内部调用 {@link #getEffectiveFireDirection()}，自动体现火控修正量。
     *
     * @param target       目标世界坐标
     * @param toleranceDeg 容差角度（度）
     * @return true 表示发射器已对准目标
     */
    public boolean isAimedAt(Vec3 target, float toleranceDeg) {
        Vec3 muzzlePos = getMuzzleWorldPosition();
        Vec3 toTarget = target.subtract(muzzlePos).normalize();
        Vec3 fireDir = getEffectiveFireDirection();
        double dot = toTarget.dot(fireDir);
        double angleRad = Math.acos(Math.clamp(dot, -1.0, 1.0));
        return Math.toDegrees(angleRad) <= toleranceDeg;
    }

    // ==================== 持久化 ====================

    @Override
    public void loadData(CompoundTag data) {
        super.loadData(data);
        if (data.contains("chambered_type")) {
            ResourceLocation key = ResourceLocation.parse(data.getString("chambered_type"));
            chamberedType = ProjectileType.get(getLevel(), key);
        } else {
            chamberedType = null;
        }
    }

    @Override
    public CompoundTag saveData(CompoundTag data) {
        super.saveData(data);
        if (chamberedType != null) {
            data.putString("chambered_type", chamberedType.getRegistryKey().toString());
        }
        return data;
    }

    /**
     * 在水平/垂直方向分别应用椭圆锥散布。
     * <p>
     * <b>调用线程：</b>物理线程（{@link #fireSingle} 调用）。
     * 使用 {@link java.util.concurrent.ThreadLocalRandom} 避免访问 Minecraft 主线程
     * {@link net.minecraft.world.level.Level#random} 导致的线程检测异常。
     */
    @Override
    public List<String> getAcceptedChannels() {
        List<String> channels = new ArrayList<>(attr.staticAttribute.getControlInputs());
        channels.addAll(attr.staticAttribute.getAmmoInputs());
        return channels;
    }

    @Override
    public boolean acceptAllBroadcastInput() {
        return acceptAllRoutingInput();
    }

    @Override
    public boolean acceptAllRoutingInput() {
        return attr.staticAttribute.getControlInputs().isEmpty() || attr.staticAttribute.getAmmoInputs().isEmpty();
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(2);
        result.putAll(attr.ammoCountOutputs);
        return result;
    }
}
