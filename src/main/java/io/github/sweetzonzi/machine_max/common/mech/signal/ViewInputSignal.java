package io.github.sweetzonzi.machine_max.common.mech.signal;

import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimTargetState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * 主线程按控制组发送的完整瞄准目标，物理线程按值覆盖。
 * 世界点使用双精度；relativeAim 是无稳轴的绝对相对角，弧度，x=俯仰、y=偏航。
 */
public final class ViewInputSignal extends Signal<Vec3> {
    /** 世界目标点，同时作为 Signal 的值供世界点接收者读取。 */
    public final Vec3 aimPoint;
    /** 无稳轴保持的绝对相对角；重复读取不能累计成增量。 */
    public final Vec2 relativeAim;
    /** 指定哪些轴使用世界点求解，哪些轴使用 relativeAim。 */
    public final AimTargetState.Mode mode;

    public ViewInputSignal(Vec3 point, Vec2 relativeAim, AimTargetState.Mode mode) {
        super(point);
        this.aimPoint = point;
        this.relativeAim = relativeAim;
        this.mode = mode;
    }
}
