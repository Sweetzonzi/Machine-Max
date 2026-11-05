package io.github.sweetzonzi.machine_max.common.mech.projectile.type;

import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageExtensions;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.BallisticConfig;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.ThrustProfile;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.explosion.CookoffContribution;
import io.github.sweetzonzi.machine_max.common.mech.projectile.BallisticProjectile;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.projectile.component.effect.WorldEffect;
import io.github.sweetzonzi.machine_max.common.mech.projectile.component.guidance.GuidanceLaw;
import io.github.sweetzonzi.machine_max.common.mech.trajectory.TrajectoryStep;
import io.github.sweetzonzi.machine_max.common.registry.MMDamageTypes;
import lombok.AccessLevel;
import lombok.Getter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 飞行弹丸类型（JSON {@code "type": "ballistic"}）。
 * <p>
 * 在 {@link ProjectileType} 共享字段之外，承载运动模型专属字段：
 * {@link ExternalProperties 外弹道}、{@link TerminalProperties 终点效应}，
 * 以及 {@link #getWarheads() 战斗部效果列表}。
 * <p>
 * 由 {@link ProjectileType#CODEC} 按 JSON 的 {@code "type"} 字段分派到此类的 {@link #CODEC}。
 * <p>
 * 速度-伤害模型见 {@link BallisticProjectile} 中的幂函数实现。
 * 字段语义参见设计文档《武器系统-组件化投射物与类型体系设计》§三。
 */
@Getter
public class BallisticProjectileType extends ProjectileType {

    // ==================== 运动模型专属字段 ====================

    /** 外弹道属性 — 决定投射物如何飞行 */
    private final ExternalProperties external;

    /** 终点效应属性 — 决定投射物命中后发生什么 */
    private final TerminalProperties terminal;

    /**
     * 制导律 — 自含算法参数与限幅配置；{@code null} 表示无制导（纯弹道）。
     * <p>
     * JSON 中为可选对象，按 {@code "type"} 字段分派到具体律（形状与 {@code warheads} 一致）。
     * 缺省为 {@code null}，未配置 {@code guidance} 的内容包 JSON 保持纯弹道。
     * <p>
     * 指令加速度并入 SoA 积分，过载限幅与诱导阻力走
     * {@link GuidanceLaw#computeAcceleration} 路径。
     */
    @Nullable
    private final GuidanceLaw guidance;

    /**
     * 战斗部效果列表 — 命中判定完成后依次执行的世界效果。
     * <p>
     * 空列表表示纯动能弹（仅执行直接动能命中）。首期唯一实现是参数化爆炸
     * （{@code machine_max:blast}，见 {@code ExplosionWorldEffect}）。
     */
    private final List<WorldEffect> warheads;

    /**
     * 受击属性 — 本弹种作为<b>目标</b>被命中时的护甲与结构血量；{@code null} 表示不可被击毁。
     * <p>
     * JSON 中为可选对象：整块缺省时投射物不持有运动学触发体，
     * {@code getArmorLevel} 维持 {@link io.github.sweetzonzi.ballistics_framework.api.ArmorLevel#UNARMORED_1}、
     * {@code hurt} 维持返回 false，内容包可以按需选择弹种开启拦截。
     */
    @Nullable
    private final VulnerabilityProperties vulnerability;

    /**
     * 命中检测原语 — 本弹种作为<b>攻击者</b>时用哪种查询发现目标。
     * <p>
     * JSON 中为可选字段 {@code hit_detection}，缺省 {@link HitDetection#RAY}。
     * 与 {@link #vulnerability} 正交：前者决定"自己如何探测别人"，后者决定"别人能否打掉自己"。
     */
    private final HitDetection hitDetection;

    /** 静态扩展数据缓存（口径、质量等不变信息），惰性初始化，命中时 copy 后追加动态值 */
    private volatile BFDamageExtensions baseExtensions;

    /** 弹道参数缓存，见 {@link #getBallisticConfig()}；惰性初始化后不再变化 */
    @Getter(AccessLevel.NONE)
    private BallisticConfig ballisticConfig;

    /** 推力剖面缓存，见 {@link #getThrustProfile()}；惰性初始化后不再变化 */
    @Getter(AccessLevel.NONE)
    private ThrustProfile thrustProfile;

    // ==================== CODEC（12 字段，远低于 16 上限） ====================

    /**
     * Mojang MapCodec：将 JSON 反序列化为 {@link BallisticProjectileType}。
     */
    public static final MapCodec<BallisticProjectileType> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        ResourceLocation.CODEC.listOf().optionalFieldOf("tags", List.of())
            .forGetter(ProjectileType::getTags),
        Codec.FLOAT.optionalFieldOf("max_lifetime", 10.0f)
            .forGetter(ProjectileType::getMaxLifetime),
        Codec.INT.optionalFieldOf("bullet_num", 1)
            .forGetter(ProjectileType::getBulletNum),
        ExternalProperties.CODEC.fieldOf("external")
            .forGetter(BallisticProjectileType::getExternal),
        TerminalProperties.CODEC.fieldOf("terminal")
            .forGetter(BallisticProjectileType::getTerminal),
        // 发射药殉爆贡献：整块可选，缺省零贡献；不从 terminal / warheads 继承任何字段
        CookoffContribution.CODEC.optionalFieldOf("cookoff", CookoffContribution.ZERO)
            .forGetter(ProjectileType::getCookoff),
        // 可选受击配置。Optional 必须留在 codec 组内、由 apply 回调解包——
        // 若用 xmap 把它映射成 null，null 会穿过 DFU 的 DataResult 触发 NPE（见 TurretDriverSubsystemStaticAttr 同款写法）
        VulnerabilityProperties.CODEC.optionalFieldOf("vulnerability")
            .forGetter(attr -> Optional.ofNullable(attr.vulnerability)),
        HitDetection.CODEC.optionalFieldOf("hit_detection", HitDetection.RAY)
            .forGetter(BallisticProjectileType::getHitDetection),
        // 可选制导配置（形状同 warheads：type 字段直接分派到律的 codec）
        GuidanceLaw.CODEC.optionalFieldOf("guidance")
            .forGetter(attr -> Optional.ofNullable(attr.guidance)),
        WorldEffect.CODEC.listOf().optionalFieldOf("warheads", List.of())
            .forGetter(BallisticProjectileType::getWarheads),
        VisualProperties.CODEC.optionalFieldOf("visual", VisualProperties.DEFAULT)
            .forGetter(ProjectileType::getVisual),
        ProjectileSoundAttr.CODEC.optionalFieldOf("sounds", ProjectileSoundAttr.DEFAULT)
            .forGetter(ProjectileType::getSounds)
    ).apply(instance, (tags, maxLifetime, bulletNum, external, terminal, cookoff, vulnerability, hitDetection, guidance, warheads, visual, sounds) ->
        new BallisticProjectileType(tags, maxLifetime, bulletNum, external, terminal, cookoff,
            vulnerability.orElse(null), hitDetection, guidance.orElse(null), warheads, visual, sounds)));

    // ==================== 构造函数 ====================

    public BallisticProjectileType(
        List<ResourceLocation> tags,
        float maxLifetime,
        int bulletNum,
        ExternalProperties external,
        TerminalProperties terminal,
        CookoffContribution cookoff,
        @Nullable VulnerabilityProperties vulnerability,
        HitDetection hitDetection,
        @Nullable GuidanceLaw guidance,
        List<WorldEffect> warheads,
        VisualProperties visual,
        ProjectileSoundAttr sounds
    ) {
        super(tags, maxLifetime, bulletNum, visual, sounds, cookoff);
        this.external = external;
        this.terminal = terminal;
        this.vulnerability = vulnerability;
        this.hitDetection = hitDetection;
        this.guidance = guidance;
        this.warheads = warheads;
    }

    @Override
    public String getSerializedName() {
        return "ballistic";
    }

    /**
     * 注册键落地时把段表折算成推力剖面。
     * <p>
     * 首次使用时才惰性折算也能跑，但<b>段窗口重叠</b>属于内容包错误，应当在加载期就点名弹种，
     * 而不是等到第一次开火。折算失败时 {@link #buildThrustProfile} 记一条 error 并退化为
     * {@link ThrustProfile#NONE}，注册本身不受影响。
     */
    @Override
    public void setRegistryKey(ResourceLocation registryKey) {
        super.setRegistryKey(registryKey);
        if (thrustProfile == null) {
            thrustProfile = buildThrustProfile(registryKey);
        }
    }

    /**
     * 本弹种是否受制导。
     *
     * @return true 表示配置了 guidance 组件
     */
    public boolean hasGuidance() {
        return guidance != null;
    }

    /**
     * 本弹种是否用凸体扫掠而非质心射线做命中查询。
     *
     * @return true 表示 {@code hit_detection} 为 {@link HitDetection#SWEEP}
     */
    public boolean usesSweep() {
        return hitDetection == HitDetection.SWEEP;
    }

    // ==================== 外弹道委托（→ ExternalProperties） ====================

    public float getMass() { return external.mass(); }
    public float getGravityFactor() { return external.gravityFactor(); }
    public float getDragFactor() { return external.dragFactor(); }
    /** 口径（mm） */
    public float getCaliber() { return external.caliberMm(); }
    /** 碰撞半径（m），由口径换算 */
    public float getRadius() { return external.caliberMm() / 2000f; }
    public float getBaseVelocity() { return external.baseVelocity(); }
    public float getBaseAccuracyMil() { return external.baseAccuracyMil(); }
    /**
     * 推进段表（缺省空表，即无推力）。
     * <p>
     * 作者侧形态：每段自带 {@code start} 与 {@code duration}，段间允许空隙。积分侧消费的是
     * {@link #getThrustProfile()} 折算出来的剖面，两者逐段一一对应。
     */
    public List<ExternalProperties.ThrustSegment> getThrustSegments() { return external.thrust(); }

    /**
     * 是否配置了会被实际施加的推力。
     * <p>
     * 判据取折算后的剖面，与 {@link #getThrustProfile()} 同源。只按「段表非空」判定会让
     * 全段无效（{@code force}/{@code duration} 非正）的配置出现「有推力」却积分不到的情形。
     */
    public boolean hasThrust() {
        return getThrustProfile().isActive();
    }

    /**
     * 此弹种的弹道解算参数，供 {@link TrajectoryStep#step} 使用。
     * <p>
     * 弹种字段在装配后不变，因此按弹种缓存一次，避免每发、每物理步重建记录。
     * 惰性写竞争是良性的：两个线程各自构造出等值记录，读到的引用指向完整构造的对象
     * （record 的字段全是 final，最终字段语义保证其可见性）。
     * <p>
     * {@code timeStep} 写死为 1 个游戏 tick（0.05 s），与物理线程的实际步长无关——
     * {@code TrajectoryStep#step} 的步长由显式 {@code dt} 给出。这个字段只对
     * {@code RealisticTrajectory#forwardSolve} 有意义；{@code maxSteps} 同理，
     * 取弹种寿命的游戏 tick 数，与 0.05 s 的步长配合才等于实际飞行时长。
     *
     * @return 本弹种的弹道参数（同一实例）
     */
    public BallisticConfig getBallisticConfig() {
        BallisticConfig cfg = ballisticConfig;
        if (cfg == null) {
            cfg = new BallisticConfig(
                    getDragFactor(),                                          // Cd
                    getMass(),                                                // 质量 kg
                    (float) (Math.PI * getRadius() * getRadius()),             // A = π·r² (m²)
                    getGravityFactor() * 9.81f,                               // 重力加速度 (m/s²)
                    1f / 20f,                                                 // 1 个游戏 tick
                    getMaxLifetimeTicks());
            ballisticConfig = cfg;
        }
        return cfg;
    }

    /**
     * 此弹种的推力剖面，时间窗用公制秒表达、自发射瞬间起算。
     * <p>
     * 段表逐项折算为 {@link ThrustProfile.Segment}（力、点火延迟、时长、段质量），整表一次构造，
     * 因此多段点火、段间空隙与燃烧期质量流失都由剖面本身表达；无段时返回
     * {@link ThrustProfile#NONE}。
     *
     * @return 本弹种的推力剖面（同一实例）
     */
    public ThrustProfile getThrustProfile() {
        ThrustProfile profile = thrustProfile;
        if (profile == null) {
            profile = buildThrustProfile(null);
            thrustProfile = profile;
        }
        return profile;
    }

    /**
     * 把内容包段表折算成弹道库的剖面，并把折算失败收成一条日志。
     * <p>
     * 映射本身是 {@link ExternalProperties#toThrustProfile()} 的一行委托；这里只负责把
     * {@link IllegalArgumentException}（段窗口重叠）转成点名弹种的 error 并退化为无推力——
     * 它属于内容包错误，不该让服务端在开火瞬间抛异常。
     *
     * @param id 告警用的注册键，可为 {@code null}（注册前调用）
     */
    private ThrustProfile buildThrustProfile(@Nullable ResourceLocation id) {
        try {
            return external.toThrustProfile();
        } catch (IllegalArgumentException e) {
            MachineMax.LOGGER.error("弹种 {} 的 external.thrust 段窗口重叠，已按无推力处理：{}",
                    id == null ? "(未注册)" : id, e.getMessage());
            return ThrustProfile.NONE;
        }
    }

    /**
     * 地形预载的视界外扩半径（m）：弹体半径与制导可达量的较大者。
     * <p>
     * 预约是提前 {@code leadSeconds} 做出的，之后方向还能被制导拉偏，所以预约必须覆盖整个提前期内的
     * 可达集。指令加速度的上界取 {@code structural_limit_g}（结构上限）——制导律的指令上限不高于它，
     * 于是横向可达是 {@code ½·a_max·lead²}：15 g、0.2 s 时约 2.9 m。
     * <p>
     * 非制导弹种没有这一项，只按弹体半径外扩；{@code sweep} 判定的半径与弹体半径同源。
     *
     * @param leadSeconds 预约的提前量（s），即 {@code L + Δ}
     * @return 外扩半径（方块 / m）
     */
    public float getPreloadSweepRadius(float leadSeconds) {
        float reach = 0f;
        if (guidance != null && leadSeconds > 0f) {
            float maxAccel = guidance.limits().structuralLimitG() * 9.81f;
            reach = 0.5f * maxAccel * leadSeconds * leadSeconds;
        }
        return Math.max(getRadius(), reach);
    }

    /**
     * 获取此投射物类型的静态扩展数据缓存（口径、质量等不变字段）。
     * 惰性初始化，命中时通过 {@link BFDamageExtensions#copy()} 复制后追加冲量等动态值。
     *
     * @return 预填充了 CALIBER / MASS 的扩展容器（每次返回同一实例的副本供调用方修改）
     */
    public BFDamageExtensions getBaseExtensions() {
        if (baseExtensions == null) {
            synchronized (this) {
                if (baseExtensions == null) {
                    BFDamageExtensions exts = new BFDamageExtensions();
                    exts.set(BFDamageExtensions.CALIBER, getCaliber());
                    exts.set(BFDamageExtensions.MASS, getMass());
                    baseExtensions = exts;
                }
            }
        }
        return baseExtensions.copy();
    }

    // ==================== 终点效应委托（→ TerminalProperties） ====================

    public float getBasePenetration() { return terminal.basePenetration(); }
    public float getBaseDamage() { return terminal.baseDamage(); }
    public float getPenetrationVelocityCoefficient() { return terminal.penetrationVelocityCoefficient(); }
    public float getDamageVelocityCoefficient() { return terminal.damageVelocityCoefficient(); }
    public float getBlockDamageFactor() { return terminal.blockDamageFactor(); }

    /**
     * 本弹种造成的伤害类型（{@link DamageType} 资源键）。
     * <p>
     * 默认 {@link MMDamageTypes#PROJECTILE}（{@code machine_max:projectile}）。命中发起的
     * {@code DamageSource} 由它构造，因此该类型决定本次伤害落入哪些按类型分派的行为——材料侧的
     * {@code damage_modifiers}（按 {@code damage_type} / {@code damage_tag} 条件）、
     * 原版免疫与无敌帧、{@code SubPart.hurt} 的击退分支等。
     */
    public ResourceKey<DamageType> getDamageType() { return terminal.damageType(); }

    /** 稳定距离（mm），0 = 无限稳定 */
    public float getStableDistance() { return terminal.stableDistance(); }
    /** 失稳后穿深保留因子（0~1） */
    public float getUnstablePenFactor() { return terminal.unstablePenFactor(); }

    // ==================== 工厂方法 ====================

    /**
     * 创建投射物实例。
     *
     * @param level    维度
     * @param position 初始世界坐标（JME）
     * @param velocity 初始速度矢量（JME，单位 m/s）
     * @return 已创建的投射物实例
     */
    public BallisticProjectile create(Level level, Vector3f position, Vector3f velocity) {
        BallisticProjectile p = new BallisticProjectile(level, this, position, velocity);
        p.addToLevel();
        return p;
    }

    /**
     * 按指定 ID 创建投射物（服务端→客户端同步专用）。
     * <p>
     * 客户端从 {@code ProjectilesSpawnPayload} 收到服务端分配的 objId 后调用此方法，
     * 在 {@link BallisticProjectile#addToLevel()} 之前覆写自动生成的本地 ID，
     * 确保客户端 SoA / ObjectManager 中的 objId 与服务端一致，
     * 后续命中包才能通过 objId 匹配到正确的投射物。
     * <p>
     * <b>仅客户端调用。</b>服务端使用 {@link #create}。
     *
     * @param level    维度
     * @param position 初始世界坐标（JME）
     * @param velocity 初始速度矢量（JME，单位 m/s）
     * @param objId    服务端分配的 DestroyableObject ID
     * @return 已创建并注册的投射物实例
     */
    public BallisticProjectile createWithId(Level level, Vector3f position, Vector3f velocity, int objId) {
        BallisticProjectile p = new BallisticProjectile(level, this, position, velocity);
        p.setId(objId); // ★ 在 addToLevel 之前覆写，ObjectManager 和 SoA 均用此 ID
        p.addToLevel();
        return p;
    }

    // ==================== 开火 / 散布 ====================

    /**
     * 开火：根据发射参数创建 bullet_num 颗投射物。
     * <p>
     * 自动处理散布（椭圆锥采样）、速度计算。
     * 音效由 {@code LauncherSubsystem} 在客户端管理，不在此方法内播放。
     * <p>
     * <b>仅服务端调用。</b>客户端不将投射物加入世界，不应用后坐力。
     * <p>
     * <b>调用线程：</b>物理线程（由 {@code LauncherSubsystem.fireSingle} 调用）。
     *
     * @param level              维度（仅服务端）
     * @param muzzlePosition     枪口世界坐标（JME Vector3f）
     * @param direction          发射方向（JME Vector3f，单位向量）
     * @param velocityMultiplier 速度乘子（1.0 = 基础速度）
     * @param velocityBonus      固定速度加成（m/s）
     * @param hAccuracyMul       水平精度乘子（1.0 = 基础精度）
     * @param vAccuracyMul       垂直精度乘子（1.0 = 基础精度）
     * @param platformVelocity   发射平台速度（JME Vector3f，继承用）
     * @return 已创建的投射物列表（全部已 addToLevel）
     */
    public List<BallisticProjectile> fire(
        Level level,
        Vector3f muzzlePosition,
        Vector3f direction,
        float velocityMultiplier,
        float velocityBonus,
        float hAccuracyMul,
        float vAccuracyMul,
        Vector3f platformVelocity
    ) {
        float finalSpeed = external.baseVelocity() * velocityMultiplier + velocityBonus;
        float hRad = external.baseAccuracyMil() * hAccuracyMul / 1000f;
        float vRad = external.baseAccuracyMil() * vAccuracyMul / 1000f;

        List<BallisticProjectile> projectiles = new ArrayList<>(getBulletNum());
        for (int i = 0; i < getBulletNum(); i++) {
            Vector3f spreadDir = applyEllipticSpread(direction, hRad, vRad);
            Vector3f vel = spreadDir.mult(finalSpeed).addLocal(platformVelocity);
            projectiles.add(create(level, muzzlePosition, vel));
        }
        return projectiles;
    }

    /**
     * 椭圆锥散布采样。
     * <p>
     * 在 direction 的局部坐标系中，以椭圆锥（水平/垂直半角分别为 hRad / vRad
     * 弧度）均匀采样一个方向向量。椭圆锥的半角由基础精度（密位）与发射器
     * 精度乘子共同决定。
     * <p>
     * <b>调用线程：</b>任意线程（使用 ThreadLocalRandom）。
     *
     * @param direction 基准发射方向（单位向量）
     * @param hRad      水平散步半角（弧度）
     * @param vRad      垂直散步半角（弧度）
     * @return 散布后的方向向量（单位向量）
     */
    private static Vector3f applyEllipticSpread(Vector3f direction, float hRad, float vRad) {
        if (hRad <= 0f && vRad <= 0f) return direction;

        var random = ThreadLocalRandom.current();

        // 构建局部坐标系：right = direction × up, localUp = right × direction
        Vector3f up;
        if (Math.abs(direction.y) < 0.99f) {
            up = new Vector3f(0, 1, 0);
        } else {
            up = new Vector3f(1, 0, 0);
        }
        Vector3f right = direction.cross(up).normalize();
        Vector3f localUp = right.cross(direction).normalize();

        // 椭圆锥均匀采样
        double theta = random.nextDouble() * 2 * Math.PI;
        double hOffset = Math.cos(theta) * hRad;
        double vOffset = Math.sin(theta) * vRad;
        double radialDist = Math.sqrt(hOffset * hOffset + vOffset * vOffset);

        if (radialDist < 1e-10) return direction;

        double cosRadial = Math.cos(radialDist);
        double sinRadial = Math.sin(radialDist);

        return direction.mult((float) cosRadial)
            .addLocal(right.mult((float) (sinRadial * hOffset / radialDist)))
            .addLocal(localUp.mult((float) (sinRadial * vOffset / radialDist)))
            .normalize();
    }

    // ==================== 嵌套类（运动模型专属属性） ====================

    /**
     * 命中检测原语。
     * <p>
     * 两种取值的差别只在"攻击弹自身半径是否计入命中阈值"：
     * <ul>
     *   <li>{@link #RAY}——攻击弹的<b>质心</b>沿本物理步线段扫掠，零厚度，
     *       命中阈值只有目标半径；与地形 DDA、穿透去重、碰撞箱识别全部兼容，开销最低</li>
     *   <li>{@link #SWEEP}——以攻击弹自身半径的球做凸体扫掠，命中阈值为
     *       攻击弹半径 + 目标半径，适合"大弹打小弹"这类不能忽略自身截面的场合；
     *       每次查询带形状，宽相比射线贵，且起止距离须 ≥ 0.4 物理单位
     *       （不足时调用方回退射线）</li>
     * </ul>
     * 两者不是互斥的两套管线：结果字段逐项对应，归一化后喂入同一套命中分派。
     */
    public enum HitDetection {

        /** 质心射线 */
        RAY("ray"),

        /** 自身半径的凸体扫掠 */
        SWEEP("sweep");

        private final String serializedName;

        HitDetection(String serializedName) {
            this.serializedName = serializedName;
        }

        public String getSerializedName() {
            return serializedName;
        }

        /**
         * 从 JSON 字符串转换为枚举。
         *
         * @param name 序列化名（{@code "ray"} / {@code "sweep"}）
         * @return 对应的枚举值
         * @throws IllegalArgumentException 不匹配时抛出（由 ProjectileModule 捕获并记为内容包加载错误）
         */
        public static HitDetection fromString(String name) {
            return switch (name) {
                case "ray" -> RAY;
                case "sweep" -> SWEEP;
                default -> throw new IllegalArgumentException("未知命中检测原语: " + name);
            };
        }

        public static final Codec<HitDetection> CODEC =
            Codec.STRING.xmap(HitDetection::fromString, HitDetection::getSerializedName);
    }

    /**
     * 受击属性 — 本弹种作为目标被命中时的护甲与结构血量。
     * <p>
     * {@code rha} 与零部件侧的 {@code rha} <b>同名不同量纲</b>：零部件的 {@code rha} 是
     * 厚度的无量纲抗穿系数（{@code HitBox.getRHA} = 厚度 × rha），弹体没有材质与厚度结构，
     * 因此这里的 {@code rha} 直接就是垂直等效厚度（mm）。
     * <p>
     * {@code rha} 是硬门槛：命中管线先判穿甲，未击穿时默认的
     * {@code calculateFinalDamage} 返回 0，耐久因此不减少。
     * 要让某型拦截弹打得动某型目标，就把它配到能击穿对应厚度。
     * <p>
     * 触发体半径不在此块配置：由口径直接换算。
     *
     * @param rha        命中判定用的垂直等效厚度（mm）
     * @param durability 结构血量（点），累计伤害达到该值即损毁；
     *                   与零部件的 {@code durability} 走同一套机制
     */
    public record VulnerabilityProperties(
        float rha,
        float durability
    ) {
        public static final Codec<VulnerabilityProperties> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("rha").forGetter(VulnerabilityProperties::rha),
            Codec.FLOAT.fieldOf("durability").forGetter(VulnerabilityProperties::durability)
        ).apply(instance, VulnerabilityProperties::new));
    }

    /**
     * 外弹道属性 — 决定投射物如何飞行。
     * <p>
     * 所有参数采用国际单位制（SI）：质量 kg、长度 m、速度 m/s、角度密位。
     * 这些字段供运动积分与 {@code BallisticsFramework} 外弹道解算使用。
     */
    public record ExternalProperties(
        /**
         * 弹体基础质量（kg），<b>不含推进剂</b>。
         * <p>
         * 燃烧期内弹丸的总质量是 {@code mass + 各段尚未燃尽的装药}，由
         * {@link ThrustProfile#massAt} 给出：出膛质量因此是 {@code mass + Σ propellant_mass}，
         * 燃尽后回落到 {@code mass}。阻力与推力加速度都以当步的总质量为分母，所以同一个
         * {@code force} 在燃尽前后给出的加速度不同。不写装药的弹种总质量恒为 {@code mass}。
         */
        float mass,
        /** 重力系数，1.0 = 标准重力，0 = 无重力 */
        float gravityFactor,
        /** 空气阻力系数（速度²阻力），0 = 无阻力 */
        float dragFactor,
        /** 口径（mm）。换算为半径（口径/2000 m）后同时用于风阻截面积 π·r² 与射线命中判定 */
        float caliberMm,
        /** 参考速度（m/s），速度-伤害模型的基准速度 */
        float baseVelocity,
        /**
         * 基础精度（密位/千分弧度）。
         * 表示 1σ 散步角，与发射器的精度乘子叠加。
         * 默认 5.0 密位 ≈ 每公里 5 米散布。
         */
        float baseAccuracyMil,
        /**
         * 推进段表，缺省空表（无推力，纯弹道）。
         * <p>
         * 多段：数组元素各自带点火时刻、时长与装药，段间允许空隙（空隙即无推力滑行）。整表经
         * {@link #toThrustProfile()} 折算成弹道库的剖面后<b>全部生效</b>——不存在"只施加首段"
         * 这种截断。字段语义见 {@link ThrustSegment}。各段装药的合计必须小于 {@link #mass}，
         * 否则解码失败（见 {@link #CODEC}）。
         */
        List<ThrustSegment> thrust
    ) {
        public static final Codec<ExternalProperties> CODEC =
                RecordCodecBuilder.<ExternalProperties>create(instance -> instance.group(
                        Codec.FLOAT.fieldOf("mass").forGetter(ExternalProperties::mass),
                        Codec.FLOAT.optionalFieldOf("gravity_factor", 1.0f).forGetter(ExternalProperties::gravityFactor),
                        Codec.FLOAT.optionalFieldOf("drag_factor", 0f).forGetter(ExternalProperties::dragFactor),
                        Codec.FLOAT.optionalFieldOf("caliber", 50.0f).forGetter(ExternalProperties::caliberMm),
                        Codec.FLOAT.fieldOf("base_velocity").forGetter(ExternalProperties::baseVelocity),
                        Codec.FLOAT.optionalFieldOf("base_accuracy_mil", 5.0f).forGetter(ExternalProperties::baseAccuracyMil),
                        Codec.list(ThrustSegment.CODEC).optionalFieldOf("thrust", List.of())
                                .forGetter(ExternalProperties::thrust)
                ).apply(instance, ExternalProperties::new))
                .flatXmap(ExternalProperties::checkPropellant, DataResult::success);

        /**
         * 解码期的跨字段校验：装药逐段非负，且合计小于弹体质量。
         * <p>
         * 这是外面那层 {@code flatXmap} 的实体，<b>只在解码方向生效</b>（编码原样通过）。两条都是
         * 内容包的输入约束而不是求解器的不变式，所以放在信任边界上，失败时由 {@code PackCodec}
         * 转成一条带文件定位的内容包错误，而不是等开火才炸：
         * <ul>
         *   <li>负装药会让该段被 {@link ThrustProfile} 当成无效段丢弃——推力一起消失，作者却只看到
         *       "没推力"，因此在这里直接拒绝；</li>
         *   <li>装药合计不小于 {@code mass} 时，燃烧末期的总质量会归零或为负，加速度发散。
         *       {@link ThrustProfile#massAt} 虽然有 1e-6 kg 的下限兜底，但那是防除零，不是可用配置。</li>
         * </ul>
         */
        private static DataResult<ExternalProperties> checkPropellant(ExternalProperties props) {
            float propellant = 0f;
            for (int i = 0; i < props.thrust.size(); i++) {
                float mass = props.thrust.get(i).propellantMass();
                if (mass < 0f) {
                    int index = i;
                    return DataResult.error(() -> "external.thrust[" + index + "].propellant_mass 必须为非负数，实际为 " + mass);
                }
                propellant += mass;
            }
            float total = propellant;
            if (total >= props.mass) {
                return DataResult.error(() -> "external.thrust 的装药合计 " + total
                        + " kg 必须小于 external.mass " + props.mass + " kg（推进剂不能重于弹体，否则燃尽时质量归零）");
            }
            return DataResult.success(props);
        }

        /**
         * 把段表折算成弹道库的推力剖面。
         * <p>
         * 这里只做字段映射与逐段搬运：排序、丢弃无效段、拒绝重叠都由
         * {@link ThrustProfile} 的构造器完成，MM 侧不重复这些判据。每段的 {@code propellant_mass}
         * 原样进入 {@code ThrustProfile.Segment#massKg}，段内匀速烧掉；不写该字段时它是 0，
         * 该段质量恒定，总质量因此在整个燃烧窗口里保持 {@link #mass}。
         *
         * @return 弹道库的推力剖面；段表为空时是 {@link ThrustProfile#NONE}
         * @throws IllegalArgumentException 段窗口重叠时由 {@link ThrustProfile} 的构造器抛出
         */
        public ThrustProfile toThrustProfile() {
            if (thrust.isEmpty()) {
                return ThrustProfile.NONE;
            }
            List<ThrustProfile.Segment> table = new ArrayList<>(thrust.size());
            for (ThrustSegment segment : thrust) {
                table.add(new ThrustProfile.Segment(
                        segment.force(), segment.start(), segment.duration(), segment.propellantMass()));
            }
            return new ThrustProfile(table);
        }

        /**
         * 推进段 — 单段恒推力，沿<b>速度矢量</b>施加（无姿态质点弹的合理近似：弹轴 ≈ 速度）。
         * <p>
         * 只增速率、不改方向，与制导/阻力/重力共用同一套半隐式 Euler 积分。段内加速度为
         * {@code force / m(t)}，质量随装药烧掉而下降，因此同一段内加速度单调上升；燃烧窗口为
         * {@code [start, start + duration)}，多段之间允许空隙，空隙内无推力。
         * <p>
         * <b>只接受数组形态。</b>{@code external.thrust} 的单对象写法会在解码期以「期望列表」
         * 失败——有意如此，错误要响亮而非静默降级。
         * <p>
         * <b>两端一致：</b>服务端 {@code updateProjectiles} 与客户端 {@code clientExtrapolate}
         * 均按同一条剖面施加推力，保证客户端外推轨迹与服务端一致。
         * <p>
         * 段的有效性与窗口终点不在这里判定：{@code force} 或 {@code duration} 非正的段由
         * {@link ThrustProfile} 的构造器丢弃，窗口终点是 {@code start + duration}。
         *
         * @param force          推力（N）
         * @param start          点火时刻（s），自发射瞬间起算；0 = 出膛即点火
         * @param duration       燃烧时长（s），必须为正才生效
         * @param propellantMass 本段全程烧掉的推进剂（kg），段内匀速消耗；0（缺省）= 本段质量恒定。
         *                       各段合计必须小于 {@link ExternalProperties#mass}，且不得为负
         */
        public record ThrustSegment(
            float force,
            float start,
            float duration,
            float propellantMass
        ) {
            public static final Codec<ThrustSegment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("force", 0f).forGetter(ThrustSegment::force),
                Codec.FLOAT.optionalFieldOf("start", 0f).forGetter(ThrustSegment::start),
                Codec.FLOAT.optionalFieldOf("duration", 0f).forGetter(ThrustSegment::duration),
                Codec.FLOAT.optionalFieldOf("propellant_mass", 0f).forGetter(ThrustSegment::propellantMass)
            ).apply(instance, ThrustSegment::new));
        }
    }

    /**
     * 终点效应属性 — 决定投射物命中后发生什么。
     * <p>
     * 这些字段供 {@code BFDamageApi.hurt()} 的穿透判定、伤害计算和方块破坏逻辑使用。
     * 穿深使用德马尔公式或其简化形式，与速度的幂函数关系由
     * {@code penetration_velocity_coefficient} 和 {@code damage_velocity_coefficient} 控制。
     */
    public record TerminalProperties(
        /** 参考速度下的穿深（mm RHA） */
        float basePenetration,
        /** 参考速度下的伤害值 */
        float baseDamage,
        /** 穿深速度系数。0 = 与速度无关，~1.43 = 经典德马尔公式 */
        float penetrationVelocityCoefficient,
        /** 伤害速度系数。0 = 与速度无关 */
        float damageVelocityCoefficient,
        /**
         * 方块破坏因子（0~1）。
         * 穿透方块时，动能×此因子与方块耐久对比，决定是否实际破坏方块。
         * 0 = 永远不破坏方块（仅穿透）。默认值 1。
         */
        float blockDamageFactor,
        /**
         * 稳定距离（mm），弹头在材料中能够保持定向飞行的最大物理距离。
         * 与穿深单位一致。0 = 无限稳定（如 APFSDS 长杆弹）。默认值 0。
         */
        float stableDistance,
        /**
         * 失稳后穿深保留因子（0~1）。弹头翻滚后有效穿深 = 当前穿深 × 此因子。
         * 0 = 失稳后无法继续穿透。默认值 0.1。
         */
        float unstablePenFactor,
        /**
         * 伤害类型（{@link DamageType} 资源键）。默认 {@link MMDamageTypes#PROJECTILE}。
         * <p>
         * 命中时经 {@code Level.damageSources().source(...)} 构造发起的 DamageSource，
         * 决定该次伤害落入原版与本项目的哪些按类型分派的行为
         * （材料侧 {@code damage_modifiers} 的 {@code damage_type} / {@code damage_tag} 条件、
         * 原版免疫与无敌帧等）。
         */
        ResourceKey<DamageType> damageType
    ) {
        public static final Codec<TerminalProperties> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("base_penetration").forGetter(TerminalProperties::basePenetration),
            Codec.FLOAT.fieldOf("base_damage").forGetter(TerminalProperties::baseDamage),
            Codec.FLOAT.optionalFieldOf("penetration_velocity_coefficient", 0f)
                .forGetter(TerminalProperties::penetrationVelocityCoefficient),
            Codec.FLOAT.optionalFieldOf("damage_velocity_coefficient", 0f)
                .forGetter(TerminalProperties::damageVelocityCoefficient),
            Codec.FLOAT.optionalFieldOf("block_damage_factor", 1.0f)
                .forGetter(TerminalProperties::blockDamageFactor),
            Codec.FLOAT.optionalFieldOf("stable_distance", 0f)
                .forGetter(TerminalProperties::stableDistance),
            Codec.FLOAT.optionalFieldOf("unstable_pen_factor", 0.1f)
                .forGetter(TerminalProperties::unstablePenFactor),
            ResourceKey.codec(Registries.DAMAGE_TYPE).optionalFieldOf("damage_type", MMDamageTypes.PROJECTILE)
                .forGetter(TerminalProperties::damageType)
        ).apply(instance, TerminalProperties::new));
    }
}
