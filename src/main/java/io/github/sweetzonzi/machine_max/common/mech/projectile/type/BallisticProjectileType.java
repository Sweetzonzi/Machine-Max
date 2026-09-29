package io.github.sweetzonzi.machine_max.common.mech.projectile.type;

import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageExtensions;
import io.github.sweetzonzi.machine_max.common.mech.projectile.BallisticProjectile;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.projectile.component.effect.WorldEffect;
import io.github.sweetzonzi.machine_max.common.mech.projectile.component.guidance.GuidanceLaw;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
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

    // ==================== CODEC（11 字段，远低于 16 上限） ====================

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
    ).apply(instance, (tags, maxLifetime, bulletNum, external, terminal, vulnerability, hitDetection, guidance, warheads, visual, sounds) ->
        new BallisticProjectileType(tags, maxLifetime, bulletNum, external, terminal,
            vulnerability.orElse(null), hitDetection, guidance.orElse(null), warheads, visual, sounds)));

    // ==================== 构造函数 ====================

    public BallisticProjectileType(
        List<ResourceLocation> tags,
        float maxLifetime,
        int bulletNum,
        ExternalProperties external,
        TerminalProperties terminal,
        @Nullable VulnerabilityProperties vulnerability,
        HitDetection hitDetection,
        @Nullable GuidanceLaw guidance,
        List<WorldEffect> warheads,
        VisualProperties visual,
        ProjectileSoundAttr sounds
    ) {
        super(tags, maxLifetime, bulletNum, visual, sounds);
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
    /** 推进段配置（缺省 {@link ExternalProperties.ThrustProperties#NONE}，即无推力） */
    public ExternalProperties.ThrustProperties getThrust() { return external.thrust(); }
    /** 是否配置了有效推进段 */
    public boolean hasThrust() { return external.thrust().isActive(); }

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
        /** 质量（kg） */
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
        /** 推进段配置，缺省 {@link ThrustProperties#NONE}（无推力，纯弹道） */
        ThrustProperties thrust
    ) {
        public static final Codec<ExternalProperties> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("mass").forGetter(ExternalProperties::mass),
            Codec.FLOAT.optionalFieldOf("gravity_factor", 1.0f).forGetter(ExternalProperties::gravityFactor),
            Codec.FLOAT.optionalFieldOf("drag_factor", 0f).forGetter(ExternalProperties::dragFactor),
            Codec.FLOAT.optionalFieldOf("caliber", 50.0f).forGetter(ExternalProperties::caliberMm),
            Codec.FLOAT.fieldOf("base_velocity").forGetter(ExternalProperties::baseVelocity),
            Codec.FLOAT.optionalFieldOf("base_accuracy_mil", 5.0f).forGetter(ExternalProperties::baseAccuracyMil),
            ThrustProperties.CODEC.optionalFieldOf("thrust", ThrustProperties.NONE)
                .forGetter(ExternalProperties::thrust)
        ).apply(instance, ExternalProperties::new));

        /**
         * 推进段 — 单段恒推力，沿<b>速度矢量</b>施加（无姿态质点弹的合理近似：弹轴 ≈ 速度）。
         * <p>
         * 只增速率、不改方向，与制导/阻力/重力共用同一套半隐式 Euler 积分。
         * 推力段内加速度为 {@code F / m}，燃烧窗口为 {@code [ignition_delay, ignition_delay + duration)}。
         * 首期只支持单段点火（多段 / 推力曲线见《武器系统-制导组件实现备忘》§十四）。
         * <p>
         * <b>两端一致：</b>服务端 {@code updateProjectiles} 与客户端 {@code clientExtrapolate}
         * 均按同一窗口施加推力，保证客户端外推轨迹与服务端一致。
         *
         * @param force         推力（N）
         * @param duration      燃烧时长（s）
         * @param ignitionDelay 点火延迟（s），从发射瞬间起算；0 = 出膛即点火
         */
        public record ThrustProperties(
            float force,
            float duration,
            float ignitionDelay
        ) {
            /** 无推力（缺省），等价于纯弹道 */
            public static final ThrustProperties NONE = new ThrustProperties(0f, 0f, 0f);

            public static final Codec<ThrustProperties> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("force", 0f).forGetter(ThrustProperties::force),
                Codec.FLOAT.optionalFieldOf("duration", 0f).forGetter(ThrustProperties::duration),
                Codec.FLOAT.optionalFieldOf("ignition_delay", 0f).forGetter(ThrustProperties::ignitionDelay)
            ).apply(instance, ThrustProperties::new));

            /** 是否配置了有效推进段（推力和燃时均 &gt; 0） */
            public boolean isActive() {
                return force > 0f && duration > 0f;
            }

            /** 燃烧窗口结束时刻（s，从发射瞬间起算） */
            public float burnEnd() {
                return ignitionDelay + duration;
            }
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
        float unstablePenFactor
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
                .forGetter(TerminalProperties::unstablePenFactor)
        ).apply(instance, TerminalProperties::new));
    }
}
