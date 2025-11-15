package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import lombok.Getter;

@Getter
public class ItemStorageSubsystemStaticAttr extends AbstractSubsystemStaticAttr {
    public final int rows;
    public final int columns;

    public static final MapCodec<ItemStorageSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(AbstractSubsystemStaticAttr::getBasicDurability),
            Codec.INT.optionalFieldOf("rows", 3).forGetter(ItemStorageSubsystemStaticAttr::getRows),
            Codec.INT.optionalFieldOf("columns", 9).forGetter(ItemStorageSubsystemStaticAttr::getColumns)
    ).apply(instance, ItemStorageSubsystemStaticAttr::new));

    public ItemStorageSubsystemStaticAttr(
            float basicDurability, 
            int rows,
            int columns) {
        super(basicDurability);
        if (rows <= 0) throw new IllegalArgumentException("error.machine_max.item_storage_subsystem.invalid_row_num");
        if (columns <= 0) throw new IllegalArgumentException("error.machine_max.item_storage_subsystem.invalid_column_num");
        this.rows = rows;
        this.columns = columns;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.ITEM_STORAGE;
    }

}
