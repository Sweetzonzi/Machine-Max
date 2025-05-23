package io.github.sweetzonzi.machinemax.common.vehicle;

import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;

import java.util.Map;

public interface ISubsystemHost {//持有子系统的接口，可能是部件，也可能是某些改装件？
    Part getPart();
    Map<String, AbstractSubsystem> getSubsystems();
}
