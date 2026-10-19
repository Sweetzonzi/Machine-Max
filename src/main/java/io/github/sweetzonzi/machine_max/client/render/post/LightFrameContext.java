package io.github.sweetzonzi.machine_max.client.render.post;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

/**
 * 本次主视图的只读借用上下文：光源来源采集所需的帧状态全部从这里取。
 *
 * <p>实例由 {@link ScreenSpaceLightEffect} 在渲染线程每帧填一次并复用，因此没有每帧分配。
 * 来源只允许读：不得保存其中的矩阵或向量引用，它们是同一实例的自有对象，下一帧会被覆写。
 * 矩阵在 {@link #update} 时复制进本实例，所以来源持有一帧也不会读到事件对象的复用内容。</p>
 *
 * <p>投影矩阵必须与光照链取的那份世界深度快照一致：它由同一个 {@code AFTER_LEVEL} 事件携带
 * （设计文档 §4.4）。</p>
 *
 * <p>设计文档：{@code docs/plan/进行中/渲染-屏幕空间光照通用化设计.md} §4.1、§4.4。</p>
 */
public final class LightFrameContext {

    @Nullable
    private Level level;
    @Nullable
    private Vec3 cameraPos;
    private final Matrix4f modelViewMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();
    private int viewportWidth;
    private int viewportHeight;
    private float focalPx;
    private float renderDistance;
    private float partialTick;

    /**
     * 渲染线程：每帧在采集之前填入本次主视图状态。
     *
     * <p>包内可见：只有 {@link ScreenSpaceLightEffect} 是写入方，来源没有写入路径。</p>
     *
     * @param level            当前维度，用于取该维度的光源宿主（投射物管理器等）
     * @param cameraPos        相机世界坐标
     * @param modelViewMatrix  纯旋转的视图矩阵（事件携带），本次调用内复制
     * @param projectionMatrix 相机投影矩阵（已取世界 FOV 与设置值中的较大者），本次调用内复制
     * @param viewportWidth    主目标宽度（px）
     * @param viewportHeight   主目标高度（px）
     * @param focalPx          像素焦距，{@code <= 0} 表示投影退化
     * @param renderDistance   当前渲染距离（m）
     * @param partialTick      帧间插值量，与光源自身渲染取同一个值
     */
    void update(Level level, Vec3 cameraPos, Matrix4f modelViewMatrix, Matrix4f projectionMatrix,
                int viewportWidth, int viewportHeight, float focalPx, float renderDistance, float partialTick) {
        this.level = level;
        this.cameraPos = cameraPos;
        this.modelViewMatrix.set(modelViewMatrix);
        this.projectionMatrix.set(projectionMatrix);
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.focalPx = focalPx;
        this.renderDistance = renderDistance;
        this.partialTick = partialTick;
    }

    /** @return 当前维度；{@link #update} 之后非空 */
    @Nullable
    public Level level() {
        return level;
    }

    /** @return 相机世界坐标；{@link #update} 之后非空 */
    @Nullable
    public Vec3 cameraPos() {
        return cameraPos;
    }

    /** @return 纯旋转的视图矩阵（自有实例，只读） */
    public Matrix4f modelViewMatrix() {
        return modelViewMatrix;
    }

    /** @return 相机投影矩阵（自有实例，只读） */
    public Matrix4f projectionMatrix() {
        return projectionMatrix;
    }

    /** @return 主目标宽度（px） */
    public int viewportWidth() {
        return viewportWidth;
    }

    /** @return 主目标高度（px） */
    public int viewportHeight() {
        return viewportHeight;
    }

    /** @return 像素焦距；{@code <= 0} 表示本帧不可做屏幕空间剔除 */
    public float focalPx() {
        return focalPx;
    }

    /** @return 当前渲染距离（m） */
    public float renderDistance() {
        return renderDistance;
    }

    /** @return 帧间插值量 */
    public float partialTick() {
        return partialTick;
    }
}
