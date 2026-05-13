package io.github.sweetzonzi.machine_max.common.mech.vehicle.event;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import lombok.Getter;
import net.neoforged.bus.api.Event;
@Getter
public abstract class VehicleEvent extends Event {
    private final VehicleCore vehicle;

    protected VehicleEvent(VehicleCore vehicle) {
        this.vehicle = vehicle;
    }
}
