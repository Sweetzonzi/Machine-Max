package io.github.tt432.machinemax.common.vehicle.subsystem;

import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.SeatSubSystemAttr;

public class SeatSubSystem extends AbstractSubSystem {
    protected SeatSubSystem(ISubsystemHost owner, String name, SeatSubSystemAttr attr) {
        super(owner, name, attr);
    }
}
