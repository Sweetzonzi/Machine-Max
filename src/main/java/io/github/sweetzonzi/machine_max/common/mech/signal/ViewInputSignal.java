package io.github.sweetzonzi.machine_max.common.mech.signal;

import net.minecraft.world.phys.Vec3;

/**
 * 视角输入信号，携带玩家瞄准点的世界坐标 Vec3。
 * 由客户端的 CameraController 每 tick 计算并通过 ViewInputPayload 发送到服务端，
 * 最终通过 SeatSubsystem.setViewInputSignal() 注入到 viewSignalTargets 信号频道中。
 * 接收端（如 FireControlSubsystem）从 targetInputs 频道读取该信号以获取目标坐标。
 */
public class ViewInputSignal extends Signal<Vec3> {

    public ViewInputSignal(Vec3 aimPoint) {
        super(aimPoint);
    }
}
