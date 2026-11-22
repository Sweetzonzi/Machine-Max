package io.github.sweetzonzi.machine_max.client.input;

import com.jme3.math.Quaternion;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimAxisLimit;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimMath;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimPose;
import io.github.sweetzonzi.machine_max.common.mech.control.view.ViewMath;
import lombok.Getter;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * 第三人称虚拟视点的客户端会话状态，由客户端主线程持有和推进。
 * 跟随模式保存相对乘坐参考的相机观察角，自由模式保存未偏置的世界焦点；参考位姿由调用方提供。
 * 角度使用弧度，Vec2 的 x 为俯仰、y 为偏航。
 */
public final class ThirdPersonViewpoint {
    @Getter
    private boolean following;
    /** 跟随模式的相对观察角，安装参考转动时保持该值。 */
    private Vec2 look = Vec2.ZERO;
    private float pitchBias;
    private double followDistance = AimMath.AIM_DISTANCE_METERS;
    /** 唯一持久的有限相机焦点；武器偏置目标只在调用方输出时解析。 */
    private Vec3 focusPoint;
    /** 回正只保存显示残差，逻辑观察角和世界目标不参与衰减。 */
    @Nullable
    private Quaternionf returnFrom;
    @Nullable
    private Quaternionf returnOffset;
    private long returnStartedNanos;
    /** 回正时长（秒），与渲染帧率无关。 */
    private static final double RETURN_SECONDS = .2;

    /** 客户端主线程切入视点时继承未偏置焦点，并换算当前乘坐参考中的相机观察角。 */
    public void enter(boolean follow, Quaternion reference, Vec3 origin, Vec3 inherited, float pitchBias) {
        enter(follow, reference, origin, inherited, pitchBias, true);
    }

    /** 第一人称可选择保留既有固定距离；继承焦点时保留其距离连续性。 */
    public void enter(boolean follow, Quaternion reference, Vec3 origin, Vec3 inherited, float pitchBias,
                      boolean inheritFollowDistance) {
        cancelReturn();
        following = follow;
        this.pitchBias = pitchBias;
        focusPoint = inherited;
        double inheritedDistance = inherited == null || !inheritFollowDistance
                ? AimMath.AIM_DISTANCE_METERS : inherited.distanceTo(origin);
        followDistance = inheritedDistance < 1e-9 ? AimMath.AIM_DISTANCE_METERS : inheritedDistance;
        if (inherited == null) {
            look = Vec2.ZERO;
        } else {
            look = ViewMath.inherit(reference, origin, inherited);
        }
    }

    /** 客户端主线程每次逻辑更新推进观察状态；零输入时也处理乘坐参考和观察位置变化。 */
    public ViewMath.Snapshot update(Quaternion reference, Vec3 origin, Vec2 input,
                                     AimAxisLimit yawLimit, AimAxisLimit pitchLimit) {
        AimAxisLimit operatorPitchLimit = pitchLimit.continuous() ? pitchLimit
                : new AimAxisLimit(false, pitchLimit.min() - pitchBias, pitchLimit.max() - pitchBias);
        if (focusPoint == null) {
            float initialPitch = (float) operatorPitchLimit.apply(0);
            Quaternion neutralRotation = reference.mult(ViewMath.lookRotation(new Vec2(initialPitch, 0)));
            focusPoint = origin.add(new AimPose(Vec3.ZERO, neutralRotation).forward()
                    .scale(followDistance));
        }
        if (following) {
            look = new Vec2((float) operatorPitchLimit.apply(look.x + input.x),
                    (float) yawLimit.apply(look.y + input.y));
            ViewMath.Snapshot result = ViewMath.follow(reference, look, origin, followDistance);
            focusPoint = result.focusPoint();
            return result;
        }
        focusPoint = ViewMath.advanceFree(focusPoint, origin, input);
        AimPose pose = new AimPose(origin, ViewMath.lookAt(reference, origin, focusPoint));
        return new ViewMath.Snapshot(pose, focusPoint);
    }

    /** 主线程／渲染线程只读采样，不改变保存的相对角或未偏置焦点。 */
    public ViewMath.Snapshot sample(Quaternion reference, Vec3 origin) {
        return following
                ? ViewMath.follow(reference, look, origin, followDistance)
                : new ViewMath.Snapshot(new AimPose(origin, ViewMath.lookAt(reference, origin, focusPoint)), focusPoint);
    }

    /** 主线程在退出临时观察时保存最后显示朝向，首个显示帧建立相对正常视野的残差。 */
    public void beginReturn(Quaternionf displayed) {
        returnFrom = new Quaternionf(displayed);
        returnOffset = null;
    }

    /** 切换观察来源或离座时清空显示过渡。 */
    public void cancelReturn() {
        returnFrom = null;
        returnOffset = null;
        returnStartedNanos = 0;
    }

    /** 判断自由观察退出后的显示回正是否仍在进行。 */
    public boolean isReturning() {
        return returnFrom != null || returnOffset != null;
    }

    /**
     * 渲染阶段将显示残差在 0.2 秒内平滑收敛到零，输入为本帧正常观察朝向。<br>
     * 本帧正常朝向右乘残差，安装体运动和正常鼠标输入即时生效；四元数插值取最短弧。
     * 只更新显示过渡状态，不改变逻辑焦点。调用方显式传入单调时钟，便于验证帧率独立性。
     */
    public Quaternionf sampleDisplayRotation(Quaternionf desired, long nowNanos) {
        if (returnFrom != null) {
            returnOffset = new Quaternionf(desired).conjugate().mul(returnFrom).normalize();
            returnFrom = null;
            returnStartedNanos = nowNanos;
        }
        if (returnOffset == null) {
            return new Quaternionf(desired);
        }
        float t = (float) Math.clamp((nowNanos - returnStartedNanos) / (RETURN_SECONDS * 1e9), 0, 1);
        if (t >= 1) {
            cancelReturn();
            return new Quaternionf(desired);
        }
        // smoothstep 让开始和结束处的回正速度为零，正常观察本身保持即时响应。
        float blend = t * t * (3 - 2 * t);
        return new Quaternionf(desired).mul(new Quaternionf(returnOffset).slerp(new Quaternionf(), blend)).normalize();
    }

    public Vec3 focusPoint() {
        return focusPoint;
    }

    public Vec2 relativeLook() {
        return look;
    }
}
