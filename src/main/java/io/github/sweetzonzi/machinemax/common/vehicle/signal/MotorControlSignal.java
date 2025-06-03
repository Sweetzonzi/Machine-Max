package io.github.sweetzonzi.machinemax.common.vehicle.signal;

import com.mojang.datafixers.util.Pair;

public class MotorControlSignal extends Signal<Pair<Float[], Float[]>> {

    public MotorControlSignal(Float[] velocity, Float[] position) {
        super(Pair.of(velocity, position));
    }
}
