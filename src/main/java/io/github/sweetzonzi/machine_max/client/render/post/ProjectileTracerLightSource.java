package io.github.sweetzonzi.machine_max.client.render.post;

import io.github.sweetzonzi.machine_max.client.MMClientConfig;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * 投射物曳光光源来源：把 {@link ProjectileManager} 的 SoA 数组里满足门禁的每一发曳光
 * 提交进通用光源表。
 *
 * <p>门禁与 {@code ClientProjectileRenderer} 完全相同：<b>看类型的曳光配置与对象能否取到，
 * 不看 {@code alive}</b>——死条目由 {@code deadRetained} 多留一次清理扫描，这一帧仍要画出曳光，
 * 光照若按 {@code alive} 早退，那一发就只剩曳光线、没有光。只有 {@code tracer_alpha > 0} 的弹种
 * 会照亮，颜色取自曳光配置。</p>
 *
 * <p>强度在提交前乘好的因子是内容强度（弹种 {@code tracer_light_intensity}）、类别倍率
 * （{@code MMClientConfig} 的曳光强度）与时间包络（剩余寿命淡出、曳光自身 alpha）；全局倍率
 * 由 {@link ScreenSpaceLightBuffer} 统一乘且只乘一次，合计每项各乘一次。</p>
 *
 * <p>三条剔除留在本类，不进入光源表的公共剔除：光心在相机背后、光心距离超过渲染距离、
 * 屏幕投影半径小于下限。这样容量表里的候选与拆出通用通路之前的曳光链逐一对应
 * （设计文档 §4.3、§7）。</p>
 *
 * <p>设计文档：{@code docs/plan/进行中/渲染-屏幕空间光照通用化设计.md} §7；
 * 落地前的完整设计：{@code docs/plan/计划中/武器系统-投射物曳光光照设计.md}。</p>
 */
public class ProjectileTracerLightSource implements ScreenSpaceLightSource {

    // ===== 数值策略常量（改这些不需要重编着色器）=====

    /**
     * 照亮半径的基准值（m）：口径折算的常数项。
     *
     * <p>照射半径由弹种口径线性折算：{@code R = 基准 + 每毫米口径增量 × 口径(mm)}。
     * 中心处的衰减值恒为 1（{@code (1 - d/R)²} 在 d=0 处为 1），因此 R 只决定“够得着多远”、
     * 不决定峰值亮度：R 越大，光斑越大越柔。</p>
     *
     * <p>基准 3 m 是按“曳光在常见高度上要有可见落点”定的：步枪弹在 1.7 m 高度平飞时，
     * 正下方地面的衰减值 {@code (1 − 1.7/3.15)² ≈ 0.21}，看得见但不刺眼；半径为 1.5 m 时该值归零，
     * 也就是脚下完全不亮、只有擦到车体或墙面才见光。想更亮就抬基准与斜率（两者一起抬，
     * 否则大小口径的差距会被压平）。</p>
     */
    private static final float LIGHT_RADIUS_BASE_M = 3.0f;
    /** 每毫米口径增加的照亮半径（m）：2 cm/mm */
    private static final float LIGHT_RADIUS_PER_MM_M = 0.02f;
    /** 照亮半径上限（m）：避免超大口径变成探照灯 */
    private static final float LIGHT_RADIUS_MAX_M = 8.0f;

    /**
     * 遮挡容差的绝对下限（m）：口径折算的常数项。
     *
     * <p>容差的物理下界是“弹体自身几何在视线方向上的半长”——投射物模型自己写深度缓冲
     * （{@code RenderType.entityTranslucent} 缺省写深度），因此在光源中心处采到的深度可能是
     * 模型前表面、比光心更近。容差小于这个量就会把光源自己剔掉、光凭空消失。弹体细长比约 5、
     * 半长约为口径的 2.5 倍，再加上模型原点偏移与深度量化的余量，取“每毫米口径 2 mm + 0.20 m”。</p>
     */
    private static final float OCCLUSION_TOL_BASE_M = 0.20f;
    /** 每毫米口径增加的遮挡容差（m）：2 mm/mm */
    private static final float OCCLUSION_TOL_PER_MM_M = 0.002f;
    /** 遮挡容差上限（m）：容差越大越容易从近处遮挡物漏光 */
    private static final float OCCLUSION_TOL_MAX_M = 1.0f;

    /** 超时淡出起点（tick）：与 {@code ClientProjectileRenderer} 的曳光淡出包络一致 */
    private static final int FADE_TICKS = 20;
    /** 参与光照的屏幕投影半径下限（px）：更小的光斑在 8 位色深下已不可辨识 */
    private static final float MIN_SCREEN_RADIUS_PX = 4.0f;

    private static final ResourceLocation SOURCE_ID =
            ResourceLocation.parse("machine_max:projectile_tracer");

    /** 逐发复用的提交载体与结果复用对象，采集路径不产生与光源数成正比的分配 */
    private final ScreenSpaceLight light = new ScreenSpaceLight();
    private final Vector3f viewPos = new Vector3f();
    private final Vector3f worldPos = new Vector3f();
    private final Vector3f colorScratch = new Vector3f();

