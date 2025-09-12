package io.github.sweetzonzi.machine_max.common.vehicle.signal;

public class EmptySignal extends Signal<Void> {
    public EmptySignal() {
        super(null);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof EmptySignal;
    }
}
