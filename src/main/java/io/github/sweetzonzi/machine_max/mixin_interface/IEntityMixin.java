package io.github.sweetzonzi.machine_max.mixin_interface;

import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.SeatSubsystem;

import javax.annotation.Nullable;

public interface IEntityMixin {
    @Nullable
    SeatSubsystem machine_Max$getRidingSubsystem();

    void machine_Max$setRidingSubsystem(SeatSubsystem subSystem);
}
