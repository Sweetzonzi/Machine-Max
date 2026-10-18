package io.github.sweetzonzi.machine_max.client.render.post;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.MMClientConfig;
import io.github.sweetzonzi.machine_max.mixin.PostChainAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;

/**
 * 通用屏幕空间光照后处理：采集编排、链的加载与释放、尺寸与深度格式检查、uniform 上传和绘制。
 *
 * <p>本类不认识任何具体光源：它按构造期固定的来源列表逐帧调用
 * {@link ScreenSpaceLightSource#collect}，把候选交给 {@link ScreenSpaceLightBuffer}，
 * 再把光源表的扁平数组一次性上传。逐光源的语义与剔除在各自的来源里
 * （{@link ProjectileTracerLightSource}、{@link ExplosionLightSource}）。</p>
 *
 * <p>总开关 {@code screen_space_light_enabled} 关闭时本类不采集任何来源、不加载链；总倍率
 * {@code screen_space_light_intensity} 交给光源表在写入槽位时统一乘一次。类别开关与类别倍率
 * 在各自的来源里生效。</p>
 *
 * <p>每帧在 {@code AFTER_LEVEL} 执行：那时世界几何（含实体、方块实体、粒子、云）已全部画完、
 * 深度完整，而第一人称手部还没画。深度快照在本方法内、在任何以 {@code minecraft:main} 为输出
 * 目标的 pass 之前取走——链尾的颜色回写会清空主目标深度，取晚了整屏都会被判成被遮挡。</p>
 *
 * <p>无候选光源的帧不加载链、不拷深度、不提交 pass，因此绝大多数帧（没有曳光在飞）零渲染开销，
 * 只有 CPU 侧的来源遍历发生。</p>
 *
 * <p>设计文档：{@code docs/plan/进行中/渲染-屏幕空间光照通用化设计.md} §4、§5、§7。</p>
 */
public class ScreenSpaceLightEffect {

    // ===== 数值策略常量（改这些不需要重编着色器）=====

    /** 槽位数：与链 JSON 的 {@code LightView[64]} / {@code LightColor[64]} / {@code LightTol[16]} / {@code LightOcclusion[16]} / {@code LightMinDist[16]} / {@code LightGlow[32]} / {@code LightGlare[16]} 对齐 */
    private static final int CAPACITY = 16;

    /**
     * S2 容差的相对分量：最终容差取“来源给出的绝对分量”与该值的较大者。
     *
     * <p>判据比较的是沿视线的前向深度，而单像素能分辨的深度差正比于前向深度，因此远距离需要
     * 与之成比例的余量。它与具体来源无关，所以留在链这一层，不逐盏上传。</p>
     */
    private static final float SOURCE_DEPTH_RELATIVE_TOLERANCE = 0.002f;

    /** 深度两点标定的近点前向深度（m） */
    private static final float CALIB_Z_NEAR = 1.0f;
    /** 深度两点标定的远点前向深度（m） */
    private static final float CALIB_Z_FAR = 256.0f;

    private static final ResourceLocation CHAIN_LOCATION =
            ResourceLocation.parse("machine_max:shaders/post/screen_space_light.json");
    /** 本效果 pass 的名字，与链 JSON 中声明的 name 一致 */
    private static final String PASS_NAME = "machine_max:screen_space_light";
    /** 链内声明的世界深度快照目标名，与链 JSON 的 targets 一致 */
    private static final String WORLD_DEPTH_TARGET = "worlddepth";

    /** 构造期固定的来源列表，按注册顺序逐帧采集 */
    private final List<ScreenSpaceLightSource> sources;
    /** 本次主视图的只读上下文，渲染线程每帧填一次并复用 */
    private final LightFrameContext frame = new LightFrameContext();
    /** 光源表：扁平数组、容量选择与诊断计数 */
    private final ScreenSpaceLightBuffer buffer = new ScreenSpaceLightBuffer(CAPACITY);

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
    @Nullable
    private Uniform lightOcclusionUniform;
    @Nullable
    private Uniform lightMinDistUniform;
    @Nullable
    private Uniform lightGlowUniform;
    @Nullable
    private Uniform lightGlareUniform;

    /** 上一帧是否因总开关关闭而跳过：用于在进入关闭状态的那一帧清一次诊断 */
    private boolean disabledLastFrame;

    /**
     * @param sources 光源来源列表，构造期复制；列表顺序只影响同键时的先到先留，不表达优先级
     */
    public ScreenSpaceLightEffect(List<ScreenSpaceLightSource> sources) {
        this.sources = List.copyOf(sources);
    }

