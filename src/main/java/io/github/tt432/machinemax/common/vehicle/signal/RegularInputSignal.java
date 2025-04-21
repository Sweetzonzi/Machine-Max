package io.github.tt432.machinemax.common.vehicle.signal;

import com.mojang.datafixers.util.Pair;
import io.github.tt432.machinemax.util.data.KeyInputMapping;

public class RegularInputSignal extends Signal<Pair<KeyInputMapping, Integer>> {
    public RegularInputSignal(Pair<KeyInputMapping, Integer> value) {
        super(value);
    }

    public RegularInputSignal(KeyInputMapping inputType, int inputTickCount) {
        super(Pair.of(inputType, inputTickCount));
    }

    public KeyInputMapping getInputType() {
        return value.getFirst();
    }

    public int getInputTickCount() {
        return value.getSecond();
    }
}
