package io.github.tt432.machinemax.common.vehicle.subsystem;

import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.signal.ISignalReceiver;
import io.github.tt432.machinemax.common.vehicle.signal.ISignalSender;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.AbstractSubsystemAttr;
import io.github.tt432.machinemax.common.vehicle.signal.Signals;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Getter
abstract public class AbstractSubsystem {

    public final String name;
    public final AbstractSubsystemAttr subSystemAttr;
    public final ISubsystemHost owner;

    public final Map<String, Map<String, ISignalReceiver>> targets = new HashMap<>();
    public final ConcurrentMap<String, Signals> signalInputs = new ConcurrentHashMap<>();
    public final ConcurrentMap<String, Float> resourceInputs = new ConcurrentHashMap<>();
    public final ConcurrentMap<String, Float> resourceOutputs = new ConcurrentHashMap<>();

    protected AbstractSubsystem(ISubsystemHost owner, String name, AbstractSubsystemAttr attr) {
        this.owner = owner;
        this.subSystemAttr = attr;
        this.name = name;
        if (this instanceof ISignalSender signalSender) {signalSender.resetSignalOutputs();}
    }

    public void onTick() {}

    public void onPhysicsTick() {}

    public void onAttach() {}

    public void onDetach() {}

    public void onDisabled() {}

    public Part getPart() {
        if (owner instanceof Part part) return part;
        else return null;
    }
}
