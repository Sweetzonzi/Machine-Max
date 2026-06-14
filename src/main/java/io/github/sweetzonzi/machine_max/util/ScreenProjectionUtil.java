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

    /**
     * 将世界坐标点转换到摄像机局部坐标系（不投影到像素）。
     * <p>
     * 用于透视渲染时直接在相机空间摆放元素，无需经过 FOV→像素 转换。
     * 正交渲染中配合正交投影矩阵使用也可能需要。
     *
     * @param worldPoint 世界空间目标点
     * @param camera     Minecraft 渲染摄像机
     * @return float[3] {localX, localY, localZ}，localZ 为深度；
     *         目标在摄像机后方时 localZ ≤ 0
     */
    public static float[] worldToCameraLocal(Vec3 worldPoint, Camera camera) {
        Vec3 camPos = camera.getPosition();

        Vector3f camForward = new Vector3f(0, 0, -1);
        camera.rotation().transform(camForward);
        Vector3f camRight = new Vector3f(1, 0, 0);
        camera.rotation().transform(camRight);
        Vector3f camUp = new Vector3f(0, 1, 0);
        camera.rotation().transform(camUp);

        double dx = worldPoint.x - camPos.x;
        double dy = worldPoint.y - camPos.y;
        double dz = worldPoint.z - camPos.z;

        float localX = (float) (camRight.x * dx + camRight.y * dy + camRight.z * dz);
        float localY = (float) (camUp.x * dx + camUp.y * dy + camUp.z * dz);
        float localZ = (float) (camForward.x * dx + camForward.y * dy + camForward.z * dz);

        return new float[]{localX, localY, localZ};
    }

    /**
     * 计算世界方向向量相对于摄像机朝向的 pitch/yaw 角差（弧度）。
     * <p>
     * 用于 FOLLOW_TRANSFORM 元素的 poseStack 旋转：
     * 将世界方向（如 scope locator 的前方指向）与摄像机朝向做差，
     * 得到"炮镜偏离屏幕中心的角差"，直接作为 poseStack 旋转量。
     *
     * @param worldDir 世界空间方向向量（需归一化或至少方向正确）
     * @param camera   Minecraft 渲染摄像机
     * @return float[2] {pitchDiff, yawDiff}（弧度），pitch 正=上，yaw 正=右
     */
    public static float[] worldDirToCameraAngles(Vec3 worldDir, Camera camera) {
        Vector3f camForward = new Vector3f(0, 0, -1);
        camera.rotation().transform(camForward);
        Vector3f camRight = new Vector3f(1, 0, 0);
        camera.rotation().transform(camRight);
        Vector3f camUp = new Vector3f(0, 1, 0);
        camera.rotation().transform(camUp);

        // 投影到摄像机局部坐标系
        float lx = (float) (camRight.x * worldDir.x + camRight.y * worldDir.y + camRight.z * worldDir.z);
        float ly = (float) (camUp.x * worldDir.x + camUp.y * worldDir.y + camUp.z * worldDir.z);
        float lz = (float) (camForward.x * worldDir.x + camForward.y * worldDir.y + camForward.z * worldDir.z);

        float yawDiff = (float) Math.atan2(lx, lz);
        float len = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        float pitchDiff = (float) Math.asin(Math.clamp(ly / len, -1.0, 1.0));

        return new float[]{pitchDiff, yawDiff};
    }
}
