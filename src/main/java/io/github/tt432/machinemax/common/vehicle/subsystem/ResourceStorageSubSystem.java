package io.github.tt432.machinemax.common.vehicle.subsystem;

import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.ResourceStorageSubSystemAttr;
import lombok.Getter;

@Getter
public class ResourceStorageSubSystem extends AbstractSubSystem {
    public final String resource;
    public final float capacity;
    public final float initialCapacity;
    public final boolean shared;

    public ResourceStorageSubSystem(ISubsystemHost owner, String name, ResourceStorageSubSystemAttr attr) {
        super(owner, name, attr);
        this.resource = attr.getResourceType();
        this.capacity = attr.getMaxCapacity();
        this.initialCapacity = attr.getInitialCapacity();
        this.shared = attr.isShared();
    }

}
