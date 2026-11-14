package io.github.sweetzonzi.machine_max.common.mech.signal;

import com.mojang.datafixers.util.Pair;

public class MotorControlSignal extends Signal<Pair<Float[], Float[]>> {

    public MotorControlSignal(Float[] velocity, Float[] position) {
        super(Pair.of(velocity, position));
    }

    /** 执行器线不参与乘员输入排序，越级量对它没有意义，返回自身。 */
    @Override
    public MotorControlSignal withPriority(int priority) {
        return this;
    }
}
