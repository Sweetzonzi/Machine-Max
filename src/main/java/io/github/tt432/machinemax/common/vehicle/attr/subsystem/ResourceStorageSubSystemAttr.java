package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.common.vehicle.attr.PortAttr;
import lombok.Getter;

import java.util.Map;

@Getter
public class ResourceStorageSubSystemAttr extends AbstractSubSystemAttr {
    public final String resourceType;
    public final float maxCapacity;
    public final float initialCapacity;
    public final boolean shared;

    public static final MapCodec<ResourceStorageSubSystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            PortAttr.MAP_CODEC.fieldOf("ports").forGetter(AbstractSubSystemAttr::getPortAttrs),
            Codec.STRING.fieldOf("resource_type").forGetter(ResourceStorageSubSystemAttr::getResourceType),
            Codec.FLOAT.optionalFieldOf("max_capacity", 0.0f).forGetter(ResourceStorageSubSystemAttr::getMaxCapacity),
            Codec.FLOAT.optionalFieldOf("initial_capacity", 0.0f).forGetter(ResourceStorageSubSystemAttr::getInitialCapacity),
            Codec.BOOL.optionalFieldOf("shared", false).forGetter(ResourceStorageSubSystemAttr::isShared)
    ).apply(instance, ResourceStorageSubSystemAttr::new));

    public ResourceStorageSubSystemAttr(Map<String, PortAttr> portAttrs, String resourceType, float maxCapacity, float initialCapacity, boolean shared) {
        super();
        this.portAttrs.putAll(portAttrs);
        this.resourceType = resourceType;
        this.maxCapacity = maxCapacity;
        this.initialCapacity = initialCapacity;
        this.shared = shared;
    }

    @Override
    public MapCodec<? extends AbstractSubSystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.RESOURCE_STORAGE;
    }
}
