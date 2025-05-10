package io.github.tt432.machinemax.common.vehicle.signal;

import com.mojang.datafixers.util.Pair;

import java.util.Map;

public class WheelControlSignal extends Signal<Pair<Float, Float>> {

    public WheelControlSignal(Pair<Float, Float> value) {
        super(value);
    }

    public WheelControlSignal(float forwardControl, float steeringControl) {
        super(Pair.of(forwardControl, steeringControl));
    }

    public Float getForwardControl() {
        return value.getFirst();
    }

    public Float getSteeringControl() {
        return value.getSecond();
    }
}
