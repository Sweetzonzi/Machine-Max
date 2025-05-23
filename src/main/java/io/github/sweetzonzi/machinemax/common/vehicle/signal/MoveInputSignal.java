package io.github.sweetzonzi.machinemax.common.vehicle.signal;

import com.mojang.datafixers.util.Pair;

public class MoveInputSignal extends Signal<Pair<byte[], byte[]>> {
    public MoveInputSignal(Pair<byte[], byte[]> value) {
        super(value);
    }

    public MoveInputSignal(byte[] input, byte[] conflict) {
        super(Pair.of(input, conflict));
    }

    public byte[] getMoveInput() {
        return value.getFirst();
    }

    public byte[] getMoveInputConflict() {
        return value.getSecond();
    }
}