    @Override
    public ResourceLocation id() {
        return SOURCE_ID;
    }

    @Override
    public int priority() {
        return ScreenSpaceLightPriority.PROJECTILE_TRACER;
    }

    /**
     * 渲染线程：遍历本维度的投射物数组，逐发提交候选光源。
     *
     * <p>总开关（曳光类别开关）关闭时不提交任何条目，本帧的光照链因此没有候选、不加载也不绘制。</p>
     */
    @Override
    public void collect(LightFrameContext frame, ScreenSpaceLightBuffer out) {
        if (!MMClientConfig.isTracerLightEnabled()) return;

        Level level = frame.level();
        if (level == null) return;
        ProjectileManager pm = ObjectManager.levelProjectileManagers.get(level);
        if (pm == null) return;

        float focalPx = frame.focalPx();
        if (focalPx <= 0f) return;

        float maxDistance = frame.renderDistance();
        float partialTick = frame.partialTick();
        float categoryIntensity = MMClientConfig.getTracerLightIntensity();

        for (int i = 0; i < pm.count; i++) {
            // 先用带边界检查的访问器过一道：SoA 的类型索引可能越界，而 getProjectileTypeByIndex
            // 取的是 typeCache[typeIndex[i]]、自身不做检查（它假定调用方已确认索引有效）
            if (pm.getTypeKeyByIndex(i) == null) continue;
            BallisticProjectileType type = pm.getProjectileTypeByIndex(i);
            if (type == null) continue;

            // 门禁：曳光不可见的弹种不发光（与渲染器同一条判据）
            if (type.getTracerAlpha() <= 0) continue;

            DestroyableObject obj = pm.getProjectile(pm.objId[i]);
            if (obj == null) continue;

            // 与 ClientProjectileRenderer 逐字相同的取位姿调用，保证光斑与曳光线同源
            obj.getWorldPositionMatrix(partialTick).getTranslation(worldPos);
            BlastProjectionUtil.toViewSpace(frame.modelViewMatrix(), frame.cameraPos(), worldPos, viewPos);

            float frontDepth = -viewPos.z;
            if (frontDepth <= 0f) continue; // 光心在相机背后：屏幕投影会翻转

            float caliberMm = type.getCaliber();
            float radius = lightRadius(caliberMm);

            // 屏幕半径剔除：投影半径小于下限的光源贡献不可辨识
            float radiusPx = focalPx * radius / frontDepth;
            if (radiusPx < MIN_SCREEN_RADIUS_PX) continue;

            // 视空间长度等于世界距离，可直接当作“光心到相机的距离”用
            if (viewPos.length() > maxDistance) continue;

            float intensity = (float) type.getTracerLightIntensity() * categoryIntensity
                    * (type.getTracerAlpha() / 255f);
            if (pm.lifetime[i] < FADE_TICKS) {
                intensity *= pm.lifetime[i] / (float) FADE_TICKS;
            }

            Vec3i color = type.getTracerColor();
            colorScratch.set(color.getX() / 255f, color.getY() / 255f, color.getZ() / 255f);
            light.setPoint(viewPos, radius, colorScratch, intensity,
                    ScreenSpaceLight.OcclusionMode.SOURCE_DEPTH, occlusionTolerance(caliberMm), 0f);
            out.submit(light);
        }
    }

    /**
     * 照亮半径：{@code R = 3 m + 2 cm × 口径(mm)}，上限 8 m。
     *
     * <p>口径是“弹丸有多大”这件事在数据里的唯一载体（{@code external.caliber}，单位 mm），
     * 物理半径 = 口径/2000 m、命中半径与风阻截面积都由它换算，因此照射半径也由它线性折算，
     * 不需要新增内容包字段：7.62mm → 3.15 m，20mm → 3.40 m，50mm → 4.00 m，155mm → 6.10 m。</p>
     *
     * @param caliberMm 弹种口径（mm）
     * @return 世界半径（m）
     */
    private static float lightRadius(float caliberMm) {
        if (caliberMm <= 0f) return 0f;
        return Math.min(LIGHT_RADIUS_BASE_M + LIGHT_RADIUS_PER_MM_M * caliberMm, LIGHT_RADIUS_MAX_M);
    }

    /**
     * 遮挡容差的绝对分量：{@code 0.20 m + 2 mm × 口径(mm)}，上限 1 m。
     *
     * <p>着色器里的判据是“光源屏幕位置处的场景深度 < 光源前向深度 − 容差 → 本帧跳过该光源”，
     * 容差太小时投射物模型会把自己剔掉（模型写深度缓冲），太大时近处遮挡物漏光，
     * 因此按弹体的细长比与口径线性折算。见 {@link #OCCLUSION_TOL_BASE_M}。</p>
     *
     * @param caliberMm 弹种口径（mm）
     * @return 容差的绝对分量（m）
     */
    private static float occlusionTolerance(float caliberMm) {
        return Math.min(OCCLUSION_TOL_BASE_M + OCCLUSION_TOL_PER_MM_M * caliberMm, OCCLUSION_TOL_MAX_M);
    }
}
