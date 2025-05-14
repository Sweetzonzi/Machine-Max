package io.github.tt432.machinemax.common.vehicle.subsystem;

import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.ResourceStorageSubsystemAttr;
import lombok.Getter;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Map;

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

    /**
     * 在此填入各个信号名对应的接收者名列表，用于自动组织信号传输关系。<p>
     * Return a map of signal names to a list of receiver names here, to automatically organize signal transfer.
     *
     * @return 信号名称->接收者名称列表 Map of signal names to a list of receiver names.
     */
    @Override
    public Map<String, List<String>> getTargetNames() {
        return Map.of();
    }
}
