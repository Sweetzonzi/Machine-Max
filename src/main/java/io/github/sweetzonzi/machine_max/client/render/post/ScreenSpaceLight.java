package io.github.sweetzonzi.machine_max.client.render.post;

import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * 一条待提交光源的可变载体：设置与提交分离，提交时立即复制数值，因此同一个载体可以逐盏复用。
 *
 * <p>字段不逐个公开写入：{@link #setPoint} / {@link #setSpot} 一次写齐必填量并把条目置为
 * “已配置”。{@link ScreenSpaceLightBuffer#submit} 在所有返回路径清除该状态，所以忘记设置
 * 就提交会被计为非法条目，而不是把上一盏灯的残值再提交一次——零位置、零容差都是合法值，
 * “提交后全部清零”证明不了完整性。</p>
 *
 * <p>单位与口径（设计文档 §4.1）：位置是视空间米制、前方为负 Z；半径是有限影响半径（m），
 * 只决定剔除范围与窗宽、不决定亮度，点到光心距离达到半径时贡献严格为 0；颜色是 <b>sRGB 编码</b>的
 * {@code [0,1]} 通道，由光源表在写入槽位时一次性转成线性；强度是光源在 1 m 处的辐照度
 * （归一化单位，数值越大只是在同一距离上更亮），全局倍率由光源表统一乘一次，这里放的只是
 * 内容强度与时间包络的乘积。</p>
 *
 * <p><b>强度有两个口径</b>：{@link #intensity()} 是<b>当前</b>强度（已乘时间包络），供表面光照与
 * 光晕亮度的求值；{@link #baseIntensity()} 是内容给出的基础强度，不带包络，只用于派生光晕的
 * 阈值半径。两者分开是因为光晕尺寸不该随闪烁抖动：枪口焰的 {@code (1-u)²} 包络会让半径每帧缩小，
 * 表现成"光晕在往回缩"。</p>
 *
 * <p>渲染线程独占；来源 ID 与优先级由光源表在提交时盖上，不由调用方填写。</p>
 */
public final class ScreenSpaceLight {

    /** 形状：P1 的 GPU 编码只接入球光，锥光在 P5 接入 */
    public enum Shape {
        POINT,
        SPOT
    }

    /** 遮挡策略：S2 是 {@link #SOURCE_DEPTH}，S3 是 {@link #SCREEN_RAY}（P5 接入） */
    public enum OcclusionMode {
        /** 不做遮挡判定 */
        NONE,
        /**
         * S2：在光心的屏幕投影位置采一次深度，显著更近即判定光源自身不可见，跳过它的表面项与星芒。
         *
         * <p>空气散射光晕不走这条判定：它有逐像素的深度截断，比这一次采样精确，套上本判定会把
         * "光源被挡住、光晕球露出一半"的情形整片抹掉。</p>
         */
        SOURCE_DEPTH,
        /** S3：对受光表面点到光源的线段做屏幕空间遮挡检测 */
        SCREEN_RAY
    }

    /**
     * {@code 1/d²} 奇点截断距离的缺省值（m）。
     *
     * <p>0.5 m 适合“光心会贴到表面上”的来源：枪口焰就在炮口、曳光沿弹道掠过车体与地面，
     * 距离小到几十厘米是常态。爆炸火光的光心通常离表面更远、且没有强方向性，取更大的值
     * （见 {@code ExplosionLightSource}）可以让爆心的过曝范围不至于太小。</p>
     */
    public static final float DEFAULT_MIN_DISTANCE_M = 0.5f;

    /**
     * 光晕阈值半径的标定系数（m）：{@code Rg = GLOW_SCALE_M · sqrt(I_base) · k}（设计文档 §3.6）。
     *
     * <p>理想模型里散射光晕的阈值半径正比于光源强度；这条链的帧已经过色调映射，用 {@code sqrt}
     * 压缩后更接近观感。当前标定下 {@code I ∝ R²}，于是 {@code sqrt(I) ∝ R}：同一组锚点与光色下
     * {@code Rg/R} 恒定（枪口 0.32、爆炸 0.52、曳光 0.18–0.26），换锚点或光色则整体平移。
     * 这是标定的副作用，不是"光晕半径由照明半径决定"。</p>
     */
    public static final float GLOW_SCALE_M = 0.32f;

    /** 光晕阈值半径的上限（m）：兜住异常内容与贴脸光源 */
    public static final float MAX_GLOW_RADIUS_M = 32f;

    /** 提交时由光源表盖上的来源身份，用于逐来源诊断 */
    @Nullable
    private ResourceLocation sourceId;
    /** 提交时由光源表盖上的来源优先级 */
    private int priority;

    private boolean configured;

    private final Vector3f positionView = new Vector3f();
    private float radius;
    private final Vector3f color = new Vector3f();
    private float intensity;
    private float minDistance = DEFAULT_MIN_DISTANCE_M;
    private float baseIntensity;
    private float glowMultiplier = 1f;
    private float glareMultiplier = 1f;

    private Shape shape = Shape.POINT;
    private final Vector3f directionView = new Vector3f();
    private float cosInner = 1f;
    private float cosOuter = -1f;

    private OcclusionMode occlusionMode = OcclusionMode.NONE;
    private float sourceDepthTolerance;
    private float normalWeight;

    /**
     * 一次写齐一条球光。
     *
     * @param positionView          视空间光心（米，前方为负 Z），按下标读到的是本次调用的数值
     * @param radius                有限影响半径（m），须大于 0；只决定剔除与窗宽
     * @param color                 非负 RGB，sRGB 编码、通道归一化到 {@code [0,1]}
     * @param intensity             非负辐照度（1 m 处），不含全局倍率
     * @param occlusionMode         遮挡策略；当前阶段支持 {@code NONE} 与 {@code SOURCE_DEPTH}
     * @param sourceDepthTolerance  S2 的绝对容差（m），允许 0
     * @param normalWeight          法线响应权重 {@code [0,1]}；逐盏值在 P5 接入锥光时随 {@code LightNormalWeight[16]} 上传，
     *                              当前球光路径的朝向权重取全局 {@code NormalWeight}
     */
    public void setPoint(Vector3f positionView, float radius, Vector3f color, float intensity,
                         OcclusionMode occlusionMode, float sourceDepthTolerance, float normalWeight) {
        this.positionView.set(positionView);
        this.radius = radius;
        this.color.set(color);
        this.intensity = intensity;
        this.minDistance = DEFAULT_MIN_DISTANCE_M;
        this.baseIntensity = 0f;                    // 缺省不发散空气散射：来源要显式 setGlow
        this.glowMultiplier = 1f;
        this.glareMultiplier = 1f;                  // 星芒缺省按标定倍率画：来源不调 setGlare 也有
        this.shape = Shape.POINT;
        this.directionView.set(0f, 0f, -1f);
        this.cosInner = 1f;
        this.cosOuter = -1f;
        this.occlusionMode = occlusionMode;
        this.sourceDepthTolerance = sourceDepthTolerance;
        this.normalWeight = normalWeight;
        this.configured = true;
    }

    /**
     * 一次写齐一条锥光。
     *
     * @param positionView          视空间光心（米，前方为负 Z）
     * @param radius                有限影响半径（m），须大于 0；锥光被同半径球截断
     * @param color                 非负 RGB，sRGB 编码、通道归一化到 {@code [0,1]}
     * @param intensity             非负辐照度（1 m 处），不含全局倍率
     * @param directionView         视空间照射方向，单位向量，从灯指向照射方向
     * @param cosInner              内半角的余弦，须满足 {@code cosInner >= cosOuter}
     * @param cosOuter              外半角的余弦
     * @param occlusionMode         遮挡策略
     * @param sourceDepthTolerance  S2 的绝对容差（m），允许 0
     * @param normalWeight          法线响应权重 {@code [0,1]}；逐盏值在 P5 接入锥光时随 {@code LightNormalWeight[16]} 上传，
     *                              当前球光路径的朝向权重取全局 {@code NormalWeight}
     */
    public void setSpot(Vector3f positionView, float radius, Vector3f color, float intensity,
                        Vector3f directionView, float cosInner, float cosOuter,
                        OcclusionMode occlusionMode, float sourceDepthTolerance, float normalWeight) {
        this.positionView.set(positionView);
        this.radius = radius;
        this.color.set(color);
        this.intensity = intensity;
        this.minDistance = DEFAULT_MIN_DISTANCE_M;
        this.baseIntensity = 0f;                    // 缺省不发散空气散射：来源要显式 setGlow
        this.glowMultiplier = 1f;
        this.glareMultiplier = 1f;                  // 星芒缺省按标定倍率画：来源不调 setGlare 也有
        this.shape = Shape.SPOT;
        this.directionView.set(directionView);
        this.cosInner = cosInner;
        this.cosOuter = cosOuter;
        this.occlusionMode = occlusionMode;
        this.sourceDepthTolerance = sourceDepthTolerance;
        this.normalWeight = normalWeight;
        this.configured = true;
    }

    /** 清除“已配置”标记；由光源表在提交的所有返回路径上调用。 */
    void clear() {
        this.configured = false;
    }

    /** 盖上来源身份与优先级；由光源表在 {@code beginSource} 之后、提交时调用。 */
    void stamp(ResourceLocation sourceId, int priority) {
        this.sourceId = sourceId;
        this.priority = priority;
    }

    /** @return 本载体是否已由 {@code setPoint}/{@code setSpot} 写齐 */
    public boolean isConfigured() {
        return configured;
    }

    /** @return 来源身份；提交之前为 null */
    @Nullable
    public ResourceLocation sourceId() {
        return sourceId;
    }

    /** @return 来源优先级；提交之前为 0 */
    public int priority() {
        return priority;
    }

    /** @return 视空间光心（自有实例，只读借用） */
    public Vector3f positionView() {
        return positionView;
    }

    /** @return 有限影响半径（m） */
    public float radius() {
        return radius;
    }

    /** @return 颜色（自有实例，只读借用） */
    public Vector3f color() {
        return color;
    }

    /** @return 强度（1 m 处辐照度），不含全局倍率 */
    public float intensity() {
        return intensity;
    }

    /**
     * 设置 {@code 1/d²} 的奇点截断距离（m）。
     *
     * <p>可选：由 {@link #setPoint}/{@link #setSpot} 复位为 {@link #DEFAULT_MIN_DISTANCE_M}，
     * 来源需要别的值时才在写齐之后再调一次。允许 0（退化为无截断的 {@code 1/d²}），
     * 非有限值或负值会被光源表按非法提交拒绝。</p>
     *
     * @param minDistance 截断距离（m），{@code >= 0}
     */
    public void setMinDistance(float minDistance) {
        this.minDistance = minDistance;
    }

    /** @return {@code 1/d²} 的奇点截断距离（m） */
    public float minDistance() {
        return minDistance;
    }

    /**
     * 写光晕参数：基础强度与逐来源倍率（设计文档 §3.6）。
     *
     * <p>可选，且必须在 {@link #setPoint}/{@link #setSpot} 之后调用：两者会把光晕复位成"不发光晕"，
     * 载体逐盏复用，不复位就会把上一盏灯的倍率带过来。不调用即该光源没有光晕。</p>
     *
     * <p>基础强度取<b>内容强度</b>，不含时间包络、不含类别倍率与全局倍率：包络会让半径随闪烁抖动，
     * 而倍率是观感旋钮——调亮度不该顺带改变光晕的张角。倍率 {@code k} 同时缩放阈值半径与亮度，
     * {@code 0} 关闭该光源的光晕。{@code k} 没有内容侧的固定上界：超过
     * {@code 32 m / (GLOW_SCALE_M·sqrt(baseIntensity))} 后阈值半径被 {@link #MAX_GLOW_RADIUS_M}
     * 截断、只剩亮度继续增长，而亮度由着色器的 {@code GLOW_MAX} 兜住。</p>
     *
     * @param baseIntensity  内容给出的强度（1 m 处辐照度，不含包络），{@code >= 0}
     * @param glowMultiplier 逐来源倍率 {@code k}，{@code >= 0}；{@code 1} 为标定值
     */
    public void setGlow(float baseIntensity, float glowMultiplier) {
        this.baseIntensity = baseIntensity;
        this.glowMultiplier = glowMultiplier;
    }

    /** @return 内容给出的基础强度（不含时间包络）；只用于派生光晕阈值半径 */
    public float baseIntensity() {
        return baseIntensity;
    }

    /** @return 逐来源光晕倍率 {@code k}；同时缩放阈值半径与亮度，{@code 0} 表示该光源没有光晕 */
    public float glowMultiplier() {
        return glowMultiplier;
    }

    /**
     * 写星芒（十字炫光）的逐来源倍率（设计文档 §3.7）。
     *
     * <p>可选，且必须在 {@link #setPoint}/{@link #setSpot} 之后调用：两者会把它复位成 1，
     * 载体逐盏复用，不复位就会把上一盏灯的倍率带过来。不调用即按 1 处理。</p>
     *
     * <p>星芒与空气散射光晕是两项独立的加法：本倍率同时缩放臂根亮度（线性）与臂长（开方），
     * {@code 0} 关闭该光源的星芒，不影响它的表面光照与光晕。它不参与阈值半径。
     * 没有内容侧的固定上界：臂长超过屏高的一个固定比例后按像素封顶，亮度由着色器的
     * {@code GLOW_MAX} 兜住。</p>
     *
     * @param glareMultiplier 逐来源倍率 {@code g}，{@code >= 0}；{@code 1} 为标定值
     */
    public void setGlare(float glareMultiplier) {
        this.glareMultiplier = glareMultiplier;
    }

    /** @return 逐来源星芒倍率 {@code g}；{@code 0} 表示该光源没有星芒 */
    public float glareMultiplier() {
        return glareMultiplier;
    }

    /** @return 形状 */
    public Shape shape() {
        return shape;
    }

    /** @return 视空间照射方向（自有实例，只读借用）；球光下无意义 */
    public Vector3f directionView() {
        return directionView;
    }

    /** @return 内半角余弦 */
    public float cosInner() {
        return cosInner;
    }

    /** @return 外半角余弦 */
    public float cosOuter() {
        return cosOuter;
    }

    /** @return 遮挡策略 */
    public OcclusionMode occlusionMode() {
        return occlusionMode;
    }

    /** @return S2 的绝对容差（m） */
    public float sourceDepthTolerance() {
        return sourceDepthTolerance;
    }

    /** @return 法线响应权重（逐盏值；当前片元读到的是全局 {@code NormalWeight}，逐盏值留待 P5 随锥光一起上传） */
    public float normalWeight() {
        return normalWeight;
    }
}
