package io.github.sweetzonzi.machine_max.client.input;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimPose;
import io.github.sweetzonzi.machine_max.common.mech.control.view.ViewMath;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 从未偏置相机焦点解析武器目标；固定距离策略可独立替换为命中点解析。
 */
public final class AimPointResolver {
    private AimPointResolver() {
    }

    /**
     * 按焦点距离沿相机局部右轴应用俯仰偏置，生成不回写视点状态的世界目标点。
     */
    public static Vec3 resolveFixedDistance(AimPose cameraPose, Vec3 focusPoint, Vec3 anchor, float pitchBias) {
        double distance = focusPoint.distanceTo(anchor);
        if (distance < 1e-9) {
            return focusPoint;
        }
        Quaternionf targetRotation = SparkMathKt.toQuaternionf(cameraPose.rotation()).rotateX(pitchBias);
        Vector3f direction = targetRotation.transform(new Vector3f(0, 0, -1));
        return anchor.add(direction.x * distance, direction.y * distance, direction.z * distance);
    }

    /**
     * 从武器目标反算未偏置焦点，作为切入第三人称座椅视点时的一次性坐标转换。
     * 目标距离保持不变，偏置在座椅参考坐标中反向应用，实际相机旋转不参与改写。
     */
    public static Vec3 resolveFocusFromAim(Quaternion reference, Vec3 aimPoint, Vec3 anchor, float pitchBias) {
        double distance = aimPoint.distanceTo(anchor);
        if (distance < 1e-9) {
            return aimPoint;
        }
        Vec2 aimAngles = ViewMath.inherit(reference, anchor, aimPoint);
        Vec2 focusAngles = new Vec2(aimAngles.x - pitchBias, aimAngles.y);
        AimPose focusPose = new AimPose(anchor, reference.mult(ViewMath.lookRotation(focusAngles)));
        return anchor.add(focusPose.forward().scale(distance));
    }
}
