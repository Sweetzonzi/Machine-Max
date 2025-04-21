package io.github.tt432.machinemax.common.vehicle.subsystem;

import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.ResourceStorageSubsystemAttr;
import lombok.Getter;
import net.neoforged.neoforge.items.ItemStackHandler;

@Getter
public class ResourceStorageSubsystem extends AbstractSubsystem {
    public final String resource;
    public final float capacity;
    public final float initialCapacity;
    public final boolean shared;
    public ItemStackHandler inventory;
    public ResourceStorageSubsystem(ISubsystemHost owner, String name, ResourceStorageSubsystemAttr attr) {
        super(owner, name, attr);
        this.resource = attr.getResourceType();
        this.capacity = attr.getMaxCapacity();
        this.initialCapacity = attr.getInitialCapacity();
        this.shared = attr.isShared();
    }

}
