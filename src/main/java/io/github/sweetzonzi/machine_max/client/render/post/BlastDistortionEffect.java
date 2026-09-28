package io.github.sweetzonzi.machine_max.client.render.post;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.mojang.blaze3d.shaders.Uniform;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.MMClientConfig;
import io.github.sweetzonzi.machine_max.common.mech.explosion.BlastFrontVisual;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionParams;
import io.github.sweetzonzi.machine_max.mixin.PostChainAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;

/**
 * 爆炸波前折射后处理（屏幕空间折射环）。
 *
 * <p>折射是"读画面"的活：以投影后的爆心为圆心，在波前所在的细环上做双极径向偏移。
 * 因此只能在世界画完之后做全屏后处理，而不是在世界内画几何——球壳网格看不到它背后的像素。</p>
 *
 * <p>每帧的数据装配全部是 CPU 侧标量运算：先减相机位置再乘视图旋转矩阵得到视空间爆心，
 * 随后逐发求半径 R、壳层厚度 W、屏幕偏移峰值 delta 与色散比 chi，写入扁平数组 uniform。
 * 着色器只认这五个量，<b>波前速度不进入任何公式</b>（R 的计算里已经含了它，仅此一次），
 * 因此 1 m/s 与 200 m/s 的爆炸观感形状一致、只有快慢不同。</p>
 *
 * <p>无活跃爆炸、或配置关闭时不加载、不提交 post pass，零渲染开销。</p>
 */
public class BlastDistortionEffect {

    // ===== 数值策略常量（改这些不需要重编着色器）=====

    /** 壳层厚度（单瓣），必须是与 R 无关的常量；若与 R 成比例，环会随半径变粗、"整颗球在扭" */
    private static final float SHELL_THICKNESS = 0.30f;
    /** 屏幕单瓣宽下限（px）：远处不至于细到看不见，也避免采样抖动 */
    private static final float MIN_RING_WIDTH_PX = 7.0f;
    /** 屏幕径向偏移峰值上限（px） */
    private static final float MAX_OFFSET_PX = 16.0f;
    /** 色散比：三通道偏移量的最大相对差（0.15 表示 ±15%） */
    private static final float CHROMA_RATIO = 0.25f;
    /** 时间包络淡入终点（归一化进度 R/max_radius） */
    private static final float FADE_IN_PROGRESS = 0.06f;
    /** 时间包络淡出起点，末段归零，避免波前到达 max_radius 时被硬切 */
    private static final float FADE_OUT_PROGRESS = 0.75f;
    /** 同屏处理的爆炸发数上限 */
    private static final int MAX_BLASTS = 8;
    /** 深度两点标定的近点前向深度（m） */
    private static final float CALIB_Z_NEAR = 1.0f;
    /** 深度两点标定的远点前向深度（m） */
    private static final float CALIB_Z_FAR = 256.0f;

    /** 低于该偏移量的爆炸不必进 pass，观感上不可辨识 */
    private static final float MIN_DELTA_PX = 1e-3f;

    private static final ResourceLocation CHAIN_LOCATION =
            ResourceLocation.parse("machine_max:shaders/post/blast_distortion.json");
    /** 本效果 pass 的名字，与链 JSON 中声明的 name 一致 */
    private static final String PASS_NAME = "machine_max:blast_distortion";

    @Nullable
    private PostChain chain;
    private boolean loaded = false;
    private int cachedWidth = -1;
    private int cachedHeight = -1;

    /**
     * 数组 uniform 无法经 {@code PostChain#setUniform(String, float)} 写入，
     * 必须拿到 pass 的 uniform 引用后全量 {@code set(float[])}，故在此长期持有。
     */
    @Nullable
    private Uniform blastViewUniform;
    @Nullable
    private Uniform blastShapeUniform;

