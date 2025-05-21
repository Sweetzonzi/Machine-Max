package io.github.tt432.machinemax.common.vehicle.signal;

import com.mojang.datafixers.util.Pair;

import java.util.Map;

public class WheelControlSignal extends Signal<Pair<Float, Float>> {

    public WheelControlSignal(Pair<Float, Float> value) {
        super(value);
    }

    public WheelControlSignal(Float forwardControl, Float steeringControl) {
        super(Pair.of(forwardControl, steeringControl));
    }

    public Float getForwardControl() {
        return value.getFirst();
    }

    public Float getSteeringControl() {
        return value.getSecond();
    }

    @Override
    public String toString() {
        return "forwardControl=" + getForwardControl() + ", steeringControl=" + getSteeringControl();
    }
}
