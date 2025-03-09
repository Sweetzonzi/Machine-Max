package io.github.tt432.machinemax.common.vehicle.signal;

import com.mojang.datafixers.util.Pair;

public class MotorInputSignal extends Signal<Pair<Float[], Float[]>> {
    public MotorInputSignal(Pair<Float[], Float[]> value) {
        super(value);
    }

    public MotorInputSignal(Float[] velocity, Float[] position) {
        this(Pair.of(velocity, position));
    }
}
