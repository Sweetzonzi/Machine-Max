package io.github.sweetzonzi.machine_max.common.mech.vehicle.signal;

import net.minecraft.world.entity.LivingEntity;

public class InteractSignal extends Signal<LivingEntity> {

    public InteractSignal(LivingEntity entity) {
        super(entity);
    }

    public LivingEntity getEntity() {
        return value;
    }
}