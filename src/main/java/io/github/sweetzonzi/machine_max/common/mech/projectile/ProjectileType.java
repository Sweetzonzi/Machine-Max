package io.github.sweetzonzi.machine_max.common.mech.projectile;

import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.common.resource.modules.ProjectileModule;
import lombok.Getter;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 投射物类型数据定义。
 * <p>
 * 对应一个 {@code projectiles/*.json} 文件，使用 Mojang Codec 从 JSON 反序列化。
 * 所有弹道参数在此定义，通过 {@link ProjectileModule} 加载至 {@link MMDynamicRes}，
 * 运行时由 {@link IProjectile#getProjectileType()} 获取。
 * <p>
 * 字段语义参见设计文档 §4。速度-伤害模型见 {@link IProjectile} 中的幂函数实现。
 */
@Getter
public class ProjectileType {

    /** 投射物类型（枚举），JSON 中以字符串 "point" / "rigid" 读写 */
    private final ProjectileTypeEnum type;

    /** 质量（kg） */
    private final float mass;

    /** 重力系数，1.0 = 标准重力，0 = 无重力 */
    private final float gravityFactor;

    /** 空气阻力系数（速度²阻力），0 = 无阻力 */
    private final float dragFactor;

    /** 碰撞半径（m），质点用于射线检测命中判定，刚体用于 SphereCollisionShape */
    private final float radius;

    /** 参考速度（m/s），速度-伤害模型的基准速度 */
    private final float baseVelocity;

    /** 参考速度下的穿深（mm RHA） */
    private final float basePenetration;

    /** 参考速度下的伤害值 */
    private final float baseDamage;

    /**
     * 基础精度（密位/千分弧度）。
     * 表示 1σ 散步角，与发射器的精度乘子叠加。
     * 默认 5.0 密位 ≈ 每公里 5 米散布。
     */
    private final float baseAccuracyMil;

    /** 穿深速度系数。0 = 与速度无关，~1.43 = 经典德马尔公式 */
    private final float penetrationVelocityCoefficient;

    /** 伤害速度系数。0 = 与速度无关 */
    private final float damageVelocityCoefficient;

    /** 最大存活 tick 数（默认 200 tick = 10 秒 @ 20Hz） */
    private final int maxLifetimeTicks;

    /** 注册键，由 {@link ProjectileModule} 加载时赋值 */
    private ResourceLocation registryKey;

    /** 弹药tag列表，用于与发射器的 {@code required_tags / acceptable_tags / forbidden_tags} 匹配 */
    private final List<ResourceLocation> tags;

    /** 曳光颜色（RGB），不设置则无曳光效果 */
    private final Vec3i tracerColor;

    /** 曳光透明度，0=完全透明，255=完全不透明 */
    private final int tracerAlpha;

    /** 字符串↔枚举互转 Codec */
    private static final Codec<ProjectileTypeEnum> ENUM_CODEC =
        Codec.STRING.xmap(ProjectileTypeEnum::fromString, ProjectileTypeEnum::getSerializedName);

    /** Mojang Codec：将 JSON 反序列化为 ProjectileType */
    public static final Codec<ProjectileType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ENUM_CODEC.fieldOf("type").forGetter(ProjectileType::getType),
        Codec.FLOAT.fieldOf("mass").forGetter(ProjectileType::getMass),
        Codec.FLOAT.optionalFieldOf("gravity_factor", 1.0f).forGetter(ProjectileType::getGravityFactor),
        Codec.FLOAT.optionalFieldOf("drag_factor", 0f).forGetter(ProjectileType::getDragFactor),
        Codec.FLOAT.optionalFieldOf("radius", 0.05f).forGetter(ProjectileType::getRadius),
        Codec.FLOAT.fieldOf("base_velocity").forGetter(ProjectileType::getBaseVelocity),
        Codec.FLOAT.fieldOf("base_penetration").forGetter(ProjectileType::getBasePenetration),
        Codec.FLOAT.fieldOf("base_damage").forGetter(ProjectileType::getBaseDamage),
        Codec.FLOAT.optionalFieldOf("base_accuracy_mil", 5.0f)
            .forGetter(ProjectileType::getBaseAccuracyMil),
        Codec.FLOAT.optionalFieldOf("penetration_velocity_coefficient", 0f)
            .forGetter(ProjectileType::getPenetrationVelocityCoefficient),
        Codec.FLOAT.optionalFieldOf("damage_velocity_coefficient", 0f)
            .forGetter(ProjectileType::getDamageVelocityCoefficient),
        Codec.INT.optionalFieldOf("max_lifetime_ticks", 200)
            .forGetter(ProjectileType::getMaxLifetimeTicks),
        ResourceLocation.CODEC.listOf().optionalFieldOf("tags", List.of())
            .forGetter(ProjectileType::getTags),
        Vec3i.CODEC.optionalFieldOf("tracer_color", new Vec3i(255, 255, 255))
            .forGetter(ProjectileType::getTracerColor),
        Codec.INT.optionalFieldOf("tracer_alpha", 200)
            .forGetter(ProjectileType::getTracerAlpha)
    ).apply(instance, ProjectileType::new));

    public ProjectileType(
        ProjectileTypeEnum type, float mass, float gravityFactor, float dragFactor, float radius,
        float baseVelocity, float basePenetration, float baseDamage, float baseAccuracyMil,
        float penetrationVelocityCoefficient, float damageVelocityCoefficient,
        int maxLifetimeTicks, List<ResourceLocation> tags,
        Vec3i tracerColor, int tracerAlpha
    ) {
        this.type = type;
        this.mass = mass;
        this.gravityFactor = gravityFactor;
        this.dragFactor = dragFactor;
        this.radius = radius;
        this.baseVelocity = baseVelocity;
        this.basePenetration = basePenetration;
        this.baseDamage = baseDamage;
        this.baseAccuracyMil = baseAccuracyMil;
        this.penetrationVelocityCoefficient = penetrationVelocityCoefficient;
        this.damageVelocityCoefficient = damageVelocityCoefficient;
        this.maxLifetimeTicks = maxLifetimeTicks;
        this.tags = tags;
        this.tracerColor = tracerColor;
        this.tracerAlpha = tracerAlpha;
    }

    /**
     * 设置注册键，由 {@link ProjectileModule} 在加载时调用。
     * 每个 ProjectileType 仅可被注册一次。
     *
     * @param registryKey 资源键（如 {@code machine_max:20mm_ap}）
     * @throws UnsupportedOperationException 重复注册时抛出
     */
    public void setRegistryKey(ResourceLocation registryKey) {
        if (this.registryKey != null)
            throw new UnsupportedOperationException(
                "ProjectileType " + this.registryKey + " already registered, cannot register as " + registryKey + ".");
        this.registryKey = registryKey;
    }

    /**
     * 按资源键从全局缓存中获取投射物类型。
     *
     * @param level 当前维度（客户端/服务端自动分流）
     * @param key   投射物类型资源键
     * @return 投射物类型，或 null（未找到时）
     */
    public static ProjectileType get(Level level, ResourceLocation key) {
        return level.isClientSide
            ? MMDynamicRes.PROJECTILE_TYPES.get(key)
            : MMDynamicRes.SERVER_PROJECTILE_TYPES.get(key);
    }

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
}
