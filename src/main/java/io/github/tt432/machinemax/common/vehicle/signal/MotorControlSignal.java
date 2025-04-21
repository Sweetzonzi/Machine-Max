package io.github.tt432.machinemax.common.vehicle.signal;

import com.mojang.datafixers.util.Pair;

public class MotorControlSignal extends Signal<Pair<Float[], Float[]>> {
    public MotorControlSignal(Pair<Float[], Float[]> value) {
        super(value);
    }

    public MotorControlSignal(Float[] velocity, Float[] position) {
        this(Pair.of(velocity, position));
    }
}
