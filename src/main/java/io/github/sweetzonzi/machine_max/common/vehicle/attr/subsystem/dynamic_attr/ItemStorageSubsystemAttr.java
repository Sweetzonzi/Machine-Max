package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.ItemStorageSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.ItemStorageSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

@Getter
public class ItemStorageSubsystemAttr extends AbstractSubsystemAttr {
    public final ItemStorageSubsystemStaticAttr staticAttribute;
    public static final MapCodec<ItemStorageSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("model").forGetter(AbstractSubsystemAttr::getModelName)
    ).apply(instance, ItemStorageSubsystemAttr::new));

    public ItemStorageSubsystemAttr(
            ResourceLocation modelName) {
        super(modelName);
        this.staticAttribute = (ItemStorageSubsystemStaticAttr) getStaticAttr();
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.ITEM_STORAGE;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new ItemStorageSubsystem(owner, name, this);
    }
}
