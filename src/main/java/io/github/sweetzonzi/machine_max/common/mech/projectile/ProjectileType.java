package io.github.sweetzonzi.machine_max.common.mech.projectile;

import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.common.resource.modules.ProjectileModule;
import lombok.Getter;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 投射物类型数据定义。
 * <p>
 * 对应一个 {@code projectiles/*.json} 文件，使用 Mojang Codec 从 JSON 反序列化。
 * 字段按语义分为三组嵌套对象：{@link ExternalProperties 外弹道}、
 * {@link TerminalProperties 终点效应}、{@link VisualProperties 视觉/音效}，
 * 外加 {@code type / tags / max_lifetime / bullet_num} 四个平铺字段。
 * <p>
 * 所有弹道参数在此定义，通过 {@link ProjectileModule} 加载至 {@link MMDynamicRes}，
 * 运行时由 {@link IProjectile#getProjectileType()} 获取。
 * <p>
 * 为向后兼容，平铺字段和所有嵌套字段的属性都提供委托 getter（如
 * {@link #getMass()}、{@link #getBaseVelocity()} 等），调用方无需改动。
 * <p>
 * 字段语义参见设计文档 §4。速度-伤害模型见 {@link IProjectile} 中的幂函数实现。
 */
@Getter
public class ProjectileType {

    // ==================== 平铺字段 ====================

    /** 投射物类型（枚举），JSON 中以字符串 "point" / "rigid" 读写 */
    private final ProjectileTypeEnum type;

    /** 弹药标签列表，用于与发射器的 required_tags / acceptable_tags / forbidden_tags 匹配 */
    private final List<ResourceLocation> tags;

    /** 最大存活时间（秒，SI 单位），内部使用时 ×20 转为 tick */
    private final float maxLifetime;

    /** 单发弹头数（默认 1），用于表示霰弹等一次射出多个弹丸 */
    private final int bulletNum;

    /** 外弹道属性 — 决定投射物如何飞行 */
    private final ExternalProperties external;

    /** 终点效应属性 — 决定投射物命中后发生什么 */
    private final TerminalProperties terminal;

    /** 视觉属性 — 决定投射物在客户端如何呈现（曳光等） */
    private final VisualProperties visual;

    /** 音效属性 — 开火、停火、弹壳等音效 */
    private final ProjectileSoundAttr sounds;

    /** 注册键，由 {@link ProjectileModule} 加载时赋值 */
    private ResourceLocation registryKey;


    // ==================== 字符串↔枚举互转 Codec ====================

    private static final Codec<ProjectileTypeEnum> ENUM_CODEC =
        Codec.STRING.xmap(ProjectileTypeEnum::fromString, ProjectileTypeEnum::getSerializedName);


    // ==================== 顶层 CODEC（7 字段，远低于 16 上限） ====================

    /** Mojang Codec：将 JSON 反序列化为 ProjectileType */
    public static final Codec<ProjectileType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ENUM_CODEC.fieldOf("type").forGetter(ProjectileType::getType),
        ResourceLocation.CODEC.listOf().optionalFieldOf("tags", List.of())
            .forGetter(ProjectileType::getTags),
        Codec.FLOAT.optionalFieldOf("max_lifetime", 10.0f)
            .forGetter(ProjectileType::getMaxLifetime),
        Codec.INT.optionalFieldOf("bullet_num", 1)
            .forGetter(ProjectileType::getBulletNum),
        ExternalProperties.CODEC.fieldOf("external")
            .forGetter(ProjectileType::getExternal),
        TerminalProperties.CODEC.fieldOf("terminal")
            .forGetter(ProjectileType::getTerminal),
        VisualProperties.CODEC.optionalFieldOf("visual", VisualProperties.DEFAULT)
            .forGetter(ProjectileType::getVisual),
        ProjectileSoundAttr.CODEC.optionalFieldOf("sounds", ProjectileSoundAttr.DEFAULT)
            .forGetter(ProjectileType::getSounds)
    ).apply(instance, ProjectileType::new));


    // ==================== 构造函数 ====================

    public ProjectileType(
        ProjectileTypeEnum type,
        List<ResourceLocation> tags,
        float maxLifetime,
        int bulletNum,
        ExternalProperties external,
        TerminalProperties terminal,
        VisualProperties visual,
        ProjectileSoundAttr sounds
    ) {
        this.type = type;
        this.tags = tags;
        this.maxLifetime = maxLifetime;
        this.bulletNum = bulletNum;
        this.external = external;
        this.terminal = terminal;
        this.visual = visual;
        this.sounds = sounds;
    }


    // ==================== 委托方法 — 向后兼容旧调用方 ====================

    /** 最大存活 tick 数（由秒换算，×20） */
    public int getMaxLifetimeTicks() {
        return (int)(maxLifetime * 20);
    }

    // -- 外弹道委托（→ ExternalProperties） --

    public float getMass() { return external.mass(); }
    public float getGravityFactor() { return external.gravityFactor(); }
    public float getDragFactor() { return external.dragFactor(); }
    public float getRadius() { return external.radius(); }
    public float getBaseVelocity() { return external.baseVelocity(); }
    public float getBaseAccuracyMil() { return external.baseAccuracyMil(); }

    // -- 终点效应委托（→ TerminalProperties） --

    public float getBasePenetration() { return terminal.basePenetration(); }
    public float getBaseDamage() { return terminal.baseDamage(); }
    public float getPenetrationVelocityCoefficient() { return terminal.penetrationVelocityCoefficient(); }
    public float getDamageVelocityCoefficient() { return terminal.damageVelocityCoefficient(); }
    public float getBlockDamageFactor() { return terminal.blockDamageFactor(); }

    // -- 视觉委托（→ VisualProperties） --

    public Vec3i getTracerColor() { return visual.tracerColor(); }
    public int getTracerAlpha() { return visual.tracerAlpha(); }

    // -- 音效委托（→ ProjectileSoundAttr） --

    /** 获取音效属性 */
    public ProjectileSoundAttr getSounds() { return sounds; }

    /** 获取开火音效映射（key=RPM阈值, value=音效） */
    public Map<String, SoundEvent> getFireSounds() { return sounds.fireSounds(); }

    /** 获取停火尾音，可能为 NO_SOUND */
    public SoundEvent getCeaseFireSound() { return sounds.ceaseFireSound(); }

    /** 获取弹壳音效映射 */
    public Map<String, SoundEvent> getShellSounds() { return sounds.shellSounds(); }


    // ==================== 注册键管理 ====================

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


    // ==================== 静态查询 ====================

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


    // ==================== 开火 / 音效 / 散布 ====================

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

        List<IProjectile> projectiles = new ArrayList<>(bulletNum);
        for (int i = 0; i < bulletNum; i++) {
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


    // ==================== 嵌套类 ====================

    /**
     * 外弹道属性 — 决定投射物如何飞行。
     * <p>
     * 所有参数采用国际单位制（SI）：质量 kg、长度 m、速度 m/s、角度密位。
     * 这些字段供 {@link ProjectileManager} 的运动积分和 {@code BallisticsFramework} 外弹道解算使用。
     */
    public record ExternalProperties(
        /** 质量（kg） */
        float mass,
        /** 重力系数，1.0 = 标准重力，0 = 无重力 */
        float gravityFactor,
        /** 空气阻力系数（速度²阻力），0 = 无阻力 */
        float dragFactor,
        /** 碰撞半径（m），质点用于射线检测命中判定，刚体用于 SphereCollisionShape */
        float radius,
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
            Codec.FLOAT.optionalFieldOf("radius", 0.05f).forGetter(ExternalProperties::radius),
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
     * <p>
     * 未来可扩展字段：引信设置（fuze_settings）、爆炸半径（blast_radius）等。
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
        float blockDamageFactor
    ) {
        public static final Codec<TerminalProperties> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("base_penetration").forGetter(TerminalProperties::basePenetration),
            Codec.FLOAT.fieldOf("base_damage").forGetter(TerminalProperties::baseDamage),
            Codec.FLOAT.optionalFieldOf("penetration_velocity_coefficient", 0f)
                .forGetter(TerminalProperties::penetrationVelocityCoefficient),
            Codec.FLOAT.optionalFieldOf("damage_velocity_coefficient", 0f)
                .forGetter(TerminalProperties::damageVelocityCoefficient),
            Codec.FLOAT.optionalFieldOf("block_damage_factor", 1.0f)
                .forGetter(TerminalProperties::blockDamageFactor)
        ).apply(instance, TerminalProperties::new));
    }

    /**
     * 视觉属性 — 决定投射物在客户端如何呈现。
     * <p>
     * 整个 {@code visual} 对象在 JSON 中是可选的，缺失时使用 {@link #DEFAULT}。
     * 音效相关字段已独立为 {@link ProjectileSoundAttr}。
     * 未来可扩展字段：枪口闪光（muzzle_flash）、命中粒子（impact_particle）、弹道烟迹（ribbon_trail）等。
     */
    public record VisualProperties(
        /** 曳光颜色（RGB），不设置则无曳光效果 */
        Vec3i tracerColor,
        /** 曳光透明度，0=完全透明，255=完全不透明 */
        int tracerAlpha
    ) {
        /** 完整默认视觉属性 */
        public static final VisualProperties DEFAULT = new VisualProperties(
            new Vec3i(255, 255, 255), 200
        );

        public static final Codec<VisualProperties> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Vec3i.CODEC.optionalFieldOf("tracer_color", DEFAULT.tracerColor)
                .forGetter(VisualProperties::tracerColor),
            Codec.INT.optionalFieldOf("tracer_alpha", DEFAULT.tracerAlpha)
                .forGetter(VisualProperties::tracerAlpha)
        ).apply(instance, VisualProperties::new));
    }

    /**
     * 投射物音效属性 — 定义开火、停火、弹壳等多种音效。
     * <p>
     * 与 {@link VisualProperties} 平级，在 JSON 中通过 {@code "sounds"} 字段配置。
     * </p>
     *
     * <h3>开火音效映射 {@code fire_sounds}</h3>
     * key 为 RPM 阈值字符串（如 {@code "0.0"} 表示单发，{@code "300.0"} 表示300RPM档位），
     * value 为对应的音效事件。运行时按实际射速匹配最近的档位：
     * <ul>
     *   <li>本 burst 首发射击 → 使用 {@code "0.0"} 键的单发音效（不循环）</li>
     *   <li>后续连射 → 使用匹配的 RPM 档位循环音效，pitch 按 actualRPM / designRPM 调制</li>
     *   <li>若仅配置了 {@code "0.0"} 而无其他 key → 全程逐发播放单发音效</li>
     * </ul>
     *
     * <h3>弹壳音效映射 {@code shell_sounds}</h3>
     * 与开火音效同理，key 为 RPM 阈值字符串。
     *
     * <h3>停火音效 {@code cease_fire_sound}</h3>
     * 连射停止时播放的一次性尾音。若为 {@link #NO_SOUND} 则跳过。
     *
     * @param fireSounds      开火音效映射（key=RPM阈值, value=音效）
     * @param ceaseFireSound  停火尾音
     * @param shellSounds     弹壳音效映射
     */
    public record ProjectileSoundAttr(
        Map<String, SoundEvent> fireSounds,
        SoundEvent ceaseFireSound,
        Map<String, SoundEvent> shellSounds
    ) {
        /** 空音效占位符，表示未配置该音效 */
        public static final SoundEvent NO_SOUND = SoundEvent.createFixedRangeEvent(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty_sound"), 0
        );

        /** 默认开火音效（单发） */
        public static final SoundEvent DEFAULT_FIRE_SOUND = SoundEvent.createFixedRangeEvent(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "projectile.fire.mini"), 128f
        );

        /** 默认音效属性 */
        public static final ProjectileSoundAttr DEFAULT = new ProjectileSoundAttr(
            Map.of("0.0", DEFAULT_FIRE_SOUND),
            NO_SOUND,
            Map.of()
        );

        public static final Codec<ProjectileSoundAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, SoundEvent.DIRECT_CODEC)
                .optionalFieldOf("fire_sounds", DEFAULT.fireSounds)
                .forGetter(ProjectileSoundAttr::fireSounds),
            SoundEvent.DIRECT_CODEC.optionalFieldOf("cease_fire_sound", NO_SOUND)
                .forGetter(ProjectileSoundAttr::ceaseFireSound),
            Codec.unboundedMap(Codec.STRING, SoundEvent.DIRECT_CODEC)
                .optionalFieldOf("shell_sounds", DEFAULT.shellSounds)
                .forGetter(ProjectileSoundAttr::shellSounds)
        ).apply(instance, ProjectileSoundAttr::new));
    }
}
