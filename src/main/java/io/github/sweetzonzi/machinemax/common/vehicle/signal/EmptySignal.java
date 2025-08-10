package io.github.sweetzonzi.machinemax.common.vehicle.signal;

public class EmptySignal extends Signal<Void> {
    public EmptySignal() {
        super(null);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof EmptySignal;
    }
}
