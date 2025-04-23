package io.github.tt432.machinemax.util.air;



import io.github.tt432.machinemax.util.direction.EntityDirectionUtils;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class Aerodynamics { //空气动力学
    // 常量
    private static final double AIR_DENSITY = 1.225; // 空气密度 (kg/m^3)
    private static final double REFERENCE_AREA = 10.0; // 参考面积 (m^2)
    private static final double CL0 = 0.1; // 基础升力系数
    private static final double CD0 = 0.01; // 基础阻力系数
    private static final double KL = 0.1; // 升力系数比例
    private static final double KD = 0.05; // 阻力系数比例

    /**
     * 计算升力
     */
    public static Vec3 calculateLift(Vec3 velocity, Quaternionf rotation) {
        // 获取攻角
        float angleOfAttack = getAngleOfAttack(velocity, rotation);
        //接近3.14为垂直下坠
        //接近0为垂直上升

        // 计算升力系数
        double liftCoefficient = CL0 + KL * angleOfAttack;

        // 计算升力大小
        double velocityMagnitude = velocity.length();
        double liftMagnitude = 0.5 * AIR_DENSITY * velocityMagnitude * velocityMagnitude * REFERENCE_AREA * liftCoefficient;

        // 获取正上方方向向量
        Vec3 upDirection = EntityDirectionUtils.getUpDirection(rotation);

        // 升力方向垂直于速度方向
        Vec3 liftDirection = upDirection.cross(velocity).normalize();

        // 计算升力向量
        Vec3 liftForce = liftDirection.scale(liftMagnitude);

        return filterNaN(liftForce);
    }

    /**
     * 计算阻力
     */
    public static Vec3 calculateDrag(Vec3 velocity, Quaternionf rotation) {
        // 获取攻角
        float angleOfAttack = getAngleOfAttack(velocity, rotation);

        // 计算阻力系数
        double dragCoefficient = CD0 + KD * angleOfAttack * angleOfAttack;

        // 计算阻力大小
        double velocityMagnitude = velocity.length();
        double dragMagnitude = 0.5 * AIR_DENSITY * velocityMagnitude * velocityMagnitude * REFERENCE_AREA * dragCoefficient;

        // 阻力方向与速度方向相反
        Vec3 dragDirection = velocity.normalize().scale(-1);

        // 计算阻力向量
        Vec3 dragForce = dragDirection.scale(dragMagnitude);

        return filterNaN(dragForce);
    }

    /**
     * 涡环状态检测：当直升机快速下降且攻角过大时，触发涡环状态，急剧降低升力。
     */
    public static boolean isVortexRingState(Vec3 velocity, Quaternionf rotation) {
        Vec3 upDirection = EntityDirectionUtils.getUpDirection(rotation);
        double descentRate = -velocity.dot(upDirection); // 下降速率为正
        return (descentRate > 5.0) && (getAngleOfAttack(velocity, rotation) > Math.toRadians(20));
    }


    /**
     * 计算攻角（速度方向与旋翼法线方向的夹角）
     */
    public static float getAngleOfAttack(Vec3 velocity, Quaternionf rotation) {
        // 获取旋翼平面法线方向（Z 轴方向）
        Vec3 rotorNormal = EntityDirectionUtils.getUpDirection(rotation);

        // 计算速度方向与旋翼法线方向的夹角
        double dotProduct = velocity.dot(rotorNormal);
        double velocityMag = velocity.length();
        double rotorMag = rotorNormal.length();

        // 避免除以零
        if (velocityMag == 0 || rotorMag == 0) return 0f;

        double cosTheta = dotProduct / (velocityMag * rotorMag);
        float angle = (float) Math.acos(cosTheta);
        return Float.isNaN(angle) ? 0f : angle;
    }


    /**
     * 判断旋翼迎风方向
     */
    public static void checkRotorWindDirection(Vec3 velocity, Quaternionf rotation) {
        // 获取旋翼平面法线方向（Z 轴方向）
        Vec3 rotorNormal = EntityDirectionUtils.getUpDirection(rotation);

        // 计算速度在旋翼法线方向上的投影
        double projection = velocity.dot(rotorNormal) / rotorNormal.length();

        if (projection > 1e-6) {
            // 速度方向与旋翼法线方向同向：旋翼上面迎风（例如直升机爬升）
            System.out.println("旋翼上面迎风（爬升状态）");
        } else if (projection < -1e-6) {
            // 速度方向与旋翼法线方向反向：旋翼下面迎风（例如直升机俯冲）
            System.out.println("旋翼下面迎风（俯冲状态）");
        } else {
            // 速度方向与旋翼平面平行（攻角为90度）
            System.out.println("速度与旋翼平面平行（侧飞）");
        }
    }


    /**
     * 旋转向量
     */
    public static Vec3 rotateVector(Quaternionf rotation, Vec3 vector) {
        float x = (float) vector.x;
        float y = (float) vector.y;
        float z = (float) vector.z;

        Quaternionf vectorQuat = new Quaternionf(x, y, z, 0.0f);
        Quaternionf rotatedQuat = new Quaternionf(rotation)
                .mul(vectorQuat)
                .mul(new Quaternionf(rotation).conjugate());

        Vec3 rotatedVector = new Vec3(rotatedQuat.x, rotatedQuat.y, rotatedQuat.z);
        return rotatedVector.normalize();
    }

    private static Vec3 filterNaN(Vec3 vector) {
        // 检查并替换 x 分量中的 NaN 值
        double x = Double.isNaN(vector.x) ? 0.0 : vector.x;
        // 检查并替换 y 分量中的 NaN 值
        double y = Double.isNaN(vector.y) ? 0.0 : vector.y;
        // 检查并替换 z 分量中的 NaN 值
        double z = Double.isNaN(vector.z) ? 0.0 : vector.z;

        // 创建并返回一个新的 Vec3 对象，其 NaN 值已被替换为 0
        return new Vec3(x, y, z);
    }


    public static void main(String[] args) {
        // 示例：定义速度和旋转
        Vec3 velocity = new Vec3(50, 10, 0); // 速度向量 (m/s)
        Quaternionf rotation = new Quaternionf().rotateXYZ(0.5f, 0.3f, 0.2f); // 旋转四元数

        // 计算升力和阻力
        Vec3 liftForce = calculateLift(velocity, rotation);
        Vec3 dragForce = calculateDrag(velocity, rotation);

        System.out.println("Lift Force: " + liftForce);
        System.out.println("Drag Force: " + dragForce);
    }
}