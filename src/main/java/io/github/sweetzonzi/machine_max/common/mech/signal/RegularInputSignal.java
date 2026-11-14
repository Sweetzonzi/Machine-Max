package io.github.sweetzonzi.machine_max.common.mech.signal;

import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.util.data.KeyInputMapping;

public class RegularInputSignal extends Signal<Pair<KeyInputMapping, Integer>> {
    public RegularInputSignal(Pair<KeyInputMapping, Integer> value) {
        this(value, 0);
    }

    public RegularInputSignal(Pair<KeyInputMapping, Integer> value, int priority) {
        super(value, priority);
    }

    public RegularInputSignal(KeyInputMapping inputType, int inputTickCount) {
        this(inputType, inputTickCount, 0);
    }

    public RegularInputSignal(KeyInputMapping inputType, int inputTickCount, int priority) {
        super(Pair.of(inputType, inputTickCount), priority);
    }

    public KeyInputMapping getInputType() {
        return value.getFirst();
    }

    public int getInputTickCount() {
        return value.getSecond();
    }

    @Override
    public RegularInputSignal withPriority(int priority) {
        return priority == this.priority ? this : new RegularInputSignal(value, priority);
    }
}
