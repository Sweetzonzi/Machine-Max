package io.github.tt432.machinemax.common.vehicle.subsystem;

import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.AbstractSubsystemAttr;
import io.github.tt432.machinemax.common.vehicle.signal.ISignalReceiver;
import io.github.tt432.machinemax.common.vehicle.signal.ISignalSender;

import java.util.List;
import java.util.Map;

public class GearboxSubsystem extends AbstractSubsystem implements ISignalReceiver, ISignalSender {
    public GearboxSubsystem(ISubsystemHost owner, String name, AbstractSubsystemAttr attr) {
        super(owner, name, attr);
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        return Map.of();
    }
}
