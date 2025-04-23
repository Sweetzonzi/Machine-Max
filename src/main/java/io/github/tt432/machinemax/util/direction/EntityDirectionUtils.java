package io.github.tt432.machinemax.util.direction;


import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class EntityDirectionUtils {

    public static Vector3f translateFromVec3(Vec3 input) {
        return new Vector3f((float) input.x, (float) input.y, (float) input.z);
    }
    /**
     * 计算俯仰角（pitch）
     */
    public static float getPitch(Quaternionf rotation) {
        Vec3 forwardDirection = getForwardDirection(rotation);
        return (float) Math.asin(-forwardDirection.y);
    }

    /**
     * 计算滚转角（roll）
     */
    public static float getRoll(Quaternionf rotation) {
        Vec3 leftDirection = getLeftDirection(rotation);
        return (float) Math.asin(leftDirection.y);
    }



    /**
     * 通过两点坐标计算方向向量
     * @param start 起点坐标
     * @param end 终点坐标
     * @return 单位方向向量
     */
    public static Vec3 getDirectionVector(Vec3 start, Vec3 end) {
        // 计算方向向量
        Vec3 direction = end.subtract(start);

        // 归一化向量
        return direction.normalize();
    }

    /**
     * 基于四元数计算实体的前方方向
     * @param rotation 实体的旋转四元数
     * @return 前方方向的单位向量
     */
    public static Vec3 getForwardDirection(Quaternionf rotation) {
        // 局部坐标系的前方方向 (0, 0, 1)
        Vec3 localForward = new Vec3(0, 0, 1);
        return rotateVector(rotation, localForward);
    }

    /**
     * 基于四元数计算实体的后方方向
     * @param rotation 实体的旋转四元数
     * @return 后方方向的单位向量
     */
    public static Vec3 getBackwardDirection(Quaternionf  rotation) {
        Vec3 localForward = new Vec3(0, 0, -1);
        return rotateVector(rotation, localForward);
    }

    /**
     * 基于四元数计算实体的右侧方向
     * @param rotation 实体的旋转四元数
     * @return 右侧方向的单位向量
     */
    public static Vec3 getRightDirection(Quaternionf  rotation) {
        // 局部坐标系的右侧方向 (1, 0, 0)
        Vec3 localRight = new Vec3(-1, 0, 0);
        return rotateVector(rotation, localRight);
    }

    /**
     * 基于四元数计算实体的左侧方向
     * @param rotation 实体的旋转四元数
     * @return 左侧方向的单位向量
     */
    public static Vec3 getLeftDirection(Quaternionf  rotation) {
        Vec3 localRight = new Vec3(1, 0, 0);
        return rotateVector(rotation, localRight);
    }

    /**
     * 基于四元数计算实体的头顶方向
     * @param rotation 实体的旋转四元数
     * @return 头顶方向的单位向量
     */
    public static Vec3 getUpDirection(Quaternionf  rotation) {
        // 局部坐标系的头顶方向 (0, 1, 0)
        Vec3 localUp = new Vec3(0, 1, 0);
        return rotateVector(rotation, localUp).multiply(-1, 1, 1);
    }

    /**
     * 基于四元数计算实体的下方方向
     * @param rotation 实体的旋转四元数
     * @return 下方方向的单位向量
     */
    public static Vec3 getDownDirection(Quaternionf  rotation) {
        // 局部坐标系的头顶方向 (0, 1, 0)
        Vec3 localUp = new Vec3(0, -1, 0);
        return rotateVector(rotation, localUp).multiply(-1, 1, 1);
    }

    /**
     * 使用四元数旋转向量
     * @param rotation 旋转四元数
     * @param vector 待旋转的向量
     * @return 旋转后的向量
     */
    public static Vec3 rotateVector(Quaternionf rotation, Vec3 vector) {
        // 将 Vec3 转换为浮点坐标（Quaternionf 通常使用 float）
        float x = (float) vector.x;
        float y = (float) vector.y;
        float z = (float) vector.z;

        // 将向量转换为纯虚四元数（实部为 0）
        Quaternionf vectorQuat = new Quaternionf(x, y, z, 0.0f);

        // 计算旋转后的四元数: rotation * vectorQuat * rotation.conjugate()
        Quaternionf rotatedQuat = new Quaternionf(rotation)
                .mul(vectorQuat)
                .mul(new Quaternionf(rotation).conjugate());

        // 提取虚部作为旋转后的向量，并转换为 Vec3
        Vec3 rotatedVector = new Vec3(
                rotatedQuat.x,
                rotatedQuat.y,
                rotatedQuat.z
        );

        // 归一化向量（确保方向向量长度为 1）
        return rotatedVector.normalize();
    }

}