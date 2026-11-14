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

    /** 空信号不参与排序，越级量对它没有意义，返回自身。 */
    @Override
    public EmptySignal withPriority(int priority) {
        return this;
    }
}
