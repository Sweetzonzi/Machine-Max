package io.github.tt432.machinemax.common.vehicle;

import io.github.tt432.machinemax.common.vehicle.subsystem.AbstractSubsystem;

import java.util.Map;

public interface ISubsystemHost {
    Part getPart();
    Map<String, AbstractSubsystem> getSubsystems();
}
