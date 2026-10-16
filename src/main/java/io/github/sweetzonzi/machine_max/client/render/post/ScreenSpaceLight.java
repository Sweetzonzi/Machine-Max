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
 * 点到光心距离达到半径时贡献严格为 0；颜色通道归一化到 {@code [0,1]}；强度是非负峰值系数，
 * 全局倍率由光源表统一乘一次，这里放的只是内容强度与时间包络的乘积。</p>
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
        /** S2：在光心的屏幕投影位置采一次深度，显著更近即整盏光跳过 */
        SOURCE_DEPTH,
        /** S3：对受光表面点到光源的线段做屏幕空间遮挡检测 */
        SCREEN_RAY
    }

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
     * @param radius                有限影响半径（m），须大于 0
     * @param color                 非负 RGB，通道归一化到 {@code [0,1]}
     * @param intensity             非负峰值系数，不含全局倍率
     * @param occlusionMode         遮挡策略；当前阶段支持 {@code NONE} 与 {@code SOURCE_DEPTH}
     * @param sourceDepthTolerance  S2 的绝对容差（m），允许 0
     * @param normalWeight          法线响应权重 {@code [0,1]}，0 表示不乘法线项
     */
    public void setPoint(Vector3f positionView, float radius, Vector3f color, float intensity,
                         OcclusionMode occlusionMode, float sourceDepthTolerance, float normalWeight) {
        this.positionView.set(positionView);
        this.radius = radius;
        this.color.set(color);
        this.intensity = intensity;
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
     * @param color                 非负 RGB，通道归一化到 {@code [0,1]}
     * @param intensity             非负峰值系数，不含全局倍率
     * @param directionView         视空间照射方向，单位向量，从灯指向照射方向
     * @param cosInner              内半角的余弦，须满足 {@code cosInner >= cosOuter}
     * @param cosOuter              外半角的余弦
     * @param occlusionMode         遮挡策略
     * @param sourceDepthTolerance  S2 的绝对容差（m），允许 0
     * @param normalWeight          法线响应权重 {@code [0,1]}
     */
    public void setSpot(Vector3f positionView, float radius, Vector3f color, float intensity,
                        Vector3f directionView, float cosInner, float cosOuter,
                        OcclusionMode occlusionMode, float sourceDepthTolerance, float normalWeight) {
        this.positionView.set(positionView);
        this.radius = radius;
        this.color.set(color);
        this.intensity = intensity;
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

    /** @return 强度峰值系数，不含全局倍率 */
    public float intensity() {
        return intensity;
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

    /** @return 法线响应权重 */
    public float normalWeight() {
        return normalWeight;
    }
}
