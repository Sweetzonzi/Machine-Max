package io.github.tt432.machinemax.mixin;

import io.github.tt432.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.tt432.machinemax.mixin_interface.IEntityMixin;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nullable;

@Mixin(Entity.class)
abstract public class EntityMixin implements IEntityMixin {
    @Unique
    private SeatSubsystem machine_Max$subSystem;

    @Nullable
    @Override
    public SeatSubsystem getRidingSubsystem() {
        return machine_Max$subSystem;
    }

    @Override
    public void setRidingSubsystem(SeatSubsystem subSystem) {
        this.machine_Max$subSystem = subSystem;
    }

//    @Inject(method = "removeVehicle", at = @At("HEAD"))
//    public void removeVehicle(CallbackInfo ci){
//        if(getRidingSubsystem()!= null){
//            setRidingSubsystem(null);
//        }
//    }

}
