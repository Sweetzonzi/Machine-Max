package io.github.tt432.machinemax.common.vehicle;

import io.github.tt432.machinemax.common.vehicle.attr.PortAttr;
import lombok.Getter;

import java.util.List;

@Getter
public class SubsystemController implements IPortHost {

    public final Port VEHICLE_BUS = new Port(this, new PortAttr("VEHICLE_BUS", null, List.of(), List.of(), List.of(), List.of()));

    public SubsystemController() {

    }

}
