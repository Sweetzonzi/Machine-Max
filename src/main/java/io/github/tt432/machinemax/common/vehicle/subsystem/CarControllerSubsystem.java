package io.github.tt432.machinemax.common.vehicle.subsystem;

import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.CarControllerSubsystemAttr;
import io.github.tt432.machinemax.common.vehicle.signal.ISignalReceiver;
import io.github.tt432.machinemax.common.vehicle.signal.ISignalSender;

import java.util.List;
import java.util.Map;

public class CarControllerSubsystem extends AbstractSubsystem implements ISignalReceiver, ISignalSender {
    public final CarControllerSubsystemAttr attr;

    public CarControllerSubsystem(ISubsystemHost owner, String name, CarControllerSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        return Map.of();
    }
}
