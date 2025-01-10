package io.github.tt432.machinemax.common.entity;

import io.github.tt432.machinemax.common.part.AbstractPart;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public abstract class AbstractPartEntity extends Entity {
    protected AbstractPartEntity(EntityType<? extends AbstractPartEntity> entityType, Level level) {
        super(entityType, level);
    }

    protected AbstractPartEntity(EntityType<? extends AbstractPartEntity> entityType, Level level, AbstractPart part){
        this(entityType, level);
    }
}
