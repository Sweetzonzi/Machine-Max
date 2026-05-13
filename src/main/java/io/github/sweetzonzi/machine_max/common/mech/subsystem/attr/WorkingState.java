package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr;

import net.minecraft.sounds.SoundEvent;

public record WorkingState(float rpm, float load, SoundEvent sound) {

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other instanceof WorkingState state) {
            return rpm == state.rpm && load == state.load;
        } else return false;
    }
}
