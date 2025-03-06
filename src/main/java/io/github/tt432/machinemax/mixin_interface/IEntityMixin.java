package io.github.tt432.machinemax.mixin_interface;

import io.github.tt432.machinemax.common.vehicle.subsystem.AbstractSubsystem;

import javax.annotation.Nullable;

public interface IEntityMixin {
    @Nullable
    AbstractSubsystem getRidingSubsystem();

    void setRidingSubsystem(AbstractSubsystem subSystem);

}
