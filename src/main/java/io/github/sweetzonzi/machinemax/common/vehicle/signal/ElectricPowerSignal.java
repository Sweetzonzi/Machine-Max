package io.github.sweetzonzi.machinemax.common.vehicle.signal;

import kotlin.Pair;

public class ElectricPowerSignal extends Signal<Pair<Float, Float>> implements IPowerSignal {

    public ElectricPowerSignal(float power, float voltage) {
        super(new Pair<>(power, voltage));
    }

    @Override
    public float getPower() {
        return getValue().getFirst();
    }

    public float getVoltage() {
        return getValue().getSecond();
    }
}
