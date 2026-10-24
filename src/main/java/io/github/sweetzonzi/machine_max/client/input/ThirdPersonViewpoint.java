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
 * 跟随模式保存相对乘坐参考的观察角，自由模式保存世界目标点；参考位姿由调用方提供。
 * 角度使用弧度，Vec2 的 x 为俯仰、y 为偏航。
 */
public final class ThirdPersonViewpoint {
    @Getter
    private boolean following;
    /** 跟随模式的相对观察角，安装参考转动时保持该值。 */
    private Vec2 look = Vec2.ZERO;
    /** 自由模式保持的有限世界点，同时供视点切换继承。 */
    private Vec3 worldPoint;
    /** 回正只保存显示残差，逻辑观察角和世界目标不参与衰减。 */
    @Nullable
    private Quaternionf returnFrom;
    @Nullable
    private Quaternionf returnOffset;
    private long returnStartedNanos;
    /** 回正时长（秒），与渲染帧率无关。 */
    private static final double RETURN_SECONDS = .2;

    /** 客户端主线程切入视点时继承期望目标，并换算当前乘坐参考中的观察角。 */
    public void enter(boolean follow, Quaternion reference, Vec3 origin, Vec3 inherited) {
        cancelReturn();
        following = follow;
        worldPoint = inherited;
        look = ViewMath.inherit(reference, origin, inherited);
    }

    /** 客户端主线程每次逻辑更新推进观察状态；零输入时也处理乘坐参考和观察位置变化。 */
    public ViewMath.Snapshot update(Quaternion reference, Vec3 origin, Vec2 input,
                                     AimAxisLimit yawLimit, AimAxisLimit pitchLimit) {
        if (worldPoint == null) {
            enter(following, reference, origin,
                    origin.add(new AimPose(Vec3.ZERO, reference).forward().scale(AimMath.AIM_DISTANCE_METERS)));
        }
        if (following) {
            look = new Vec2((float) pitchLimit.apply(look.x + input.x),
                    (float) yawLimit.apply(look.y + input.y));
            ViewMath.Snapshot result = ViewMath.follow(reference, look, origin);
            worldPoint = result.worldPoint();
            return result;
        }
        worldPoint = ViewMath.advanceFree(worldPoint, origin, input);
        return ViewMath.free(worldPoint, origin);
    }

    /** 主线程／渲染线程只读采样，不改变保存的相对角或世界点。 */
    public ViewMath.Snapshot sample(Quaternion reference, Vec3 origin) {
        return following ? ViewMath.follow(reference, look, origin) : ViewMath.free(worldPoint, origin);
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

    /**
     * 渲染阶段将显示残差在 0.2 秒内平滑收敛到零，输入为本帧正常观察朝向。<br>
     * 本帧正常朝向右乘残差，安装体运动和正常鼠标输入即时生效；四元数插值取最短弧。
     * 只更新显示过渡状态，不改变逻辑目标。调用方显式传入单调时钟，便于验证帧率独立性。
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

    public Vec3 worldPoint() {
        return worldPoint;
    }

    public Vec2 relativeLook() {
        return look;
    }
}
