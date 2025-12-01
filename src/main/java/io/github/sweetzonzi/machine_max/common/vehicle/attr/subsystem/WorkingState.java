package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem;

import net.minecraft.resources.ResourceLocation;

public record WorkingState(float rpm, float load, ResourceLocation sound) {

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other instanceof WorkingState state) {
            return rpm == state.rpm && load == state.load;
        } else return false;
    }
}
