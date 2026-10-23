package io.github.sweetzonzi.machine_max.client.input;

import lombok.Getter;
import net.minecraft.world.phys.Vec2;

import net.minecraft.world.phys.Vec3;
import com.jme3.math.Quaternion;

import io.github.sweetzonzi.machine_max.common.mech.control.aim.*;
import io.github.sweetzonzi.machine_max.common.mech.control.view.ViewMath;

/**
 * 第三人称虚拟视点的客户端会话状态，由客户端主线程持有和推进。
 * 跟随模式保存相对乘坐参考的观察角，自由模式保存世界目标点；参考位姿由调用方提供。
 * 角度使用弧度，Vec2 的 x 为俯仰、y 为偏航。
 */
public final class ThirdPersonViewpoint {
    @Getter
    private boolean following;
    private Vec2 look = Vec2.ZERO;
    private Vec3 worldPoint;

    /** 客户端主线程切入视点时继承期望目标，并换算当前乘坐参考中的观察角。 */
    public void enter(boolean follow, Quaternion reference, Vec3 origin, Vec3 inherited) {
        following = follow;
        worldPoint = inherited;
        look = ViewMath.inherit(reference, origin, inherited);
    }
    /** 客户端主线程每次逻辑更新推进观察状态；零输入时也处理乘坐参考和观察位置变化。 */
    public ViewMath.Snapshot update(Quaternion reference, Vec3 origin, Vec2 input,
                                    AimAxisLimit yawLimit, AimAxisLimit pitchLimit) {
        if (worldPoint == null) enter(following, reference, origin,
                origin.add(new AimPose(Vec3.ZERO, reference).forward().scale(AimMath.AIM_DISTANCE_METERS)));
        if (following) {
            look = new Vec2((float) (pitchLimit.apply(look.x+input.x)), (float) (yawLimit.apply(look.y+input.y)));
            ViewMath.Snapshot result = ViewMath.follow(reference, look, origin);
            worldPoint = result.worldPoint();
            return result;
        }
        worldPoint = ViewMath.advanceFree(worldPoint, origin, input);
        return ViewMath.free(worldPoint, origin);
    }

    public Vec3 worldPoint() { return worldPoint; }
    public Vec2 relativeLook() { return look; }
}
