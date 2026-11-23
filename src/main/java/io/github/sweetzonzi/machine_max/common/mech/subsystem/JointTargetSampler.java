package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.animation.anim.origin.Loop;
import com.jme3.bullet.RotationOrder;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.util.data.Axis;

/** 将动画 locator 相对静止 locator 的位姿差换算到物理关节坐标系。 */
final class JointTargetSampler {
    private static final int[] NEXT_AXIS = {1, 2, 0, 1};

    private JointTargetSampler() {
    }

    /** 单次播放到时即松手；保持末帧和循环播放在混合权重归零前仍提供目标。 */
    static boolean hasDriveTarget(Loop loop, float time, float length, float weight) {
        return weight > 0f && (loop != Loop.ONCE || time < length);
    }

    static Target sample(Transform restLocator, Transform animatedLocator,
                         Quaternion jointFrameRotation, RotationOrder rotationOrder) {
        Vector3f translation = animatedLocator.getTranslation()
                .subtract(restLocator.getTranslation());
        jointFrameRotation.inverse().toRotationMatrix().mult(translation, translation);

        Quaternion restRotation = restLocator.getRotation();
        Quaternion locatorToJoint = jointFrameRotation.inverse().mult(restRotation);
        Quaternion locatorDelta = restRotation.inverse().mult(animatedLocator.getRotation());
        Quaternion jointDelta = locatorToJoint.mult(locatorDelta).mult(locatorToJoint.inverse());
        Vector3f rotation = toEulerAngles(jointDelta.toRotationMatrix(), rotationOrder);
        return new Target(translation, rotation);
    }

    private static Vector3f toEulerAngles(com.jme3.math.Matrix3f matrix, RotationOrder order) {
        int firstAxis = switch (order) {
            case XYZ, XZY -> 0;
            case YXZ, YZX -> 1;
            case ZXY, ZYX -> 2;
        };
        int parity = switch (order) {
            case XYZ, YZX, ZXY -> 0;
            case XZY, YXZ, ZYX -> 1;
        };
        int secondAxis = NEXT_AXIS[firstAxis + parity];
        int thirdAxis = NEXT_AXIS[firstAxis - parity + 1];

        float cosine = (float) Math.hypot(matrix.get(firstAxis, firstAxis), matrix.get(secondAxis, firstAxis));
        float first;
        float second = (float) Math.atan2(-matrix.get(thirdAxis, firstAxis), cosine);
        float third;
        if (cosine > 1e-6f) {
            first = (float) Math.atan2(matrix.get(thirdAxis, secondAxis), matrix.get(thirdAxis, thirdAxis));
            third = (float) Math.atan2(matrix.get(secondAxis, firstAxis), matrix.get(firstAxis, firstAxis));
        } else {
            first = (float) Math.atan2(-matrix.get(secondAxis, thirdAxis), matrix.get(secondAxis, secondAxis));
            third = 0f;
        }
        if (parity != 0) {
            first = -first;
            second = -second;
            third = -third;
        }

        Vector3f result = new Vector3f();
        result.set(firstAxis, first);
        result.set(secondAxis, second);
        result.set(thirdAxis, third);
        return result;
    }

    static float servoTarget(Target target, Axis axis) {
        int index = axis.getValue();
        if (index < 0 || index > 5) throw new IllegalArgumentException("Not a joint axis: " + axis);
        if (index < 3) return target.translation().get(index);
        // 连接器把 YR 限位反号映射到物理关节；该轴的采样结果已经处于关节坐标系。
        return axis == Axis.YR ? target.rotation().y : -target.rotation().get(index - 3);
    }

    record Target(Vector3f translation, Vector3f rotation) {
    }
}
