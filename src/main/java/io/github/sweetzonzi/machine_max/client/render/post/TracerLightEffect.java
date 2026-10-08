package io.github.sweetzonzi.machine_max.client.render.post;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.MMClientConfig;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType;
import io.github.sweetzonzi.machine_max.mixin.PostChainAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * 投射物曳光光照后处理（屏幕空间逐像素光照）。
 *
 * <p>以每发曳光为球心、按世界空间距离衰减，把光加到<b>已经画好的画面</b>上。之所以必须是
 * "读画面"的后处理而不是世界内加法几何（光晕广告牌），是因为只有逐像素方案才拿得到世界空间
 * 距离、遮挡与法线响应；广告牌只能拿屏幕距离当世界距离的代理，光斑形状在被照表面与光心深度差
 * 很大时会明显不对（投射物低空掠过地面、相机俯视时最明显）。</p>
 *
 * <p>每帧的数据装配全部是 CPU 侧标量运算：遍历 {@link ProjectileManager} 的 SoA 数组，
 * 逐发做门禁与剔除，把视空间光心、世界半径、颜色、强度与遮挡容差写进自持的扁平数组，
 * 一次性交给数组 uniform。光源几何只依赖两个量——光心的世界位置与半径：
 * 颜色取曳光自己的 `tracer_color`、强度取可选字段 `tracer_light_intensity`，再乘上曳光自身已有的
 * 透明度与超时淡出（{@link #FADE_TICKS}），保证光斑与曳光线同步淡出、不出现"线还在、光先走"。</p>
 *
 * <p><b>深度</b>：每帧在 {@code AFTER_LEVEL} 把主目标的世界深度拷进链内声明的快照目标，
 * 着色器从快照采样。更晚就没有世界深度了（手部渲染前的那次深度清除无光影下会把主目标深度
 * 整体写成远平面），详见 {@code render/post/AGENTS.md}。无候选光源的帧不拷、不提交 pass，
 * 因此绝大多数帧（没有曳光在飞）零渲染开销。</p>
 *
 * <p>设计文档：{@code docs/plan/计划中/武器系统-投射物曳光光照设计.md}。</p>
 */
public class TracerLightEffect {

    // ===== 数值策略常量（改这些不需要重编着色器）=====

    /** 同屏处理的光源槽位上限 */
    private static final int MAX_LIGHTS = 16;
    /** 参与光照的屏幕投影半径下限（px）：更小的光斑在 8 位色深下已不可辨识 */
    private static final float MIN_SCREEN_RADIUS_PX = 4.0f;

    /**
     * 照亮半径的基准值（m）：口径折算的常数项。
     *
     * <p>照射半径由弹种口径线性折算：{@code R = 基准 + 每毫米口径增量 × 口径(mm)}。
     * 中心处的衰减值恒为 1（{@code (1 - d/R)²} 在 d=0 处为 1），因此 R 只决定"够得着多远"、
     * 不决定峰值亮度：R 越大，光斑越大越柔。基准 1.5 m 保证小口径曳光贴地/贴车飞过时也有可见光斑。</p>
     */
    private static final float LIGHT_RADIUS_BASE_M = 1.5f;
    /** 每毫米口径增加的照亮半径（m）：1 cm/mm */
    private static final float LIGHT_RADIUS_PER_MM_M = 0.01f;
    /** 照亮半径上限（m）：避免超大口径变成探照灯 */
    private static final float LIGHT_RADIUS_MAX_M = 4.0f;

    /**
     * 遮挡容差的绝对下限（m）：口径折算的常数项。
     *
     * <p>容差的物理下界是"弹体自身几何在视线方向上的半长"——投射物模型自己写深度缓冲
     * （{@code RenderType.entityTranslucent} 缺省写深度），因此在光源中心处采到的深度可能是
     * 模型前表面、比光心更近。容差小于这个量就会把光源自己剔掉、光凭空消失。弹体细长比约 5、
     * 半长约为口径的 2.5 倍，再加上模型原点偏移与深度量化的余量，取"每毫米口径 2 mm + 0.20 m"。</p>
     */
    private static final float OCCLUSION_TOL_BASE_M = 0.20f;
    /** 每毫米口径增加的遮挡容差（m）：2 mm/mm */
    private static final float OCCLUSION_TOL_PER_MM_M = 0.002f;
    /** 遮挡容差上限（m）：容差越大越容易从近处遮挡物漏光 */
    private static final float OCCLUSION_TOL_MAX_M = 1.0f;
    /**
     * 遮挡容差的相对分量：判据比较的是沿视线的前向深度，而单像素能分辨的深度差正比于前向深度，
     * 因此远距离需要与之成比例的余量。最终容差取两者的较大者。
     */
    private static final float OCCLUSION_TOL_REL = 0.002f;

    /** 超时淡出起点（tick）：与 {@code ClientProjectileRenderer} 的曳光淡出包络一致 */
    private static final int FADE_TICKS = 20;

    /** 深度两点标定的近点前向深度（m） */
    private static final float CALIB_Z_NEAR = 1.0f;
    /** 深度两点标定的远点前向深度（m） */
    private static final float CALIB_Z_FAR = 256.0f;

    private static final ResourceLocation CHAIN_LOCATION =
            ResourceLocation.parse("machine_max:shaders/post/tracer_light.json");
    /** 本效果 pass 的名字，与链 JSON 中声明的 name 一致 */
    private static final String PASS_NAME = "machine_max:tracer_light";
    /** 链内声明的世界深度快照目标名，与链 JSON 的 targets 一致 */
    private static final String WORLD_DEPTH_TARGET = "worlddepth";

    @Nullable
    private PostChain chain;
    private boolean loaded = false;
    private int cachedWidth = -1;
    private int cachedHeight = -1;

    /** 链内声明的世界深度快照；本效果的 pass 采样它，而不是 {@code minecraft:main:depth} */
    @Nullable
    private RenderTarget worldDepth;
    /** 快照目标的深度格式是否已与主目标对齐（每个链实例只需检查一次） */
    private boolean worldDepthFormatChecked;
    /** 快照尺寸与主目标不一致时只报一次，避免刷屏 */
    private boolean sizeMismatchLogged;

    /**
     * 数组 uniform 无法经 {@code PostChain#setUniform(String, float)} 写入，
     * 必须拿到 pass 的 uniform 引用后全量 {@code set(float[])}，故在此长期持有。
     */
    @Nullable
    private Uniform lightViewUniform;
    @Nullable
    private Uniform lightColorUniform;
    @Nullable
    private Uniform lightTolUniform;

    /** 每帧复用的装配缓冲：16 槽 × (视空间光心 xyz, 世界半径 R) */
    private final float[] lightView = new float[MAX_LIGHTS * 4];
    /** 每帧复用的装配缓冲：16 槽 × (颜色 rgb, 强度) */
    private final float[] lightColor = new float[MAX_LIGHTS * 4];
    /** 每帧复用的装配缓冲：16 槽 × 遮挡容差的绝对分量（m） */
    private final float[] lightTol = new float[MAX_LIGHTS];
    /** 该槽的屏幕投影半径（px），仅用于排序与"超限时替换最弱一个" */
    private final float[] sortKey = new float[MAX_LIGHTS];

    /** 视空间光心与光心世界坐标的结果复用对象，避免每帧分配 */
    private final Vector3f viewPos = new Vector3f();
    private final Vector3f worldPos = new Vector3f();

    /**
     * 加载或重新加载 PostChain 资源，并取出数组 uniform 的引用。
     */
    public void load() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getResourceManager() == null) return;
        try {
            PostChain newChain = new PostChain(
                    mc.getTextureManager(),
                    mc.getResourceManager(),
                    mc.getMainRenderTarget(),
                    CHAIN_LOCATION
            );
            if (chain != null) chain.close();
            chain = newChain;
            cachedWidth = mc.getWindow().getWidth();
            cachedHeight = mc.getWindow().getHeight();
            chain.resize(cachedWidth, cachedHeight);
            refreshDepthTarget();
            bindArrayUniforms(newChain);
            loaded = true;
            MachineMax.LOGGER.debug("投射物曳光光照后处理已加载");
        } catch (IOException e) {
            MachineMax.LOGGER.error("投射物曳光光照后处理加载失败", e);
            loaded = true; // 标记已尝试，不再重复失败
        }
    }

    /**
     * 取出链内的世界深度快照目标。
     *
     * <p>链每个实例检查一次深度格式：深度位 blit 要求两侧格式逐位相同，主目标带 stencil 时
     * 快照也必须带，否则 {@code glBlitFrameBuffer} 按规范非法（{@code GL_INVALID_OPERATION}），
     * 快照里只剩清屏值、着色器会逐像素早退。链 JSON 里声明的目标已被 NeoForge 的
     * {@code PostChain.addTempTarget} 补丁镜像了主目标的 stencil，正常情况下走不到这条守卫。</p>
     */
    private void refreshDepthTarget() {
        if (chain == null) return;
        worldDepth = chain.getTempTarget(WORLD_DEPTH_TARGET);
        if (worldDepth == null) {
            MachineMax.LOGGER.error("投射物曳光光照：链未声明世界深度快照目标 {}，光照不会生效",
                    WORLD_DEPTH_TARGET);
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (!worldDepthFormatChecked) {
            worldDepthFormatChecked = true;
            if (main.isStencilEnabled() && !worldDepth.isStencilEnabled()) {
                worldDepth.enableStencil();
                MachineMax.LOGGER.info("投射物曳光光照：世界深度快照目标已切换为与主目标一致的深度-模板格式");
            }
        }
    }

    /**
     * 从链中取出本效果 pass 的数组 uniform。
     *
     * <p>JSON 里的数组默认值不可用（解析期只写前若干个分量，其余是未初始化内存），
     * 所以引用必须在加载时拿到，之后每帧全量写入。</p>
     *
     * @param newChain 刚创建的链
     */
    private void bindArrayUniforms(PostChain newChain) {
        lightViewUniform = null;
        lightColorUniform = null;
        lightTolUniform = null;
        for (PostPass pass : ((PostChainAccessor) newChain).machineMax$getPasses()) {
            if (!PASS_NAME.equals(pass.getName())) continue;
            lightViewUniform = pass.getEffect().getUniform("LightView");
            lightColorUniform = pass.getEffect().getUniform("LightColor");
            lightTolUniform = pass.getEffect().getUniform("LightTol");
        }
        if (lightViewUniform == null || lightColorUniform == null || lightTolUniform == null) {
            MachineMax.LOGGER.error(
                    "投射物曳光光照：pass {} 未声明数组 uniform LightView/LightColor/LightTol，光照不会生效",
                    PASS_NAME);
        }
    }

    /**
     * 每帧在 {@code AFTER_LEVEL} 组装候选光源并提交一次光照 pass。
     *
     * <p>挂载点必须是 {@code AFTER_LEVEL}：那时世界几何（含实体、方块实体、粒子、云）已全部
     * 画完、深度完整，而第一人称手部还没画——光照只作用于世界画面。深度快照也在本方法内、
     * 在任何以 {@code minecraft:main} 为输出目标的 pass 之前取走。</p>
     *
     * @param modelViewMatrix  纯旋转的视图矩阵（事件携带）
     * @param projectionMatrix 相机投影矩阵（已取世界 FOV 与设置值中的较大者）
     * @param cameraPos        相机世界坐标
     * @param level            当前维度，用于取该维度的投射物管理器
     * @param partialTick      帧间插值量，用于与曳光渲染器取同一个插值位姿
     */
    public void render(Matrix4f modelViewMatrix, Matrix4f projectionMatrix, Vec3 cameraPos,
                       Level level, float partialTick) {
        if (!MMClientConfig.isTracerLightEnabled()) return;

        ProjectileManager pm = ObjectManager.levelProjectileManagers.get(level);
        if (pm == null) return;

        Minecraft mc = Minecraft.getInstance();
        int h = mc.getWindow().getHeight();
        float focalPx = BlastProjectionUtil.focalPx(projectionMatrix, h);
        if (focalPx <= 0f) return;

        float maxDistance = mc.gameRenderer.getRenderDistance();
        int count = assemble(pm, modelViewMatrix, cameraPos, focalPx, maxDistance,
                MMClientConfig.getTracerLightIntensity(), partialTick);
        if (count == 0) return; // 没有候选光源：pass 不提交、深度也不拷

        if (!ensureLoaded()) return;
        if (lightViewUniform == null || lightColorUniform == null || lightTolUniform == null) return;

        int w = mc.getWindow().getWidth();
        if (w != cachedWidth || h != cachedHeight) {
            cachedWidth = w;
            cachedHeight = h;
            chain.resize(w, h);
            refreshDepthTarget();
        }

        if (!captureWorldDepth()) return;

        // 标量经链写入（对未声明该 uniform 的 pass 是空操作），数组必须拿到 pass 引用全量写入
        float[] depth = BlastProjectionUtil.calibrateDepth(projectionMatrix, CALIB_Z_NEAR, CALIB_Z_FAR);
        chain.setUniform("FocalPx", focalPx);
        chain.setUniform("DepthA", depth[0]);
        chain.setUniform("DepthB", depth[1]);
        chain.setUniform("LightCount", count);
        chain.setUniform("OcclusionTolRel", OCCLUSION_TOL_REL);
        lightViewUniform.set(lightView);
        lightColorUniform.set(lightColor);
        lightTolUniform.set(lightTol);

        chain.process(partialTick);
        mc.getMainRenderTarget().bindWrite(true);
    }

    /**
     * 把主目标的世界深度拷进链内快照目标。
     *
     * <p>拷前拷后都要还原状态：{@code copyDepthFrom} 结束时把 FBO 0 绑在 DRAW 上（不还原会让
     * 后续渲染打错目标），而 blit 本身受深度写掩码约束（掩码为假时拷不动）。</p>
     *
     * @return 快照是否可用；false 表示本帧不能提交 pass
     */
    private boolean captureWorldDepth() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        RenderTarget target = worldDepth;
        if (target == null) return false;
        if (target.width != main.width || target.height != main.height) {
            if (!sizeMismatchLogged) {
                sizeMismatchLogged = true;
                MachineMax.LOGGER.error("投射物曳光光照：世界深度快照尺寸 {}x{} 与主目标 {}x{} 不一致，暂停光照",
                        target.width, target.height, main.width, main.height);
            }
            return false;
        }
        sizeMismatchLogged = false;

        boolean maskBefore = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        GlStateManager._depthMask(true);
        target.copyDepthFrom(main);
        main.bindWrite(true);
        GlStateManager._depthMask(maskBefore);
        return true;
    }

    /** 惰性加载；返回链是否可用。加载过程会绑过临时 FBO，结束前还原主目标。 */
    private boolean ensureLoaded() {
        if (chain != null) return true;
        if (!loaded) {
            load();
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
        return chain != null;
    }

    /**
     * 逐发装配候选光源。
     *
     * <p>门禁与 {@code ClientProjectileRenderer} 完全相同：<b>看类型的曳光配置与对象能否取到，
     * 不看 {@code alive}</b>——死条目由 {@code deadRetained} 多留一次清理扫描，这一帧仍要画出曳光，
     * 光照若按 {@code alive} 早退，那一发就只剩曳光线、没有光。只有 {@code tracer_alpha > 0}
     * 的弹种会照亮，颜色也取自曳光配置，因此内容包唯一需要新增的可选字段是强度（§11.1）。</p>
     *
     * @return 实际写入的槽位数，0 表示本帧不必提交 pass
     */
    private int assemble(ProjectileManager pm, Matrix4f modelViewMatrix, Vec3 cameraPos,
                         float focalPx, float maxDistance, float globalIntensity, float partialTick) {
        int count = 0;
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
            BlastProjectionUtil.toViewSpace(modelViewMatrix, cameraPos, worldPos, viewPos);

            float frontDepth = -viewPos.z;
            if (frontDepth <= 0f) continue; // 光心在相机背后：屏幕投影会翻转

            float caliberMm = type.getCaliber();
            float radius = lightRadius(caliberMm);
            if (radius <= 0f) continue;

            // 屏幕半径剔除：投影半径小于下限的光源贡献不可辨识
            float radiusPx = focalPx * radius / frontDepth;
            if (radiusPx < MIN_SCREEN_RADIUS_PX) continue;

            // 视空间长度等于世界距离，可直接当作"光心到相机的距离"用
            if (viewPos.length() > maxDistance) continue;

            float intensity = (float) type.getTracerLightIntensity() * globalIntensity
                    * (type.getTracerAlpha() / 255f);
            if (pm.lifetime[i] < FADE_TICKS) {
                intensity *= pm.lifetime[i] / (float) FADE_TICKS;
            }
            if (intensity <= 0f) continue;

            int slot;
            if (count < MAX_LIGHTS) {
                slot = count++;
            } else {
                // 超出上限：替换掉当前屏幕半径最小的一个
                int weakest = 0;
                for (int k = 1; k < MAX_LIGHTS; k++) {
                    if (sortKey[k] < sortKey[weakest]) weakest = k;
                }
                if (sortKey[weakest] >= radiusPx) continue;
                slot = weakest;
            }

            Vec3i color = type.getTracerColor();
            lightView[slot * 4] = viewPos.x;
            lightView[slot * 4 + 1] = viewPos.y;
            lightView[slot * 4 + 2] = viewPos.z;
            lightView[slot * 4 + 3] = radius;

            lightColor[slot * 4] = color.getX() / 255f;
            lightColor[slot * 4 + 1] = color.getY() / 255f;
            lightColor[slot * 4 + 2] = color.getZ() / 255f;
            lightColor[slot * 4 + 3] = intensity;

            lightTol[slot] = occlusionTolerance(caliberMm);
            sortKey[slot] = radiusPx;
        }
        return count;
    }

    /**
     * 照亮半径：{@code R = 1.5 m + 1 cm × 口径(mm)}，上限 4 m。
     *
     * <p>口径是"弹丸有多大"这件事在数据里的唯一载体（{@code external.caliber}，单位 mm），
     * 物理半径 = 口径/2000 m、命中半径与风阻截面积都由它换算，因此照射半径也由它线性折算，
     * 不需要新增内容包字段：7.62mm → 1.58 m，20mm → 1.70 m，50mm → 2.00 m，155mm → 3.05 m。</p>
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
     * <p>着色器里的判据是"光源屏幕位置处的场景深度 < 光源前向深度 − 容差 → 本帧跳过该光源"，
     * 容差太小时投射物模型会把自己剔掉（模型写深度缓冲），太大时近处遮挡物漏光，
     * 因此按弹体的细长比与口径线性折算。见 {@link #OCCLUSION_TOL_BASE_M}。</p>
     *
     * @param caliberMm 弹种口径（mm）
     * @return 容差的绝对分量（m）
     */
    private static float occlusionTolerance(float caliberMm) {
        return Math.min(OCCLUSION_TOL_BASE_M + OCCLUSION_TOL_PER_MM_M * caliberMm, OCCLUSION_TOL_MAX_M);
    }

    /**
     * 释放 GPU 资源。
     */
    public void dispose() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
        lightViewUniform = null;
        lightColorUniform = null;
        lightTolUniform = null;
        worldDepth = null;
        worldDepthFormatChecked = false;
        sizeMismatchLogged = false;
        loaded = false;
        cachedWidth = -1;
        cachedHeight = -1;
    }
}
