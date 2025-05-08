package io.github.tt432.machinemax.mixin_interface;

import io.github.tt432.machinemax.common.vehicle.subsystem.SeatSubsystem;

import javax.annotation.Nullable;

public interface ILivingEntityMixin {
    @Nullable
    SeatSubsystem machine_Max$getRidingSubsystem();

    void machine_Max$setRidingSubsystem(SeatSubsystem subSystem);
}
