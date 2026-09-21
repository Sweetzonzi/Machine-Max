package io.github.sweetzonzi.machine_max.client.render.post;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 爆炸波前折射的视空间换算与深度线性化。
 *
 * <p>纯静态工具，不持有任何状态；渲染线程调用。换算只有两步：先平移（世界坐标减相机位置），
 * 再旋转（乘事件携带的视图旋转矩阵，该矩阵为纯旋转、不含平移）。</p>
 *
 * <p>深度不走"反投影求视空间距离"那条路（那需要原版不对外暴露的近/远平面常量），
 * 而是用两个标量系数把非线性深度线性化，对任何透视投影约定都成立。</p>
 */
public final class BlastProjectionUtil {

    private BlastProjectionUtil() {
    }

    /**
     * 世界坐标 → 视空间。视空间中 z 为负表示在相机前方。
     *
     * @param modelViewMatrix 纯旋转的视图矩阵（`RenderLevelLastEvent#getModelViewMatrix()`）
     * @param cameraPos       相机世界坐标
     * @param worldPos        待换算的世界坐标
     * @param out             结果写入的目标，允许与 worldPos 相同
     * @return out，便于链式书写
     */
    public static Vector3f toViewSpace(Matrix4f modelViewMatrix, Vec3 cameraPos, Vector3f worldPos, Vector3f out) {
        out.set(worldPos.x - (float) cameraPos.x,
                worldPos.y - (float) cameraPos.y,
                worldPos.z - (float) cameraPos.z);
        modelViewMatrix.transformPosition(out);
        return out;
    }

    /**
     * 像素焦距 f = |m11| · 画面高度 / 2。
     *
     * <p>透视投影下 m11 = 1/tan(fovY/2)，直接取矩阵元素与"由 FOV 现算"等价，
     * 且自动跟随望远镜、细雪视效、座舱视角等改变 FOV 的状态。</p>
     *
     * @param projectionMatrix 相机投影矩阵
     * @param screenHeight     画面高度（px）
     * @return 像素焦距；投影矩阵退化时返回 0
     */
    public static float focalPx(Matrix4f projectionMatrix, int screenHeight) {
        float m11 = Math.abs(projectionMatrix.m11());
        if (m11 <= 0f) return 0f;
        return m11 * screenHeight * 0.5f;
    }

    /**
     * 两点标定深度线性化系数。
     *
     * <p>标定取两个已知前向深度 {@code zNear}、{@code zFar}，把视空间点 {@code (0, 0, -z)}
     * 经当前投影矩阵投影得到非线性深度，再反解系数。系数满足
     * {@code d_tex = DepthB / z - DepthA}，其逆变换为 {@code z = DepthB / (d_tex + DepthA)}。</p>
     *
     * @param projectionMatrix 相机投影矩阵
     * @param calibZNear       标定用近点前向深度（m），须为正
     * @param calibZFar        标定用远点前向深度（m），须与近点不同
     * @return 长度 2 的数组 {@code {DepthA, DepthB}}
     */
    public static float[] calibrateDepth(Matrix4f projectionMatrix, float calibZNear, float calibZFar) {
        float d1 = projectDepth(projectionMatrix, calibZNear);
        float d2 = projectDepth(projectionMatrix, calibZFar);
        float invDiff = 1f / calibZNear - 1f / calibZFar;
        // invDiff 为 0 只可能出现在两个标定深度相等时，属调用方契约错误，此处按退化处理
        float depthB = invDiff == 0f ? 1f : (d1 - d2) / invDiff;
        float depthA = depthB / calibZNear - d1;
        return new float[]{depthA, depthB};
    }

    /**
     * 用投影矩阵把视空间点 {@code (0, 0, -z)} 投影成 [0, 1] 的非线性深度。
     *
     * @param projectionMatrix 相机投影矩阵
     * @param z                前向深度（m），为正
     */
    private static float projectDepth(Matrix4f projectionMatrix, float z) {
        Vector4f clip = projectionMatrix.transform(new Vector4f(0f, 0f, -z, 1f));
        return clip.z / clip.w * 0.5f + 0.5f;
    }
}
