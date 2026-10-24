package io.github.sweetzonzi.machine_max.common.mech.control.aim;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.joints.JointEnd;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import jme3utilities.math.MyMath;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.Vec2;

/**
 * 物理线程采样方向机／高低机的参考、实际角与炮镜完整光轴。<br>
 * 支持独立两轴、YXZ 双轴一体及单轴机构；返回值复制几何，不持有活体关节。
 * 客户端只限制累计偏差，机械行程由原生关节执行，因此此处不读取行程限制。
 */
public final class JointAimGeometry {
    private JointAimGeometry() {}

    /**
     * 物理线程按 WC 已握手的机构和本次实际光轴建立正解。<br>
     * 方向机的 bodyB 是基座；独立高低机的 bodyA 是支承体、bodyB 是从动体。
     * 炮镜可位于从动体的固定后代或带后坐行程的子部件，光轴局部变换按本次实际姿态换算。
     *
     * @param yaw 方向机关节；仅高低机时为 null
     * @param pitch 高低机关节；仅方向机时为 null，一体机构与 yaw 相同
     * @param opticWorld 同一物理步的炮镜世界变换
     */
    public static AimGeometrySnapshot sample(@Nullable New6Dof yaw, @Nullable New6Dof pitch,
                                             Transform opticWorld) {
        if (yaw == null && pitch == null) {
            throw new IllegalArgumentException("机构采样至少需要一个转轴");
        }
        boolean integrated = yaw != null && yaw == pitch;
        Transform root = (yaw != null ? yaw.getBodyB() : pitch.getBodyA()).getTransform(null);
        Transform yawFrame = yaw != null ? yaw.getFrameTransform(JointEnd.B, null) : new Transform();
        Transform pitchFrame = new Transform();
        Transform opticFrame;
        if (integrated || pitch == null) {
            Transform opticLocal = MyMath.combine(opticWorld, yaw.getBodyA().getTransform(null).invert(), null);
            opticFrame = MyMath.combine(opticLocal, yaw.getFrameTransform(JointEnd.A, null).invert(), null);
        } else {
            if (yaw != null && yaw.getBodyA() != pitch.getBodyA()) {
                throw new IllegalArgumentException("方向机从动体必须是高低机支承体");
            }
            pitchFrame = pitch.getFrameTransform(JointEnd.A, null);
            if (yaw != null) {
                pitchFrame = MyMath.combine(pitchFrame, yaw.getFrameTransform(JointEnd.A, null).invert(), null);
            }
            Transform opticLocal = MyMath.combine(opticWorld, pitch.getBodyB().getTransform(null).invert(), null);
            opticFrame = MyMath.combine(opticLocal, pitch.getFrameTransform(JointEnd.B, null).invert(), null);
        }
        Vec2 actual = new Vec2(pitch == null ? 0 : pitch.getAngles(null).x * (integrated ? -1 : 1),
                yaw == null ? 0 : -yaw.getAngles(null).y);
        return new AimGeometrySnapshot(new AimPose(SparkMathKt.toVec3(root.getTranslation()), root.getRotation()),
                yawFrame, new Vector3f(0, -1, 0), pitchFrame, new Vector3f(-1, 0, 0), opticFrame,
                yaw == null ? new AimAxisLimit(false, 0, 0) : AimAxisLimit.CONTINUOUS,
                pitch == null ? new AimAxisLimit(false, 0, 0) : AimAxisLimit.CONTINUOUS, actual);
    }
}
