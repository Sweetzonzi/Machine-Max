package io.github.sweetzonzi.machine_max.util;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * 屏幕投影工具：将世界坐标点通过摄像机位姿和 FOV 投影到屏幕空间。
 * <p>
 * 输入世界坐标 + Minecraft 渲染摄像机 + 垂直 FOV + 屏幕尺寸，
 * 输出相对于屏幕中心的像素偏移（GUI 坐标系：右为正、下为正）。
 * <br>
 * 可用于 HUD 准星偏移、3D 标记投影等场景。
 * 遵循客户端 JOML 约定，不使用 JME 数学类型。
 */
public final class ScreenProjectionUtil {

    private ScreenProjectionUtil() {}

    /**
     * 将世界坐标点投影到屏幕空间，返回相对于屏幕中心的像素偏移。
     *
     * @param worldPoint  世界空间中的目标点
     * @param camera      Minecraft 渲染摄像机（提供位置和朝向）
     * @param vFov        垂直视场角（度）
     * @param screenWidth  屏幕宽度（像素）
     * @param screenHeight 屏幕高度（像素）
     * @return float[2] {offsetX, offsetY}，offsetX 正=右、offsetY 正=下（GUI 坐标系）；
     *         若目标点在摄像机后方则返回 {@code null}
     */
    public static float @Nullable [] worldToScreenOffset(Vec3 worldPoint, Camera camera,
                                                         float vFov, int screenWidth, int screenHeight) {
        Vec3 camPos = camera.getPosition();

        // 摄像机局部坐标系基向量（世界空间）
        Vector3f camForward = new Vector3f(0, 0, -1);
        camera.rotation().transform(camForward);
        Vector3f camRight = new Vector3f(1, 0, 0);
        camera.rotation().transform(camRight);
        Vector3f camUp = new Vector3f(0, 1, 0);
        camera.rotation().transform(camUp);

        // 摄像机 → 目标点的向量
        double dx = worldPoint.x - camPos.x;
        double dy = worldPoint.y - camPos.y;
        double dz = worldPoint.z - camPos.z;

        // 投影到摄像机局部坐标系
        float localX = (float) (camRight.x * dx + camRight.y * dy + camRight.z * dz);
        float localY = (float) (camUp.x * dx + camUp.y * dy + camUp.z * dz);
        float localZ = (float) (camForward.x * dx + camForward.y * dy + camForward.z * dz);

        // 目标点在摄像机后方，不可见
        if (localZ <= 0f) return null;

        // 透视投影：焦距（像素）= 半屏高 / tan(半FOV)
        float vFovHalfRad = (float) Math.toRadians(vFov / 2f);
        float focalLengthPx = (screenHeight / 2f) / (float) Math.tan(vFovHalfRad);

        // 屏幕偏移 = 焦距 × (横向分量 / 深度)，无需 atan→tan 来回转换
        // GUI Y 轴向下，故 vertical 取反
        float offsetX = focalLengthPx * (localX / localZ);
        float offsetY = -focalLengthPx * (localY / localZ);

        return new float[]{offsetX, offsetY};
    }
}
