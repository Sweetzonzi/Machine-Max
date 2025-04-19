package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.tt432.machinemax.common.vehicle.subsystem.ResourceStorageSubsystem;
import lombok.Getter;

@Getter
public class ResourceStorageSubsystemAttr extends AbstractSubsystemAttr {
    public final String resourceType;
    public final float maxCapacity;
    public final float initialCapacity;
    public final boolean shared;

    public static final MapCodec<ResourceStorageSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 100f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("hit_box", "").forGetter(AbstractSubsystemAttr::getHitBox),
            Codec.STRING.fieldOf("resource_type").forGetter(ResourceStorageSubsystemAttr::getResourceType),
            Codec.FLOAT.optionalFieldOf("max_capacity", 0.0f).forGetter(ResourceStorageSubsystemAttr::getMaxCapacity),
            Codec.FLOAT.optionalFieldOf("initial_capacity", 0.0f).forGetter(ResourceStorageSubsystemAttr::getInitialCapacity),
            Codec.BOOL.optionalFieldOf("shared", false).forGetter(ResourceStorageSubsystemAttr::isShared)
    ).apply(instance, ResourceStorageSubsystemAttr::new));

    public ResourceStorageSubsystemAttr(
            float basicDurability,
            String hitBox,
            String resourceType,
            float maxCapacity,
            float initialCapacity,
            boolean shared) {
        super(basicDurability, hitBox);
        this.resourceType = resourceType;
        this.maxCapacity = maxCapacity;
        this.initialCapacity = initialCapacity;
        this.shared = shared;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.RESOURCE_STORAGE;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new ResourceStorageSubsystem(owner, name, this);
    }
}