    /** 每帧复用的装配缓冲：8 发 × (视空间爆心 xyz, 世界半径 R) */
    private final float[] blastView = new float[MAX_BLASTS * 4];
    /** 每帧复用的装配缓冲：8 发 × (壳层厚度 W, 偏移峰值 delta, 色散比 chi, 备用) */
    private final float[] blastShape = new float[MAX_BLASTS * 4];
    /** 视空间爆心结果复用对象，避免每帧分配 */
    private final Vector3f viewPos = new Vector3f();

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
            bindArrayUniforms(newChain);
            loaded = true;
            MachineMax.LOGGER.debug("爆炸波前折射后处理已加载");
        } catch (IOException e) {
            MachineMax.LOGGER.error("爆炸波前折射后处理加载失败", e);
            loaded = true; // 标记已尝试，不再重复失败
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
        blastViewUniform = null;
        blastShapeUniform = null;
        for (PostPass pass : ((PostChainAccessor) newChain).machineMax$getPasses()) {
            if (!PASS_NAME.equals(pass.getName())) continue;
            blastViewUniform = pass.getEffect().getUniform("BlastView");
            blastShapeUniform = pass.getEffect().getUniform("BlastShape");
        }
        if (blastViewUniform == null || blastShapeUniform == null) {
            MachineMax.LOGGER.error("爆炸波前折射：pass {} 未声明数组 uniform BlastView/BlastShape，折射不会生效", PASS_NAME);
        }
    }

    /**
     * 每帧在主渲染目标上执行一次折射。
     *
     * <p>必须排在过载黑视与失色之前：折射要读未被染色的画面。</p>
     *
     * @param modelViewMatrix  纯旋转的视图矩阵（事件携带）
     * @param projectionMatrix 相机投影矩阵（已取世界 FOV 与设置值中的较大者）
     * @param cameraPos        相机世界坐标
     * @param visuals          当前表现条目（无活跃爆炸时为空表）
     * @param partialTick      帧间插值量，用于在上一 tick 与当前 tick 的半径之间插值
     */
    public void render(Matrix4f modelViewMatrix, Matrix4f projectionMatrix, Vec3 cameraPos,
                       List<BlastFrontVisual> visuals, float partialTick) {
        if (!MMClientConfig.isBlastDistortionEnabled()) return;
        if (visuals.isEmpty()) return;
        if (!loaded && chain == null) {
            load();
            return;
        }
        if (chain == null) return;
        if (blastViewUniform == null || blastShapeUniform == null) return;

        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (w != cachedWidth || h != cachedHeight) {
            cachedWidth = w;
            cachedHeight = h;
            chain.resize(w, h);
        }

        float focalPx = BlastProjectionUtil.focalPx(projectionMatrix, h);
        if (focalPx <= 0f) return;
        float intensity = MMClientConfig.getBlastDistortionIntensity();

        int count = assemble(visuals, modelViewMatrix, cameraPos, focalPx, intensity, partialTick);
        if (count == 0) return; // 全部发都被剔除：pass 不提交

        // 标量经链写入（对未声明该 uniform 的 pass 是空操作），数组必须拿到 pass 引用全量写入
        float[] depth = BlastProjectionUtil.calibrateDepth(projectionMatrix, CALIB_Z_NEAR, CALIB_Z_FAR);
        chain.setUniform("FocalPx", focalPx);
        chain.setUniform("DepthA", depth[0]);
        chain.setUniform("DepthB", depth[1]);
        chain.setUniform("BlastCount", count);
        blastViewUniform.set(blastView);
        blastShapeUniform.set(blastShape);

        chain.process(partialTick);
        mc.getMainRenderTarget().bindWrite(true);
    }

    /**
     * 逐发求值并写入装配缓冲。
     *
     * @return 实际写入的发数，0 表示本帧不必提交 pass
     */
    private int assemble(List<BlastFrontVisual> visuals, Matrix4f modelViewMatrix, Vec3 cameraPos,
                         float focalPx, float intensity, float partialTick) {
        int count = 0;
        for (BlastFrontVisual visual : visuals) {
            ExplosionParams params = visual.params();
            float radius = visual.frontRadius(partialTick);
            if (radius <= 0f) continue;

            // 视空间爆心：先减相机位置，再乘视图旋转矩阵
            BlastProjectionUtil.toViewSpace(modelViewMatrix, cameraPos,
                    SparkMathKt.toVector3f(visual.origin()), viewPos);
            float frontDepth = -viewPos.z;
            if (frontDepth <= 0f) continue; // 爆心在相机背后：屏幕径向方向会翻转

            float delta = offsetPeakPx(radius, params, intensity);
            if (delta <= MIN_DELTA_PX) continue;

            // 远处环在屏幕上过细时，把壳层厚度抬到下限对应的世界厚度
            float thickness = Math.max(SHELL_THICKNESS, MIN_RING_WIDTH_PX * frontDepth / focalPx);

            int slot;
            if (count < MAX_BLASTS) {
                slot = count++;
            } else {
                // 超出上限：替换掉当前偏移最弱的一发
                int weakest = 0;
                for (int k = 1; k < MAX_BLASTS; k++) {
                    if (blastShape[k * 4 + 1] < blastShape[weakest * 4 + 1]) weakest = k;
                }
                if (blastShape[weakest * 4 + 1] >= delta) continue;
                slot = weakest;
            }

            blastView[slot * 4] = viewPos.x;
            blastView[slot * 4 + 1] = viewPos.y;
            blastView[slot * 4 + 2] = viewPos.z;
            blastView[slot * 4 + 3] = radius;

            blastShape[slot * 4] = thickness;
            blastShape[slot * 4 + 1] = delta;
            blastShape[slot * 4 + 2] = CHROMA_RATIO;
            blastShape[slot * 4 + 3] = 0f;
        }
        return count;
    }

    /**
     * 屏幕偏移峰值 delta = δ_max · a_d · a_t · c_intensity。
     *
     * <p>两项衰减都在 (0, 1]：{@code a_d} 按 1/R 衰减（振幅是压力的量类），
     * {@code a_t} 用归一化进度 R/max_radius 做两端平滑的时间包络——它与波前速度无关，
     * 因此不同传播速度的爆炸观感形状一致、只有快慢不同。</p>
     *
     * @param radius    当前波前半径（m）
     * @param params    该发的参数集
     * @param intensity 客户端强度倍率
     * @return 偏移峰值（px）
     */
    private static float offsetPeakPx(float radius, ExplosionParams params, float intensity) {
        float nearRadius = params.nearRadius();
        float distanceFalloff = nearRadius / Math.max(radius, nearRadius);

        float progress = radius / params.maxRadius();
        float timeEnvelope = smoothstep(0f, FADE_IN_PROGRESS, progress)
                * (1f - smoothstep(FADE_OUT_PROGRESS, 1f, progress));

        return MAX_OFFSET_PX * distanceFalloff * timeEnvelope * intensity;
    }

    /** 标准 smoothstep：edge1 <= edge0 时退化为阶跃。 */
    private static float smoothstep(float edge0, float edge1, float x) {
        if (edge1 <= edge0) return x >= edge1 ? 1f : 0f;
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /**
     * 释放 GPU 资源。
     */
    public void dispose() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
        blastViewUniform = null;
        blastShapeUniform = null;
        loaded = false;
        cachedWidth = -1;
        cachedHeight = -1;
    }
}
