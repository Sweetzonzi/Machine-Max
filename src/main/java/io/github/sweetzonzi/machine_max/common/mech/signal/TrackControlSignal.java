package io.github.sweetzonzi.machine_max.common.mech.signal;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.TrackDriveSubsystem;

/**
 * 履带驱动控制信号。
 *
 * <p>走 {@code track_control} 频道，由控制器逐个发送给 {@link TrackDriveSubsystem}。</p>
 *
 * @param brakeControl    服务制动 0~1，乘以驱动的 {@code max_brake_torque}
 * @param handBrakeControl 驻车制动 0~1，乘以驱动的 {@code max_hand_brake_torque}
 * @param clutchEngagement 离合结合度 0~1，1 为结合；0 时该功率路径退出传动反馈
 */
public record TrackControlSignal(
        float brakeControl,
        float handBrakeControl,
        float clutchEngagement
) {
    public TrackControlSignal {
        brakeControl = Float.isFinite(brakeControl) ? Math.clamp(brakeControl, 0f, 1f) : 0f;
        handBrakeControl = Float.isFinite(handBrakeControl) ? Math.clamp(handBrakeControl, 0f, 1f) : 0f;
        clutchEngagement = Float.isFinite(clutchEngagement) ? Math.clamp(clutchEngagement, 0f, 1f) : 0f;
    }
}
