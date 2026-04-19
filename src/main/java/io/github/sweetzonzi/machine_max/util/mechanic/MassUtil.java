package io.github.sweetzonzi.machine_max.util.mechanic;

import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

public class MassUtil {
    private static final float EPSILON = 1e-6f;

    public static double getEntityMass(Entity entity){
        AABB entitySize = entity.getBoundingBox();
        return 92.6 * entitySize.getXsize() * entitySize.getYsize() * entitySize.getZsize();
    }

    public static float getEquivalentMass(SubPart subPart) {
        float partMass = subPart.body.getMass();
        for (AbstractConnector connector : subPart.connectors.values()) {
            if (connector.hasPart())
                partMass += (0.3f * connector.attachedConnector.subPart.body.getMass());
        }
        partMass += 0.05f * (subPart.part.vehicle.totalMass - subPart.body.getMass());
        return partMass;
    }

    /**
     * 计算整车绕「质量中心(质心)」的主轴转动惯量倒数。
     *
     * <p>返回值含义：</p>
     * <ul>
     *     <li>x: 绕世界 X 轴的 1 / Ixx</li>
     *     <li>y: 绕世界 Y 轴的 1 / Iyy</li>
     *     <li>z: 绕世界 Z 轴的 1 / Izz</li>
     * </ul>
     *
     * <p>计算步骤：</p>
     * <ul>
     *     <li>先按各 {@link SubPart} 质量计算整车质心</li>
     *     <li>再调用 {@link #getVehicleInverseInertiaAboutPoint(VehicleCore, Vector3f)}，使用平行轴定理叠加整车惯量</li>
     *     <li>最终返回三个方向的惯量倒数，供角加速度近似计算使用</li>
     * </ul>
     *
     * @param vehicle 载具核心对象
     * @return 整车绕质心的转动惯量倒数向量；若无可用质量，返回零向量
     */
    public static Vector3f getVehicleInverseInertiaAboutCentroid(VehicleCore vehicle) {
        float totalMass = 0f;
        Vector3f weightedSum = new Vector3f();
        for (Part part : vehicle.partMap.values()) {
            for (SubPart subPart : part.subParts.values()) {
                float mass = subPart.body.getMass();
                if (mass <= EPSILON) continue;
                Vector3f pos = subPart.getPosition();
                weightedSum.addLocal(pos.mult(mass));
                totalMass += mass;
            }
        }

        // 质量接近 0 时无法得到稳定质心，直接返回零向量避免除零。
        if (totalMass <= EPSILON) return Vector3f.ZERO.clone();
        Vector3f centerOfMass = weightedSum.mult(1f / totalMass);
        return getVehicleInverseInertiaAboutPoint(vehicle, centerOfMass);
    }

    /**
     * 计算整车绕「任意参考点」的主轴转动惯量倒数。
     *
     * <p>该方法适合用于“绕指定枢轴点”的动力学近似，例如：</p>
     * <ul>
     *     <li>绕轮胎接地点估算车体俯仰/侧倾响应</li>
     *     <li>绕某安装点估算模块扭转响应</li>
     * </ul>
     *
     * <p>实现逻辑：</p>
     * <ul>
     *     <li>先取每个子刚体在世界坐标系下绕 X/Y/Z 轴的自身转动惯量分量</li>
     *     <li>再使用平行轴定理增加偏心项：m * r_perp^2</li>
     *     <li>所有子刚体求和得到整车 Ixx/Iyy/Izz，最后逐轴取倒数</li>
     * </ul>
     *
     * @param vehicle 载具核心对象
     * @param point   参考点（世界坐标，单位：方块）
     * @return 整车绕该点的转动惯量倒数向量；若某轴惯量不可用，则该轴返回 0
     */
    public static Vector3f getVehicleInverseInertiaAboutPoint(VehicleCore vehicle, Vector3f point) {
        float ixx = 0f;
        float iyy = 0f;
        float izz = 0f;

        for (Part part : vehicle.partMap.values()) {
            for (SubPart subPart : part.subParts.values()) {
                float mass = subPart.body.getMass();
                if (mass <= EPSILON) continue;

                // 1) 子刚体自身绕世界主轴的转动惯量（质心处）。
                float selfIxx = getMomentOfInertiaOnWorldAxis(subPart, Vector3f.UNIT_X);
                float selfIyy = getMomentOfInertiaOnWorldAxis(subPart, Vector3f.UNIT_Y);
                float selfIzz = getMomentOfInertiaOnWorldAxis(subPart, Vector3f.UNIT_Z);

                // 2) 平行轴项：I_point = I_com + m * d_perp^2
                Vector3f pos = subPart.getPosition();
                float dx = pos.x - point.x;
                float dy = pos.y - point.y;
                float dz = pos.z - point.z;

                ixx += selfIxx + mass * (dy * dy + dz * dz);
                iyy += selfIyy + mass * (dx * dx + dz * dz);
                izz += selfIzz + mass * (dx * dx + dy * dy);
            }
        }

        return new Vector3f(
                inverseSafe(ixx),
                inverseSafe(iyy),
                inverseSafe(izz)
        );
    }

    /**
     * 计算某个子刚体绕世界某轴的「实际转动惯量」。
     *
     * <p>物理引擎可直接给出世界系逆惯量张量 invI_world，沿轴向投影后可得到：</p>
     * <pre>I_axis = 1 / (axis^T * invI_world * axis)</pre>
     *
     * @param subPart   子零件
     * @param axisWorld 世界坐标系轴向（无需单位化，方法内会归一化）
     * @return 该轴向上的转动惯量；不可用时返回 0
     */
    public static float getMomentOfInertiaOnWorldAxis(SubPart subPart, Vector3f axisWorld) {
        Vector3f axis = axisWorld.clone().normalize();
        Matrix3f invInertiaWorld = new Matrix3f();
        subPart.body.getInverseInertiaWorld(invInertiaWorld);
        Vector3f tmp = new Vector3f();
        invInertiaWorld.mult(axis, tmp);
        float invI = axis.dot(tmp);
        if (invI <= EPSILON) return 0f;
        return 1f / invI;
    }

    /**
     * 安全求倒数，避免极小值导致数值爆炸。
     */
    private static float inverseSafe(float value) {
        return value > EPSILON ? 1f / value : 0f;
    }
}
