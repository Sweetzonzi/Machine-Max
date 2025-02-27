package io.github.tt432.machinemax.common.vehicle.subsystem;

import io.github.tt432.machinemax.common.vehicle.IPortHost;
import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.Port;
import io.github.tt432.machinemax.common.vehicle.attr.PortAttr;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.AbstractSubSystemAttr;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Getter
abstract public class AbstractSubSystem implements IPortHost {

    private final String name;
    private final ConcurrentMap<String, Port> ports = new ConcurrentHashMap<>();//接口资源/信号传输端口
    private final AbstractSubSystemAttr subSystemAttr;
    private final ISubsystemHost owner;

    protected AbstractSubSystem(ISubsystemHost owner,String name, AbstractSubSystemAttr attr) {
        this.owner = owner;
        this.subSystemAttr = attr;
        this.name = name;
        for (Map.Entry<String, PortAttr> entry : attr.getPortAttrs().entrySet()) {
            ports.put(entry.getKey(), new Port(this, entry.getValue()));
        }
    }

    public Part getPart() {
        if(owner instanceof Part) return (Part) owner;
        else return null;
    }
}
