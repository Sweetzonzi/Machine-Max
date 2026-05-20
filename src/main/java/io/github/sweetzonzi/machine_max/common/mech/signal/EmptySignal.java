package io.github.sweetzonzi.machine_max.common.mech.signal;

public class EmptySignal extends Signal<Void> {
    public static final EmptySignal INSTANCE = new EmptySignal();

    private EmptySignal() {
        super(null);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof EmptySignal;
    }
}
