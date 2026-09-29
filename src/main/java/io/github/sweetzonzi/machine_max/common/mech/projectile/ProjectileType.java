package io.github.sweetzonzi.machine_max.common.mech.projectile;

import cn.solarmoon.spark_core.particle.common.IParticleAnchor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType;
import io.github.sweetzonzi.machine_max.common.resource.modules.ProjectileModule;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import lombok.Getter;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

/**
 * 投射物类型数据的抽象基类。
 * <p>
 * 对应一个 {@code projectiles/*.json} 文件，使用 Mojang Codec 从 JSON 反序列化。
 * 本类只承载**所有投射物运动模型共有**的字段（{@link #getTags() 弹药标签}、
 * {@link #getMaxLifetime() 寿命}、{@link #getBulletNum() 弹丸数}、
 * {@link #getVisual() 视觉}、{@link #getSounds() 音效}），并按 JSON 中的
 * {@code "type"} 字段经 {@link #CODEC dispatch} 分派到具体子类。
 * <p>
 * 运动模型专属字段（外弹道、终点效应等）下沉到子类，见 {@link BallisticProjectileType}。
 * <p>
 * 所有弹道参数通过 {@link ProjectileModule} 加载至 {@link MMDynamicRes}，
 * 运行时由 {@link BallisticProjectile#getProjectileType()} 获取。
 * <p>
 * 为向后兼容，平铺字段和所有嵌套字段的属性都提供委托 getter（如
 * {@link #getTracerColor()}、{@link #getFireSounds()} 等），调用方无需改动。
 * <p>
 * 字段语义参见设计文档《武器系统-组件化投射物与类型体系设计》§二、§三。
 * 速度-伤害模型见 {@link BallisticProjectile} 中的幂函数实现。
 */
@Getter
public abstract class ProjectileType {

    // ==================== 共享字段 ====================

    /** 弹药标签列表，用于与发射器的 required_tags / acceptable_tags / forbidden_tags 匹配 */
    private final List<ResourceLocation> tags;

    /** 最大存活时间（秒，SI 单位），内部使用时 ×20 转为 tick */
    private final float maxLifetime;

    /** 单发弹头数（默认 1），用于表示霰弹等一次射出多个弹丸 */
    private final int bulletNum;

    /** 视觉属性 — 决定投射物在客户端如何呈现（曳光等） */
    private final VisualProperties visual;

    /** 音效属性 — 开火、停火、弹壳等音效 */
    private final ProjectileSoundAttr sounds;

    /** 注册键，由 {@link ProjectileModule} 加载时赋值（服务端与客户端各自持有独立实例） */
    private ResourceLocation registryKey;

    // ==================== 构造函数 ====================

    protected ProjectileType(
        List<ResourceLocation> tags,
        float maxLifetime,
        int bulletNum,
        VisualProperties visual,
        ProjectileSoundAttr sounds
    ) {
        this.tags = tags;
        this.maxLifetime = maxLifetime;
        this.bulletNum = bulletNum;
        this.visual = visual;
        this.sounds = sounds;
    }

    // ==================== 类型分派 ====================

    /**
     * JSON 中的类型名。当前只有 {@code "ballistic"} 一种取值——它标识
     * "SoA 积分 + 射线检测"这条运动模型；未来的光束族（{@code "pulse"} /
     * {@code "beam"}）会在 {@link #CODEC} 中新增分支，届时才有对应的取值。
     * <p>
     * 既是 {@link #CODEC dispatch} 的路由键，也是编码时写回 {@code "type"} 字段的值。
     *
     * @return 序列化类型名
     */
    public abstract String getSerializedName();

    /**
     * Mojang Codec：按 {@code "type"} 字段分派到具体子类的 Codec。
     * <p>
     * 分派表当前只注册 {@code ballistic}（路由到 {@link BallisticProjectileType}）；
     * 未注册的名字（如 {@code pulse} / {@code beam}）会在此处抛出异常，
     * 由 {@link ProjectileModule} 捕获并记录为内容包加载错误。
     */
    public static final Codec<ProjectileType> CODEC = Codec.STRING.dispatch(
        "type",
        ProjectileType::getSerializedName,
        name -> switch (name) {
            case "ballistic" -> BallisticProjectileType.CODEC;
            default -> throw new IllegalArgumentException("未知投射物类型: " + name);
        }
    );

    // ==================== 委托方法 — 向后兼容旧调用方 ====================

    /** 最大存活 tick 数（由秒换算，×20） */
    public int getMaxLifetimeTicks() {
        return (int)(maxLifetime * 20);
    }

    // -- 视觉委托（→ VisualProperties） --

    public Vec3i getTracerColor() { return visual.tracerColor(); }
    public int getTracerAlpha() { return visual.tracerAlpha(); }
    public double getTracerWidth() { return visual.tracerWidth(); }
    public double getTracerLength() { return visual.tracerLength(); }

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

    // ==================== 开火粒子特效 ====================

    /**
     * 播放绑定到定位器锚点的持久开火粒子特效列表。
     * 每种粒子效果均创建独立发射器实例，每 tick 从锚点轮询 locator 位姿以跟随移动。
     * <p>
     * 使用 {@link VisualProperties#fireParticles()} 中配置的粒子效果 ID 列表。
     * <p>
     * <b>调用线程：</b>主线程（渲染线程/客户端 tick）。
     * <b>仅在客户端调用。</b>
     *
     * @param level       维度（客户端）
     * @param anchor      定位器锚点（如 {@code LauncherSubsystem}）
     * @param locatorName 定位器名称（如 {@code "muzzle"}）
     */
    public void playFireEffect(Level level, IParticleAnchor anchor, String locatorName) {
        for (ResourceLocation particleId : visual.fireParticles()) {
            anchor.playEffect(level, particleId, locatorName);
        }
    }

    // ==================== 嵌套类（共享属性） ====================

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
        int tracerAlpha,
        /**
         * 曳光线宽（像素）。默认 2.0，与旧版行为一致。
         * 值存入 {@link net.minecraft.client.renderer.RenderStateShard.LineStateShard}，
         * 通过 OpenGL glLineWidth 控制 LINES 模式下的线宽。
         */
        double tracerWidth,
        /**
         * 拖尾长度乘子（无量纲）。实际拖尾长度 = 当前速度 × 此值（米）。
         * 默认 0.05 对应旧版行为。
         */
        double tracerLength,
        /** 开火粒子效果ID列表，在客户端依次播放枪口火焰/炮口焰等效果 */
        List<ResourceLocation> fireParticles
    ) {
        /** 完整默认视觉属性 */
        public static final VisualProperties DEFAULT = new VisualProperties(
            new Vec3i(255, 255, 255), 200,
            2.0, 0.02,
            List.of(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "fire_medium"))
        );

        public static final Codec<VisualProperties> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Vec3i.CODEC.optionalFieldOf("tracer_color", DEFAULT.tracerColor)
                .forGetter(VisualProperties::tracerColor),
            Codec.INT.optionalFieldOf("tracer_alpha", DEFAULT.tracerAlpha)
                .forGetter(VisualProperties::tracerAlpha),
            Codec.DOUBLE.optionalFieldOf("tracer_width", DEFAULT.tracerWidth)
                .forGetter(VisualProperties::tracerWidth),
            Codec.DOUBLE.optionalFieldOf("tracer_length", DEFAULT.tracerLength)
                .forGetter(VisualProperties::tracerLength),
            Codec.list(ResourceLocation.CODEC).optionalFieldOf("fire_particles", DEFAULT.fireParticles)
                .forGetter(VisualProperties::fireParticles)
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
