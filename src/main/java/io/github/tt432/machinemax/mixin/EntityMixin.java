package io.github.tt432.machinemax.mixin;

import io.github.tt432.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.tt432.machinemax.mixin_interface.IEntityMixin;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nullable;

@Mixin(Entity.class)
abstract public class EntityMixin implements IEntityMixin {
    @Unique
    private AbstractSubsystem subSystem;
//
//    @Shadow
//    public abstract EntityType<?> getType();
//
//    @Shadow @Nullable private Entity vehicle;

    @Nullable
    @Override
    public AbstractSubsystem getRidingSubsystem() {
        return subSystem;
    }

    @Override
    public void setRidingSubsystem(AbstractSubsystem subSystem) {
        this.subSystem = subSystem;
    }

//    /**
//     * @author 甜粽子 Sweetzonzi
//     * @reason 获取乘坐部件的实体对象
//     */
//    @Nullable
//    @Overwrite
//    public Entity getVehicle() {
//        Entity vehicle = this.vehicle;
//        if(vehicle == null && vehiclePart != null && vehiclePart.entity != null) vehicle = vehiclePart.entity;
//        return vehicle;
//    }
//
//    /**
//     * @author 甜粽子 Sweetzonzi
//     * @reason 额外将本模组载具视为骑乘对象
//     */
//    @Overwrite
//    public boolean isPassenger() {
//        return (this.getVehicle() != null || this.getRidingPart() != null);
//    }
//
//    /**
//     * @author 甜粽子 Sweetzonzi
//     * @reason 额外将本模组载具视为不可控制的骑乘对象
//     */
//    @Overwrite
//    public boolean canControlVehicle() {
//        return !this.getType().is(EntityTypeTags.NON_CONTROLLING_RIDER) && this.getRidingPart() == null;
//    }

}
