package io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.ItemStorageSubsystem;
import lombok.Getter;

@Getter
public class ItemStorageSubsystemAttr extends AbstractSubsystemAttr {
    public final int rows;
    public final int columns;

    public static final MapCodec<ItemStorageSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("hit_box", "").forGetter(AbstractSubsystemAttr::getHitBox),
            Codec.INT.optionalFieldOf("rows", 3).forGetter(ItemStorageSubsystemAttr::getRows),
            Codec.INT.optionalFieldOf("columns", 9).forGetter(ItemStorageSubsystemAttr::getColumns)
    ).apply(instance, ItemStorageSubsystemAttr::new));

    public ItemStorageSubsystemAttr(
            float basicDurability,
            String hitBox,
            int rows,
            int columns) {
        super(basicDurability, hitBox);
        if (rows <= 0) throw new IllegalArgumentException("error.machine_max.item_storage_subsystem.invalid_row_num");
        if (columns <= 0) throw new IllegalArgumentException("error.machine_max.item_storage_subsystem.invalid_column_num");
        this.rows = rows;
        this.columns = columns;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.ITEM_STORAGE;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new ItemStorageSubsystem(owner, name, this);
    }
}
