package io.github.sweetzonzi.machine_max.common.mech.signal;

import net.minecraft.world.phys.Vec3;

/**
 * 视角输入信号，携带瞄准点世界坐标 + 分轴稳定/偏移信息。<p>
 * 由 CameraSubsystem onTick 根据客户端注入的瞄准点和自身 staticAttribute 的稳定标志构造。
 * WeaponController 消费时按每个轴是稳定→位置控制还是无稳→增量偏移来分轴驱动炮塔。
 */
public class ViewInputSignal extends Signal<Vec3> {
    /** 世界空间瞄准点坐标（稳定轴使用） */
    public final Vec3 aimPoint;
    /** 本 tick 俯仰鼠标增量（度），无稳轴且鼠标有移动时非零 */
    public final float pitchOffsetDeg;
    /** 本 tick 偏航鼠标增量（度），无稳轴且鼠标有移动时非零 */
    public final float yawOffsetDeg;
    /** 俯仰轴是否稳定（来自摄像机 staticAttribute） */
    public final boolean pitchStabilized;
    /** 偏航轴是否稳定（来自摄像机 staticAttribute） */
    public final boolean yawStabilized;

    public ViewInputSignal(Vec3 aimPoint, float pitchOffsetDeg, float yawOffsetDeg,
                           boolean pitchStabilized, boolean yawStabilized) {
        super(aimPoint);
        this.aimPoint = aimPoint;
        this.pitchOffsetDeg = pitchOffsetDeg;
        this.yawOffsetDeg = yawOffsetDeg;
        this.pitchStabilized = pitchStabilized;
        this.yawStabilized = yawStabilized;
    }
}
