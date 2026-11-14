package io.github.sweetzonzi.machine_max.common.mech.signal;

import com.mojang.datafixers.util.Pair;

public class MoveInputSignal extends Signal<Pair<byte[], byte[]>> {
    public MoveInputSignal(Pair<byte[], byte[]> value) {
        this(value, 0);
    }

    public MoveInputSignal(Pair<byte[], byte[]> value, int priority) {
        super(value, priority);
    }

    public MoveInputSignal(byte[] input, byte[] conflict) {
        this(input, conflict, 0);
    }

    public MoveInputSignal(byte[] input, byte[] conflict, int priority) {
        super(Pair.of(input, conflict), priority);
    }

    public byte[] getMoveInput() {
        return value.getFirst();
    }

    public byte[] getMoveInputConflict() {
        return value.getSecond();
    }

    @Override
    public MoveInputSignal withPriority(int priority) {
        return priority == this.priority ? this : new MoveInputSignal(value, priority);
    }
}
