package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.ItemStorageSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ItemStorageSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

@Getter
public class ItemStorageSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final ItemStorageSubsystemStaticAttr staticAttribute;
    public static final MapCodec<ItemStorageSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName)
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
