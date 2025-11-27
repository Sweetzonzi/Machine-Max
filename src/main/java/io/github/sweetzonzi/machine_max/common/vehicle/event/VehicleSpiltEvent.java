package io.github.sweetzonzi.machine_max.common.vehicle.event;

import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import lombok.Getter;

import java.util.List;

@Getter
public class VehicleSpiltEvent extends VehicleEvent {
    public final List<VehicleCore> spiltVehicles;

    public VehicleSpiltEvent(VehicleCore origin, List<VehicleCore> spiltVehicles) {
        super(origin);
        this.spiltVehicles = spiltVehicles;
    }
}
