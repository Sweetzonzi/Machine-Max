package io.github.sweetzonzi.machine_max.mixin_interface;

import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractControllableSubsystem;

import javax.annotation.Nullable;

public interface IEntityMixin {
    @Nullable
    AbstractControllableSubsystem machine_Max$getControllingSubsystem();

    void machine_Max$setControllingSubsystem(AbstractControllableSubsystem subSystem);
}
