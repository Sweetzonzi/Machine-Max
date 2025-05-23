package io.github.sweetzonzi.machinemax.common.vehicle.signal;

import lombok.Getter;

@Getter
abstract public class Signal<T> {
    public final T value;

    public Signal(T value) {
        this.value = value;
    }

}
