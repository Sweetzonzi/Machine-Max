package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import lombok.Getter;

@Getter
public class ItemStorageSubsystemStaticAttr extends BasicSubsystemStaticAttr {
    public final int rows;
    public final int columns;

    public static final MapCodec<ItemStorageSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.INT.optionalFieldOf("rows", 3).forGetter(ItemStorageSubsystemStaticAttr::getRows),
            Codec.INT.optionalFieldOf("columns", 9).forGetter(ItemStorageSubsystemStaticAttr::getColumns),
            BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT).forGetter(BasicSubsystemStaticAttr::getSoundAttr)
    ).apply(instance, ItemStorageSubsystemStaticAttr::new));

    public ItemStorageSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr,
            int rows,
            int columns,
            BasicSoundAttr sounds
    ) {
        super(basicAttr, sounds);
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
