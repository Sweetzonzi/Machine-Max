package io.github.sweetzonzi.machine_max.common.mech.control.view;

import net.minecraft.world.phys.Vec2;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.*;
import net.minecraft.world.phys.Vec3;
import jme3utilities.math.MyQuaternion;

/**
 * 观察姿态的纯函数，参考旋转与逻辑观察原点均由调用方显式提供。
 * 不推断武器拓扑；角度使用弧度，Vec2 的 x 为俯仰、y 为偏航。
 * 调用线程：主线程或物理线程，不持有共享可变状态。
 */
public final class ViewMath {
    private ViewMath() {}
    public record Snapshot(AimPose pose, Vec3 worldPoint) {}

    public static Quaternion lookRotation(Vec2 angles) {
        return new Quaternion().fromAngleNormalAxis((float) -angles.y, Vector3f.UNIT_Y)
                .mult(new Quaternion().fromAngleNormalAxis((float) angles.x, Vector3f.UNIT_X));
    }
    public static Vec2 directionAngles(Vec3 direction) {
        Vec3 d = direction.normalize();
        return new Vec2((float) (Math.asin(Math.clamp(d.y, -1, 1))), (float) (Math.atan2(d.x, -d.z)));
    }
    public static Vec2 inherit(Quaternion reference, Vec3 origin, Vec3 point) {
        Vec3 direction = point.subtract(origin);
        return direction.length() < 1e-9 ? Vec2.ZERO : directionAngles(
                SparkMathKt.toVec3(MyQuaternion.rotateInverse(reference,PhysicsHelperKt.toBVector3f(direction),null)));
    }
    public static Snapshot follow(Quaternion reference, Vec2 look, Vec3 origin) {
        AimPose pose = new AimPose(origin, reference.mult(lookRotation(look)));
        return new Snapshot(pose, origin.add(pose.forward().scale(AimMath.AIM_DISTANCE_METERS)));
    }
    public static Snapshot free(Vec3 point, Vec3 origin) {
        Vec3 direction = point.subtract(origin);
        Vec2 angles = direction.length() < 1e-9 ? Vec2.ZERO : directionAngles(direction);
        return new Snapshot(new AimPose(origin, lookRotation(angles)), point);
    }
    public static Vec3 advanceFree(Vec3 point, Vec3 origin, Vec2 delta) {
        if ((delta.x == 0 && delta.y == 0)) return point;
        Vec2 q = inherit(Quaternion.IDENTITY,origin,point);
        Vec2 next = new Vec2((float) (Math.clamp(q.x+delta.x, -Math.PI/2+1e-6, Math.PI/2-1e-6)), (float) (q.y+delta.y));
        return origin.add(new AimPose(Vec3.ZERO,lookRotation(next)).forward().scale(AimMath.AIM_DISTANCE_METERS));
    }
}