    /**
     * 每帧在 {@code AFTER_LEVEL} 采集来源并提交一次光照 pass。
     *
     * @param modelViewMatrix  纯旋转的视图矩阵（事件携带）
     * @param projectionMatrix 相机投影矩阵（已取世界 FOV 与设置值中的较大者）
     * @param cameraPos        相机世界坐标
     * @param level            当前维度，来源用它取该维度的宿主
     * @param partialTick      帧间插值量，用于与光源自身渲染取同一个位姿
     */
    public void render(Matrix4f modelViewMatrix, Matrix4f projectionMatrix, Vec3 cameraPos,
                       Level level, float partialTick) {
        if (!MMClientConfig.isScreenSpaceLightEnabled()) {
            // 总开关关闭：不采集、不加载链、不提交 pass。进入关闭状态的那一帧清一次诊断计数，
            // 使重新打开时不沿用关闭前的触顶状态；瞬态光源的年龄由游戏时钟推导，照常推进。
            if (!disabledLastFrame) {
                disabledLastFrame = true;
                buffer.resetDiagnostics();
            }
            return;
        }
        disabledLastFrame = false;

        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        float focalPx = BlastProjectionUtil.focalPx(projectionMatrix, height);
        float renderDistance = mc.gameRenderer.getRenderDistance();

        frame.update(level, cameraPos, modelViewMatrix, projectionMatrix,
                width, height, focalPx, renderDistance, partialTick);
        buffer.beginFrame(frame, MMClientConfig.getScreenSpaceLightIntensity());
        for (ScreenSpaceLightSource source : sources) {
            buffer.beginSource(source);
            source.collect(frame, buffer);
        }
        buffer.endFrame();

        // 没有候选光源：pass 不提交、深度也不拷。逐帧计数在 beginFrame 归零，触顶日志的限频
        // 状态刻意不在这里重置——短暂不触顶不构成绕过频率限制的理由。
        int count = buffer.count();
        if (count == 0) return;

        if (!ensureLoaded()) return;
        if (lightViewUniform == null || lightColorUniform == null
                || lightTolUniform == null || lightOcclusionUniform == null
                || lightMinDistUniform == null || lightGlowUniform == null
                || lightGlareUniform == null) {
            return;
        }

        if (width != cachedWidth || height != cachedHeight) {
            cachedWidth = width;
            cachedHeight = height;
            chain.resize(width, height);
            refreshDepthTarget();
        }

        if (!captureWorldDepth()) return;

        // 标量经链写入（对未声明该 uniform 的 pass 是空操作），数组必须拿到 pass 引用全量写入
        float[] depth = BlastProjectionUtil.calibrateDepth(projectionMatrix, CALIB_Z_NEAR, CALIB_Z_FAR);
        chain.setUniform("FocalPx", focalPx);
        chain.setUniform("DepthA", depth[0]);
        chain.setUniform("DepthB", depth[1]);
        chain.setUniform("LightCount", count);
        chain.setUniform("OcclusionTolRel", SOURCE_DEPTH_RELATIVE_TOLERANCE);
        chain.setUniform("NormalWeight", MMClientConfig.getScreenSpaceLightNormalWeight());
        lightViewUniform.set(buffer.positionRadius());
        lightColorUniform.set(buffer.colorIntensity());
        lightTolUniform.set(buffer.sourceDepthTolerance());
        lightOcclusionUniform.set(buffer.occlusion());
        lightMinDistUniform.set(buffer.minDistance());
        lightGlowUniform.set(buffer.glow());
        lightGlareUniform.set(buffer.glare());

        chain.process(partialTick);
        mc.getMainRenderTarget().bindWrite(true);
    }

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
            verifyArrayCounts();
            loaded = true;
            MachineMax.LOGGER.debug("屏幕空间光照后处理已加载");
        } catch (IOException e) {
            MachineMax.LOGGER.error("屏幕空间光照后处理加载失败", e);
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
            MachineMax.LOGGER.error("屏幕空间光照：链未声明世界深度快照目标 {}，光照不会生效",
                    WORLD_DEPTH_TARGET);
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (!worldDepthFormatChecked) {
            worldDepthFormatChecked = true;
            if (main.isStencilEnabled() && !worldDepth.isStencilEnabled()) {
                worldDepth.enableStencil();
                MachineMax.LOGGER.info("屏幕空间光照：世界深度快照目标已切换为与主目标一致的深度-模板格式");
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
        lightOcclusionUniform = null;
        lightMinDistUniform = null;
        lightGlowUniform = null;
        lightGlareUniform = null;
        for (PostPass pass : ((PostChainAccessor) newChain).machineMax$getPasses()) {
            if (!PASS_NAME.equals(pass.getName())) continue;
            lightViewUniform = pass.getEffect().getUniform("LightView");
            lightColorUniform = pass.getEffect().getUniform("LightColor");
            lightTolUniform = pass.getEffect().getUniform("LightTol");
            lightOcclusionUniform = pass.getEffect().getUniform("LightOcclusion");
            lightMinDistUniform = pass.getEffect().getUniform("LightMinDist");
            lightGlowUniform = pass.getEffect().getUniform("LightGlow");
            lightGlareUniform = pass.getEffect().getUniform("LightGlare");
        }
        if (lightViewUniform == null || lightColorUniform == null
                || lightTolUniform == null || lightOcclusionUniform == null
                || lightMinDistUniform == null || lightGlowUniform == null
                || lightGlareUniform == null) {
            MachineMax.LOGGER.error(
                    "屏幕空间光照：pass {} 未声明数组 uniform LightView/LightColor/LightTol/LightOcclusion/LightMinDist/LightGlow/LightGlare，光照不会生效",
                    PASS_NAME);
        }
    }

    /**
     * 加载时核验 Java 数组长度与程序 JSON 的 {@code count} 一致。
     *
     * <p>{@code Uniform#set(float[])} 在数组过短时只打一条 WARN 并整段忽略、过长时可能越过内部
     * FloatBuffer 容量，因此“大于等于 count 即可”不是安全契约：不一致时直接拒绝本链生效，
     * 而不是让每一帧的上传静默失效。</p>
     */
    private void verifyArrayCounts() {
        if (lightViewUniform == null || lightColorUniform == null
                || lightTolUniform == null || lightOcclusionUniform == null
                || lightMinDistUniform == null || lightGlowUniform == null
                || lightGlareUniform == null) {
            return;
        }
        int viewCount = lightViewUniform.getCount();
        int colorCount = lightColorUniform.getCount();
        int tolCount = lightTolUniform.getCount();
        int occlusionCount = lightOcclusionUniform.getCount();
        int minDistCount = lightMinDistUniform.getCount();
        int glowCount = lightGlowUniform.getCount();
        int glareCount = lightGlareUniform.getCount();
        if (viewCount == buffer.positionRadius().length
                && colorCount == buffer.colorIntensity().length
                && tolCount == buffer.sourceDepthTolerance().length
                && occlusionCount == buffer.occlusion().length
                && minDistCount == buffer.minDistance().length
                && glowCount == buffer.glow().length
                && glareCount == buffer.glare().length) {
            return;
        }
        MachineMax.LOGGER.error(
                "屏幕空间光照：数组 uniform 的 count 与 Java 数组长度不一致"
                        + "（LightView {}/{}、LightColor {}/{}、LightTol {}/{}、LightOcclusion {}/{}、LightMinDist {}/{}、LightGlow {}/{}、LightGlare {}/{}），光照不会生效",
                viewCount, buffer.positionRadius().length,
                colorCount, buffer.colorIntensity().length,
                tolCount, buffer.sourceDepthTolerance().length,
                occlusionCount, buffer.occlusion().length,
                minDistCount, buffer.minDistance().length,
                glowCount, buffer.glow().length,
                glareCount, buffer.glare().length);
        lightViewUniform = null;
        lightColorUniform = null;
        lightTolUniform = null;
        lightOcclusionUniform = null;
        lightMinDistUniform = null;
        lightGlowUniform = null;
        lightGlareUniform = null;
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
                MachineMax.LOGGER.error("屏幕空间光照：世界深度快照尺寸 {}x{} 与主目标 {}x{} 不一致，暂停光照",
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
     * 释放 GPU 资源与诊断状态。
     */
    public void dispose() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
        lightViewUniform = null;
        lightColorUniform = null;
        lightTolUniform = null;
        lightOcclusionUniform = null;
        lightMinDistUniform = null;
        lightGlowUniform = null;
        lightGlareUniform = null;
        worldDepth = null;
        worldDepthFormatChecked = false;
        sizeMismatchLogged = false;
        loaded = false;
        cachedWidth = -1;
        cachedHeight = -1;
        disabledLastFrame = false;
        buffer.resetDiagnostics();
    }
}
