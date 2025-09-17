package io.github.sweetzonzi.machine_max.mixin_interface;

import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.IControllableSubsystem;

import javax.annotation.Nullable;

public interface IEntityMixin {
    @Nullable
    IControllableSubsystem machine_Max$getControllingSubsystem();

    void machine_Max$setControllingSubsystem(IControllableSubsystem subSystem);
}
