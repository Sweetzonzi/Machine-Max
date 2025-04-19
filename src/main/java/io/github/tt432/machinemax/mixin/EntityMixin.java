package io.github.tt432.machinemax.mixin;

import io.github.tt432.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.tt432.machinemax.mixin_interface.IEntityMixin;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(Entity.class)
abstract public class EntityMixin implements IEntityMixin {
    @Unique
    private SeatSubsystem subSystem;

    @Nullable
    @Override
    public SeatSubsystem getRidingSubsystem() {
        return subSystem;
    }

    @Override
    public void setRidingSubsystem(SeatSubsystem subSystem) {
        this.subSystem = subSystem;
    }

//    @Inject(method = "removeVehicle", at = @At("HEAD"))
//    public void removeVehicle(CallbackInfo ci){
//        if(getRidingSubsystem()!= null){
//            setRidingSubsystem(null);
//        }
//    }

}
