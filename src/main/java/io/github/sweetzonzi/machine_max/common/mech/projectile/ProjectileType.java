package io.github.sweetzonzi.machine_max.common.mech.projectile;

import cn.solarmoon.spark_core.particle.common.IParticleAnchor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.explosion.CookoffContribution;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType;
import io.github.sweetzonzi.machine_max.common.resource.modules.ProjectileModule;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import lombok.Getter;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 投射物类型数据的抽象基类。
 * <p>
 * 对应一个 {@code projectiles/*.json} 文件，使用 Mojang Codec 从 JSON 反序列化。
 * 本类只承载**所有投射物运动模型共有**的字段（{@link #getTags() 弹药标签}、
 * {@link #getMaxLifetime() 寿命}、{@link #getBulletNum() 弹丸数}、
 * {@link #getVisual() 视觉}、{@link #getSounds() 音效}、
 * {@link #getCookoff() 发射药殉爆贡献}），并按 JSON 中的
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

    /**
     * 弹药标签列表，用于与发射器的 required_tags / acceptable_tags / forbidden_tags 匹配。
     * <p>
     * <b>显示文本不读本字段</b>：弹种的展示名取 {@link #getDisplayLabel()}（注册键的语言键）。
     * 标签是兼容性匹配与作者侧分类的载体，不是标识符到文本的映射表。
     */
    private final List<ResourceLocation> tags;

    /** 最大存活时间（秒，SI 单位），内部使用时 ×20 转为 tick */
    private final float maxLifetime;

    /** 单发弹头数（默认 1），用于表示霰弹等一次射出多个弹丸 */
    private final int bulletNum;

    /** 视觉属性 — 决定投射物在客户端如何呈现（曳光等） */
    private final VisualProperties visual;

    /** 音效属性 — 开火、停火、弹壳等音效 */
    private final ProjectileSoundAttr sounds;

    /**
     * 发射药殉爆贡献 — 每整发库存弹药被诱爆时的贡献（不含 {@code warheads} 内的战斗部）。
     * <p>
     * 本字段属于弹药根对象：它在 JSON 中与 {@code type} 平级，整块可选，缺省
     * {@link CookoffContribution#ZERO}。它独立于 {@code terminal} 的动能终效与战斗部的正常引爆
     * 参数，不从 {@code base_penetration} / {@code base_damage} 等字段继承任何值。
     * 飞行中被击毁的弹药发射药已经用于发射，不计入殉爆。
     */
    private final CookoffContribution cookoff;

    /** 注册键，由 {@link ProjectileModule} 加载时赋值（服务端与客户端各自持有独立实例） */
    private ResourceLocation registryKey;

    // ==================== 构造函数 ====================

    protected ProjectileType(
        List<ResourceLocation> tags,
        float maxLifetime,
        int bulletNum,
        VisualProperties visual,
        ProjectileSoundAttr sounds,
        CookoffContribution cookoff
    ) {
        this.tags = tags;
        this.maxLifetime = maxLifetime;
        this.bulletNum = bulletNum;
        this.visual = visual;
        this.sounds = sounds;
        this.cookoff = cookoff == null ? CookoffContribution.ZERO : cookoff;
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
    public double getTracerLightIntensity() { return visual.tracerLightIntensity(); }

    /**
     * 获取弹体模型名。
     * <p>
     * 未配置时回退为按弹种注册键查找同名模型，回退逻辑在
     * {@link BallisticProjectile#getDefaultModelIndex()}。
     *
     * @return 模型名；{@link Optional#empty()} 表示未配置
     */
    public Optional<ResourceLocation> getModel() { return visual.model(); }

    /**
     * 获取模型贴图全路径。
     *
     * @return 贴图路径；{@link Optional#empty()} 表示未配置，由模型名推导
     */
    public Optional<ResourceLocation> getTexture() { return visual.texture(); }

    // -- 音效委托（→ ProjectileSoundAttr） --

    /** 获取音效属性 */
    public ProjectileSoundAttr getSounds() { return sounds; }

    /** 获取开火音效映射（key=RPM阈值, value=音效） */
    public Map<String, SoundEvent> getFireSounds() { return sounds.fireSounds(); }

    /** 获取停火尾音，可能为 NO_SOUND */
    public SoundEvent getCeaseFireSound() { return sounds.ceaseFireSound(); }

    /** 获取弹壳音效映射 */
    public Map<String, SoundEvent> getShellSounds() { return sounds.shellSounds(); }

    // ==================== 显示文本 ====================

    /**
     * 弹种展示名，如 {@code AP}、{@code HE}、{@code ATGM}。
     * <p>
     * 文本取自本弹种自己的翻译键：{@link #getRegistryKey() 注册键} 经
     * {@link ResourceLocation#toLanguageKey()} 得到
     * {@code <命名空间>.<路径>}（{@code machine_max:50mm_pzgr39} →
     * {@code machine_max.50mm_pzgr39}，路径中的 {@code /} 原样保留），
     * 由该弹种所属的内容包在自己的 {@code lang/<locale>.json} 里给出取值。
     * 弹种的 {@link #getTags() 标签}不参与本方法。
     * <p>
     * 语言表中没有该键时返回键字符串本身（{@code machine_max.50mm_pzgr39}）。
     * 这是刻意的可见失败：内容包漏写语言键时界面直接暴露缺失的键名。
     * <p>
     * 返回值<b>仅供显示</b>，不保证唯一，不得用于比对、查找或路由。
     * <p>
     * <b>调用线程：</b>主线程（HUD 刷新，可能每帧调用）。
     * <b>仅在客户端调用</b>：服务端的语言表只含原版 {@code en_us}，内容包的键在服务端取不到取值，
     * 服务端需要弹种标识时改用 {@link #getRegistryKey()}。
     * <p>
     * 实现内部不缓存：一次 {@code toLanguageKey()} 加一次 {@link Component#translatable(String)}
     * 都是廉价操作。
     */
    public String getDisplayLabel() {
        return Component.translatable(registryKey.toLanguageKey()).getString();
    }

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
     * 枪口火光参数 — 内容包 {@code visual.muzzle_flash} 子对象。
     * <p>
     * 它只描述"这一发开火在枪口投射一颗多大、多亮、多久的屏幕空间光源"，不改变弹道、伤害、
     * 音效或枪口粒子（后者仍是 {@link VisualProperties#fireParticles()}）。开火时由发射器与
     * 调试物品在客户端登记一次，登记入口见 {@link MuzzleFlashLight}，年龄包络与遮挡策略见
     * {@code client/render/post/MuzzleFlashLightSource}。
     * <p>
     * 整块可缺省：未写 {@code muzzle_flash} 时整份取 {@link #DEFAULT}，只写部分字段时逐字段回落。
     * 颜色不是 {@link Optional}：曳光照明色要回退到 {@code tracer_color}，枪口火光没有可回退的
     * 兄弟字段，缺省色是一个固定的暖色，因此"未配置"与"配成缺省色"不需要区分。
     * <p>
     * <b>它按弹种而不是按发射器配置</b>：仓库里枪口粒子与开火音效同样按弹种配置，发射器只提供
     * locator 与开火动画名，且发射器属性里没有口径（口径只存在于 {@code external.caliber}）——
     * 按弹种配置是唯一能按口径折算半径的落点。
     *
     * @param enabled       是否产生枪口火光；false 表示该弹种开火不发光，其余字段照常解析
     * @param radius        照射半径（m）；{@link #DERIVE_RADIUS} 表示按口径折算，显式值取 {@code (0, 64]}
     * @param color         颜色，三个 {@code [0,255]} 整数
     * @param intensity     光源在 1 m 处的辐照度（{@code [0,4096]}）；0 表示该弹种开火不发光
     * @param glow          空气散射光晕的倍率（非负，无上界）：同时缩放光晕的阈值半径与亮度，
     *                      {@code 0} 表示该弹种开火只有表面光照、没有光晕
     * @param glare         十字炫光的倍率（非负，无上界）：同时缩放臂根的亮度与臂长，
     *                      {@code 0} 表示该弹种开火没有星芒
     * @param durationTicks 总寿命（游戏 tick，{@code [1,40]}），按 20 TPS 计，暂停时冻结
     */
    public record MuzzleFlash(
        boolean enabled,
        double radius,
        Vec3i color,
        float intensity,
        float glow,
        float glare,
        int durationTicks
    ) {
        /**
         * {@link #radius} 的缺省值：按弹种口径折算。
         * <p>
         * 与 {@link VisualProperties#DERIVE_LIGHT_RADIUS} 同值同义（-1 = 按口径折算），
         * 但折算式不同：枪口火光比曳光小得多，见 {@link MuzzleFlashLight#radiusFor}。
         */
        public static final double DERIVE_RADIUS = -1.0;

        /** 显式照射半径上界（m）：与曳光显式半径、爆炸火光半径取同一量级 */
        public static final double MAX_RADIUS_M = 64.0;

        /**
         * 辐照度上界（1 m 处）：与爆炸火光同一量纲。
         *
         * <p>新口径下步枪弹约 10、155 mm 炮口约 640，4 个数量级的余量留给超大口径与特效弹种，
         * 同时挡住明显的笔误（例如把 {@code 255} 当成"最大亮度"）。</p>
         */
        public static final float MAX_INTENSITY = 4096f;

        /** 寿命下界（tick）：1 tick 即一次主线程 tick 的闪光 */
        public static final int MIN_DURATION_TICKS = 1;

        /** 寿命上界（tick）：40 tick = 2 秒，已是"炮口焰"乃至"持续喷火"的量级上界 */
        public static final int MAX_DURATION_TICKS = 40;

        /**
         * 缺省枪口火光：按口径折算半径、暖白色、辐照度 90、光晕倍率 1、星芒倍率 1、持续 6 tick（≈300 ms）。
         *
         * <p><b>强度是绝对量</b>：90 对应 50 mm 档（缺省半径 9.5 m，参考距离 4.75 m 处把 0.10 的
         * 灰板抬到 0.30 所需的辐照度，见物理化设计 §4.2）。任何弹种都可以用它当起点，但真正
         * 按装药量区分的做法是在 {@code visual.muzzle_flash.intensity} 里按本节的口径写显式值：
         * 7.62 mm ≈ 10、20 mm ≈ 25、30 mm ≈ 42、50 mm ≈ 90、120 mm ≈ 400、155 mm ≈ 640。</p>
         *
         * <p><b>标定目标是"夜里打一发就看得见"</b>：6 tick 让它在画面上停留 3–4 帧（更短的值只覆盖
         * 1–2 帧，肉眼容易漏掉），同时不至于久留成常亮。{@code (1-u)²} 包络使后半程快速变暗，
         * 因此实际可辨时长约一半。连射时多条登记会重叠（见 {@link MuzzleFlashLight} 的
         * "每 tick 至多一条"），表现为持续光而不是逐发频闪。</p>
         */
        public static final MuzzleFlash DEFAULT =
            new MuzzleFlash(true, DERIVE_RADIUS, new Vec3i(255, 200, 120), 90f, 1f, 1f, 6);

        /** {@link #radius} 的校验：接受缺省哨兵，或 {@code (0, 64]} 内的有限正数。 */
        private static final Codec<Double> RADIUS_CODEC = Codec.DOUBLE.validate(value -> {
            if (value == DERIVE_RADIUS) return DataResult.success(value);
            if (!Double.isFinite(value) || value <= 0.0 || value > MAX_RADIUS_M) {
                return DataResult.error(() -> "muzzle_flash.radius 必须是 " + DERIVE_RADIUS
                    + "（按口径折算）或 (0, " + MAX_RADIUS_M + "] m 内的正数，实际为 " + value);
            }
            return DataResult.success(value);
        });

        /** {@link #intensity} 的校验：有限且落在 {@code [0, 8]}。 */
        private static final Codec<Float> INTENSITY_CODEC = Codec.FLOAT.validate(value -> {
            if (!Float.isFinite(value)) {
                return DataResult.error(() -> "muzzle_flash.intensity 必须是有限数，实际为 " + value);
            }
            if (value < 0f || value > MAX_INTENSITY) {
                return DataResult.error(() -> "muzzle_flash.intensity 必须落在 [0, "
                    + MAX_INTENSITY + "] 内，实际为 " + value);
            }
            return DataResult.success(value);
        });

        /**
         * 非负的浮点字段：没有上界，只挡非有限值与负值。
         *
         * <p>光晕与星芒的倍率都只有"关闭"（{@code 0}）与"放大"两端，物理上没有统一的最大值：
         * 光晕倍率超过 {@code 32 m / (GlowScale·sqrt(intensity))} 后阈值半径被半径上限截断、
         * 只剩亮度继续增长；星芒的臂长超过屏高的一个固定比例后按像素封顶。两者的亮度都由
         * 着色器的 {@code GLOW_MAX} 兜住，因此写多大的倍率都是观感选择。</p>
         */
        private static Codec<Float> nonNegative(String field) {
            return Codec.FLOAT.validate(value -> {
                if (!Float.isFinite(value)) {
                    return DataResult.error(() -> field + " 必须是有限数，实际为 " + value);
                }
                if (value < 0f) {
                    return DataResult.error(() -> field + " 必须为非负数，实际为 " + value);
                }
                return DataResult.success(value);
            });
        }

        /** {@link #glow} 的校验：有限且非负，没有上界。 */
        private static final Codec<Float> GLOW_CODEC = nonNegative("muzzle_flash.glow");

        /** {@link #glare} 的校验：有限且非负，没有上界。 */
        private static final Codec<Float> GLARE_CODEC = nonNegative("muzzle_flash.glare");

        /** {@link #durationTicks} 的校验：整数且落在 {@code [1, 40]}。 */
        private static final Codec<Integer> DURATION_CODEC = Codec.INT.validate(value -> {
            if (value < MIN_DURATION_TICKS || value > MAX_DURATION_TICKS) {
                return DataResult.error(() -> "muzzle_flash.duration_ticks 必须落在 ["
                    + MIN_DURATION_TICKS + ", " + MAX_DURATION_TICKS + "] tick 内，实际为 " + value);
            }
            return DataResult.success(value);
        });

        public static final Codec<MuzzleFlash> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled)
                .forGetter(MuzzleFlash::enabled),
            RADIUS_CODEC.optionalFieldOf("radius", DEFAULT.radius)
                .forGetter(MuzzleFlash::radius),
            Vec3i.CODEC.optionalFieldOf("color", DEFAULT.color)
                .forGetter(MuzzleFlash::color),
            INTENSITY_CODEC.optionalFieldOf("intensity", DEFAULT.intensity)
                .forGetter(MuzzleFlash::intensity),
            GLOW_CODEC.optionalFieldOf("glow", DEFAULT.glow)
                .forGetter(MuzzleFlash::glow),
            GLARE_CODEC.optionalFieldOf("glare", DEFAULT.glare)
                .forGetter(MuzzleFlash::glare),
            DURATION_CODEC.optionalFieldOf("duration_ticks", DEFAULT.durationTicks)
                .forGetter(MuzzleFlash::durationTicks)
        ).apply(instance, MuzzleFlash::new));
    }

    /**
     * 视觉属性 — 决定投射物在客户端如何呈现。
     * <p>
     * 整个 {@code visual} 对象在 JSON 中是可选的，缺失时使用 {@link #DEFAULT}。
     * 音效相关字段已独立为 {@link ProjectileSoundAttr}。
     * <p>
     * 曳光与模型是两条各自独立的呈现通路：{@code tracer_*} 一组的载体是 {@code ClientProjectileRenderer}
     * 画出的线段，{@link #model} / {@link #texture} 一组的载体是 {@code ProjectileEntityRenderer}
     * 遍历骨骼画出的立体弹体。两者可以同时存在，也可以各自单独存在。
     * <p>
     * 未来可扩展字段：命中粒子（impact_particle）、弹道烟迹（ribbon_trail）等。
     */
    public record VisualProperties(
        /** 曳光颜色（RGB），不设置则无曳光效果 */
        Vec3i tracerColor,
        /** 曳光透明度，0=完全透明，255=完全不透明 */
        int tracerAlpha,
        /**
         * 曳光线宽（像素）。默认 2.0。
         * 值存入 {@link net.minecraft.client.renderer.RenderStateShard.LineStateShard}，
         * 通过 OpenGL glLineWidth 控制 LINES 模式下的线宽。
         */
        double tracerWidth,
        /**
         * 拖尾长度乘子（无量纲）。实际拖尾长度 = 当前速度 × 此值（米）。默认 0.05。
         */
        double tracerLength,
        /**
         * 曳光光源在 1 m 处的辐照度（可选，默认 3.0）。最终强度还要乘透明度因子与超时淡出。
         * <p>
         * 它是绝对量而不是叠加倍率：数值越大，同一距离上被照亮的表面越亮。缺省 3.0 约等于
         * 7.62 mm 步枪弹（半径 3.15 m，参考距离 1.58 m 处把 0.06 的灰板抬到 0.12）；大口径弹种
         * 写显式值，首轮值见 {@code docs/plan/进行中/渲染-屏幕空间光照物理化设计.md} §4.2。
         * <p>
         * 强度与透明度各自承担不同的量：透明度是曳光线自己的可见度，而大口径弹种可以用较低的
         * 透明度（曳光线更暗）配更高的辐照度（闪光更强）。见
         * {@code docs/plan/计划中/武器系统-投射物曳光光照设计.md} §11.1。
         */
        double tracerLightIntensity,
        /**
         * 曳光光源的照射半径（m，可选）。缺省 {@link #DERIVE_LIGHT_RADIUS} 表示按
         * {@link BallisticProjectileType.ExternalProperties#caliberMm} 折算
         * （{@code min(3 + 0.02 × 口径mm, 8)} m），既有弹种因此不必配置本字段。
         * <p>
         * 显式取值不受折算式的 8 m 上限约束（上限改为整条链的 {@value #MAX_LIGHT_RADIUS_M} m）：
         * 照明弹一类需要几十米照射范围的光源正是靠它表达的。取值须为
         * {@link #DERIVE_LIGHT_RADIUS} 或 {@code (0, }{@value #MAX_LIGHT_RADIUS_M}{@code ]} 内的正数，
         * 越界在内容包加载期报错。
         */
        double tracerLightRadius,
        /**
         * 曳光光源颜色（可选）。缺省（{@link Optional#empty()}）回退到 {@link #tracerColor}：
         * 光是曳光自己发的，同色是常规情形；需要"曳光线与照明颜色不同"时（暖色弹道曳光配白色照明）
         * 才显式配置。
         */
        Optional<Vec3i> tracerLightColor,
        /**
         * 曳光光源的独立开关（可选）。缺省（{@link Optional#empty()}）沿用"曳光线可见才发光"，
         * 即 {@code tracerAlpha > 0}。
         * <p>
         * 显式设为 {@code true} 后，发光不再要求曳光线可见：透明度为 0 时曳光线整条不画，但光斑
         * 照常以 {@code tracerLightIntensity} 的完整强度照亮周围。照明弹一类"只有光、没有弹道线"
         * 的来源用它；显式设为 {@code false} 则关掉该弹种的光而不影响它的曳光线。
         */
        Optional<Boolean> tracerLightEnabled,
        /**
         * 曳光光源的空气散射光晕倍率（可选，默认 1.0，非负、无上界）。
         * <p>
         * 倍率同时缩放光晕的阈值半径与亮度，{@code 0} 表示该弹种只有表面光照、没有光晕。
         * 阈值半径按 {@link #tracerLightIntensity} 派生（见物理化设计 §3.6），本字段只做放大：
         * 曳光的辐照度比信号弹低两三个数量级，缺省值下光晕的峰值只有千分之一量级，实际看不见。
         * 要让曳光带可见光晕，把倍率写到 10 以上（7.62 mm 在 10 m 处约 0.04 的线性亮度），
         * 倍率再大则阈值半径先撞 32 m 上限、只剩亮度增长。
         */
        double tracerGlow,
        /**
         * 曳光光源的十字炫光倍率（可选，默认 1.0，非负、无上界）。
         * <p>
         * 倍率同时缩放臂根亮度与臂长，{@code 0} 表示该弹种没有星芒。星芒的驱动量是相机处的
         * 表观照度 {@code I/d²}，所以曳光只有在贴近相机掠过时才会画出来（10 m 处约 0.2 的线性
         * 亮度），远距离弹道不会被画成一串星芒；嫌它吵就把本字段写成 0。
         */
        double tracerGlare,
        /**
         * 枪口火光 — 该弹种开火时在枪口投射的屏幕空间光源（可选子对象）。
         * <p>
         * 缺省 {@link MuzzleFlash#DEFAULT}：开启、按口径折算半径、暖白、辐照度 90、光晕倍率 1、6 tick。
         * 意思是"任何有口径的弹种开火都会闪一下"，想关掉的弹种写 {@code muzzle_flash.enabled = false}。
         * <p>
         * 它按弹种而不是按发射器配置，理由与 {@code fire_particles}、{@code fire_sounds} 相同：
         * 发射器只提供 locator，真正决定火焰大小的是装药量，而口径是它在数据里的唯一载体。
         */
        MuzzleFlash muzzleFlash,
        /** 开火粒子效果ID列表，在客户端依次播放枪口火焰/炮口焰等效果 */
        List<ResourceLocation> fireParticles,
        /**
         * 弹体模型名 — {@code models/projectile/} 下的文件名去掉 {@code .json}，如
         * {@code machine_max:9m133.geo}。
         * <p>
         * 缺省（{@link Optional#empty()}）时回退到"按弹种注册键查找同名模型"，因此内容包把模型文件
         * 命名成弹种名即可零配置接入；显式配置则可以让多个弹种共用一个模型，或指向任意名字的模型。
         */
        Optional<ResourceLocation> model,
        /**
         * 模型贴图全路径，如 {@code machine_max:textures/projectile/9m133.png}。
         * <p>
         * 缺省时按 {@code textures/projectile/<model 的路径>.png} 推导。推导只把模型名里的
         * {@code .geo} 当作普通名字的一部分，因此贴图文件名与模型名不一致时必须显式配置本字段。
         */
        Optional<ResourceLocation> texture
    ) {
        /**
         * {@link #tracerLightRadius} 的缺省值：按弹种口径折算照射半径。
         * <p>
         * 它是一个哨兵值而不是"0 表示缺省"：0 是有意义的半径（影响球退化为一个点，实际不产生贡献），
         * 用 0 兼任缺省会让"作者显式想关掉光"与"作者没写这个字段"无法区分。
         */
        public static final double DERIVE_LIGHT_RADIUS = -1.0;

        /** 渲染侧可接受的照射半径上界（m），与爆炸火光的字段边界取同一个量级 */
        public static final double MAX_LIGHT_RADIUS_M = 64.0;

        /**
         * 非负的浮点字段：没有上界，只挡非有限值与负值。
         *
         * <p>曳光的辐照度比信号弹低两三个数量级，要让它的光晕可见就得把倍率写到远高于 4；
         * 倍率超过 {@code 32 m / (GlowScale·sqrt(tracer_light_intensity))} 后阈值半径被半径上限
         * 截断，只剩亮度继续增长，而亮度又由着色器的 {@code GLOW_MAX} 兜住。星芒同理：臂长
         * 撞到屏高比例的封顶后只剩亮度增长。</p>
         */
        private static Codec<Double> nonNegative(String field) {
            return Codec.DOUBLE.validate(value -> {
                if (!Double.isFinite(value)) {
                    return DataResult.error(() -> field + " 必须是有限数，实际为 " + value);
                }
                if (value < 0.0) {
                    return DataResult.error(() -> field + " 必须为非负数，实际为 " + value);
                }
                return DataResult.success(value);
            });
        }

        /** {@link #tracerGlow} 的校验：有限且非负，没有上界。 */
        private static final Codec<Double> GLOW_CODEC = nonNegative("tracer_glow");

        /** {@link #tracerGlare} 的校验：有限且非负，没有上界。 */
        private static final Codec<Double> GLARE_CODEC = nonNegative("tracer_glare");

        /**
         * {@link #tracerLightRadius} 的校验：接受缺省哨兵，或 {@code (0, 64]} 内的有限正数。
         */
        private static final Codec<Double> LIGHT_RADIUS_CODEC = Codec.DOUBLE.validate(value -> {
            if (value == DERIVE_LIGHT_RADIUS) return DataResult.success(value);
            if (!Double.isFinite(value) || value <= 0.0 || value > MAX_LIGHT_RADIUS_M) {
                return DataResult.error(() -> "tracer_light_radius 必须是 " + DERIVE_LIGHT_RADIUS
                        + "（按口径折算）或 (0, " + MAX_LIGHT_RADIUS_M + "] m 内的正数，实际为 " + value);
            }
            return DataResult.success(value);
        });

        /** 完整默认视觉属性 */
        public static final VisualProperties DEFAULT = new VisualProperties(
            new Vec3i(255, 255, 255), 200,
            2.0, 0.02,
            3.0,
            DERIVE_LIGHT_RADIUS,
            Optional.empty(),
            Optional.empty(),
            1.0,
            1.0,
            MuzzleFlash.DEFAULT,
            List.of(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "fire_medium")),
            Optional.empty(),
            Optional.empty()
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
            Codec.DOUBLE.optionalFieldOf("tracer_light_intensity", DEFAULT.tracerLightIntensity)
                .forGetter(VisualProperties::tracerLightIntensity),
            LIGHT_RADIUS_CODEC.optionalFieldOf("tracer_light_radius", DEFAULT.tracerLightRadius)
                .forGetter(VisualProperties::tracerLightRadius),
            Vec3i.CODEC.optionalFieldOf("tracer_light_color")
                .forGetter(VisualProperties::tracerLightColor),
            Codec.BOOL.optionalFieldOf("tracer_light_enabled")
                .forGetter(VisualProperties::tracerLightEnabled),
            GLOW_CODEC.optionalFieldOf("tracer_glow", DEFAULT.tracerGlow)
                .forGetter(VisualProperties::tracerGlow),
            GLARE_CODEC.optionalFieldOf("tracer_glare", DEFAULT.tracerGlare)
                .forGetter(VisualProperties::tracerGlare),
            MuzzleFlash.CODEC.optionalFieldOf("muzzle_flash", MuzzleFlash.DEFAULT)
                .forGetter(VisualProperties::muzzleFlash),
            Codec.list(ResourceLocation.CODEC).optionalFieldOf("fire_particles", DEFAULT.fireParticles)
                .forGetter(VisualProperties::fireParticles),
            ResourceLocation.CODEC.optionalFieldOf("model")
                .forGetter(VisualProperties::model),
            ResourceLocation.CODEC.optionalFieldOf("texture")
                .forGetter(VisualProperties::texture)
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
