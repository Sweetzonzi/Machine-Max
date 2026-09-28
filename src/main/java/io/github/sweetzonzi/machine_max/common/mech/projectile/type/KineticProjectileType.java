package io.github.sweetzonzi.machine_max.common.mech.projectile.type;

import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageExtensions;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.projectile.IProjectile;
import io.github.sweetzonzi.machine_max.common.mech.projectile.PointProjectile;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileTypeEnum;
import io.github.sweetzonzi.machine_max.common.mech.projectile.RigidProjectile;
import io.github.sweetzonzi.machine_max.common.mech.projectile.component.effect.WorldEffect;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 飞行弹丸类型（{@code point} / {@code rigid}）。
 * <p>
 * 在 {@link ProjectileType} 共享字段之外，承载运动模型专属字段：
 * {@link #getType() 物理模型枚举}、{@link ExternalProperties 外弹道}、
 * {@link TerminalProperties 终点效应}，以及 {@link #getWarheads() 战斗部效果列表}。
 * <p>
 * 由 {@link ProjectileType#CODEC} 按 JSON 的 {@code "type"} 字段分派到此类的
 * {@link #CODEC}；{@code point} 与 {@code rigid} 路由到同一个 {@code CODEC}，
 * 二者的区分由 {@code "type"} 字段本身（{@link #getType()}）完成。
 * <p>
 * 速度-伤害模型见 {@link IProjectile} 中的幂函数实现。
 * 字段语义参见设计文档《武器系统-组件化投射物与类型体系设计》§三。
 */
@Getter
public class KineticProjectileType extends ProjectileType {

    // ==================== 运动模型专属字段 ====================

    /** 物理模型类型（枚举），JSON 中以字符串 "point" / "rigid" 读写 */
    private final ProjectileTypeEnum type;

    /** 外弹道属性 — 决定投射物如何飞行 */
    private final ExternalProperties external;

    /** 终点效应属性 — 决定投射物命中后发生什么 */
    private final TerminalProperties terminal;

    /**
     * 战斗部效果列表 — 命中判定完成后依次执行的世界效果。
     * <p>
     * 空列表表示纯动能弹（仅执行直接动能命中）。首期唯一实现是参数化爆炸
     * （{@code machine_max:blast}，见 {@code ExplosionWorldEffect}）。
     */
    private final List<WorldEffect> warheads;

    /** 静态扩展数据缓存（口径、质量等不变信息），惰性初始化，命中时 copy 后追加动态值 */
    private volatile BFDamageExtensions baseExtensions;

    // ==================== 字符串↔枚举互转 Codec ====================

    private static final Codec<ProjectileTypeEnum> ENUM_CODEC =
        Codec.STRING.xmap(ProjectileTypeEnum::fromString, ProjectileTypeEnum::getSerializedName);

    // ==================== CODEC（9 字段，远低于 16 上限） ====================

    /**
     * Mojang MapCodec：将 JSON 反序列化为 {@link KineticProjectileType}。
     * <p>
     * {@code "type"} 字段同时被 {@link ProjectileType#CODEC} 的 dispatch 读取（用于路由）
     * 和本 Codec 读取（用于确定 {@link #getType()} 枚举），两者取值一致。
     */
    public static final MapCodec<KineticProjectileType> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        ENUM_CODEC.fieldOf("type").forGetter(KineticProjectileType::getType),
        ResourceLocation.CODEC.listOf().optionalFieldOf("tags", List.of())
            .forGetter(ProjectileType::getTags),
        Codec.FLOAT.optionalFieldOf("max_lifetime", 10.0f)
            .forGetter(ProjectileType::getMaxLifetime),
        Codec.INT.optionalFieldOf("bullet_num", 1)
            .forGetter(ProjectileType::getBulletNum),
        ExternalProperties.CODEC.fieldOf("external")
            .forGetter(KineticProjectileType::getExternal),
        TerminalProperties.CODEC.fieldOf("terminal")
            .forGetter(KineticProjectileType::getTerminal),
        WorldEffect.CODEC.listOf().optionalFieldOf("warheads", List.of())
            .forGetter(KineticProjectileType::getWarheads),
        VisualProperties.CODEC.optionalFieldOf("visual", VisualProperties.DEFAULT)
            .forGetter(ProjectileType::getVisual),
        ProjectileSoundAttr.CODEC.optionalFieldOf("sounds", ProjectileSoundAttr.DEFAULT)
            .forGetter(ProjectileType::getSounds)
    ).apply(instance, KineticProjectileType::new));

    // ==================== 构造函数 ====================

    public KineticProjectileType(
        ProjectileTypeEnum type,
        List<ResourceLocation> tags,
        float maxLifetime,
        int bulletNum,
        ExternalProperties external,
        TerminalProperties terminal,
        List<WorldEffect> warheads,
        VisualProperties visual,
        ProjectileSoundAttr sounds
    ) {
        super(tags, maxLifetime, bulletNum, visual, sounds);
        this.type = type;
        this.external = external;
        this.terminal = terminal;
        this.warheads = warheads;
    }

    @Override
    public String getSerializedName() {
        return type.getSerializedName();
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
     * 按类型枚举自动分派创建投射物实例。
     * <p>
     * {@link ProjectileTypeEnum#POINT} → {@link PointProjectile}，
     * {@link ProjectileTypeEnum#RIGID} → {@link RigidProjectile}。
     * 调用方无需手动判断。
     *
     * @param level    维度
     * @param position 初始世界坐标（JME）
     * @param velocity 初始速度矢量（JME，单位 m/s）
     * @return 已创建的投射物实例
     */
    public IProjectile create(Level level, Vector3f position, Vector3f velocity) {
        IProjectile p = switch (type) {
            case POINT -> new PointProjectile(level, this, position, velocity);
            case RIGID -> new RigidProjectile(level, this, position, velocity);
        };
        ((DestroyableObject) p).addToLevel();
        return p;
    }

    /**
     * 按指定 ID 创建投射物（服务端→客户端同步专用）。
     * <p>
     * 客户端从 {@code ProjectilesSpawnPayload} 收到服务端分配的 objId 后调用此方法，
     * 在 {@link DestroyableObject#addToLevel()} 之前覆写自动生成的本地 ID，
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
    public IProjectile createWithId(Level level, Vector3f position, Vector3f velocity, int objId) {
        IProjectile p = switch (type) {
            case POINT -> new PointProjectile(level, this, position, velocity);
            case RIGID -> new RigidProjectile(level, this, position, velocity);
        };
        ((DestroyableObject) p).setId(objId); // ★ 在 addToLevel 之前覆写，ObjectManager 和 SoA 均用此 ID
        ((DestroyableObject) p).addToLevel();
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
    public List<IProjectile> fire(
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

        List<IProjectile> projectiles = new ArrayList<>(getBulletNum());
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
     * 外弹道属性 — 决定投射物如何飞行。
     * <p>
     * 所有参数采用国际单位制（SI）：质量 kg、长度 m、速度 m/s、角度密位。
     * 这些字段供运动积分（质点 SoA / Bullet 刚体）与 {@code BallisticsFramework} 外弹道解算使用。
     */
    public record ExternalProperties(
        /** 质量（kg） */
        float mass,
        /** 重力系数，1.0 = 标准重力，0 = 无重力 */
        float gravityFactor,
        /** 空气阻力系数（速度²阻力），0 = 无阻力 */
        float dragFactor,
        /** 口径（mm），质点用于射线检测命中判定，刚体用于 SphereCollisionShape */
        float caliberMm,
        /** 参考速度（m/s），速度-伤害模型的基准速度 */
        float baseVelocity,
        /**
         * 基础精度（密位/千分弧度）。
         * 表示 1σ 散步角，与发射器的精度乘子叠加。
         * 默认 5.0 密位 ≈ 每公里 5 米散布。
         */
        float baseAccuracyMil
    ) {
        public static final Codec<ExternalProperties> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("mass").forGetter(ExternalProperties::mass),
            Codec.FLOAT.optionalFieldOf("gravity_factor", 1.0f).forGetter(ExternalProperties::gravityFactor),
            Codec.FLOAT.optionalFieldOf("drag_factor", 0f).forGetter(ExternalProperties::dragFactor),
            Codec.FLOAT.optionalFieldOf("caliber", 50.0f).forGetter(ExternalProperties::caliberMm),
            Codec.FLOAT.fieldOf("base_velocity").forGetter(ExternalProperties::baseVelocity),
            Codec.FLOAT.optionalFieldOf("base_accuracy_mil", 5.0f).forGetter(ExternalProperties::baseAccuracyMil)
        ).apply(instance, ExternalProperties::new));
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
