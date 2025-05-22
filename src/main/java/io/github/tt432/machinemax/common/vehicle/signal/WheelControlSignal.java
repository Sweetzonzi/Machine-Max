package io.github.tt432.machinemax.common.vehicle.signal;


public class WheelControlSignal extends Signal<WheelControlSignalTemplate> {

    private WheelControlSignal(WheelControlSignalTemplate value) {
        super(value);
    }

    /**
     * <p>用于控制轮胎驱动子系统的控制信号</p>
     * <p>Control signal for {@link io.github.tt432.machinemax.common.vehicle.subsystem.WheelDriverSubsystem}</p>
     *
     * @param brakeControl    <p>0~1, 0表示不刹车，1表示刹车踩死</p> <p>0~1, 0 means not braking, 1 means fully braking</p>
     * @param steeringControl <p>期望轮胎转角，单位弧度</p> <p>Expected wheel steering angle, in radians</p>
     */
    public WheelControlSignal(float brakeControl, float steeringControl) {
        this(brakeControl, 0f, steeringControl);
    }

    /**
     * <p>用于控制轮胎驱动子系统的控制信号</p>
     * <p>Control signal for {@link io.github.tt432.machinemax.common.vehicle.subsystem.WheelDriverSubsystem}</p>
     *
     * @param brakeControl     <p>0~1, 0表示不刹车，1表示刹车踩死</p> <p>0~1, 0 means not braking, 1 means fully braking</p>
     * @param handBrakeControl <p>0~1, 同上，但控制的是手刹车</p> <p>0~1, same as brakeControl, but for parking brake</p>
     * @param steeringControl  <p>期望轮胎转角，单位弧度</p> <p>Expected wheel steering angle, in radians</p>
     */
    public WheelControlSignal(float brakeControl, float handBrakeControl, float steeringControl) {
        this(new WheelControlSignalTemplate(brakeControl, handBrakeControl, steeringControl));
    }

    public float getBrakeControl() {
        return value.brakeControl();
    }

    public float getHandBrakeControl() {
        return value.handBrakeControl();
    }

    public float getSteeringControl() {
        return value.steeringControl();
    }

    @Override
    public String toString() {
        return "brakeControl=" + getBrakeControl() + ", handBrakeControl=" + getHandBrakeControl() + ", steeringControl=" + getSteeringControl();
    }
}

record WheelControlSignalTemplate(float brakeControl, float handBrakeControl, float steeringControl) {
}
