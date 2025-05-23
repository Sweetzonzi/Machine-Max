package io.github.sweetzonzi.machinemax.common.vehicle.signal;

import kotlin.Pair;

public class MechPowerSignal extends Signal<Pair<Float, Float>> implements IPowerSignal {
    public MechPowerSignal(Pair<Float, Float> value) {
        super(value);
    }

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
