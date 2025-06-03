package io.github.sweetzonzi.machinemax.common.vehicle.signal;

import kotlin.Pair;

public class MechPowerSignal extends Signal<Pair<Float, Float>> implements IPowerSignal {
    /**
     * <p>创建一个机械功率信号，包含功率和速度</p>
     * <p>Create a mech power signal, containing power and speed</p>
     * @param power 功率，正值代表加速，负值代表减速 Power, positive values represent acceleration, negative values represent deceleration
     * @param speed 速度，正负代表方向 Speed, positive and negative values represent direction
     */
    public MechPowerSignal(float power, float speed) {
        super(new Pair<>(power, speed));
    }

    @Override
    public float getPower() {
        return getValue().getFirst();
    }

    public float getSpeed() {
        return getValue().getSecond();
    }

    public String toString() {
        return "MechPowerSignal{" +
                "power=" + getPower() +
                ", speed=" + getSpeed() +
                '}';
    }

}
