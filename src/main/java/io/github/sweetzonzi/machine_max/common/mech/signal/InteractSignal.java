package io.github.sweetzonzi.machine_max.common.mech.signal;

import net.minecraft.world.entity.LivingEntity;

public class InteractSignal extends Signal<LivingEntity> {

    public InteractSignal(LivingEntity entity) {
        super(entity);
    }

    public LivingEntity getEntity() {
        return value;
    }

    /** 交互线不参与乘员输入排序，越级量对它没有意义，返回自身。 */
    @Override
    public InteractSignal withPriority(int priority) {
        return this;
    }
}