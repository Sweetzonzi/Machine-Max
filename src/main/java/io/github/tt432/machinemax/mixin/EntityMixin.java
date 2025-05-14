package io.github.tt432.machinemax.mixin;

import io.github.tt432.machinemax.common.entity.MMPartEntity;
import io.github.tt432.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.tt432.machinemax.mixin_interface.IEntityMixin;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(Entity.class)
abstract public class EntityMixin extends AttachmentHolder implements IEntityMixin {
    @Unique
    private SeatSubsystem machine_Max$subSystem;

//    @Inject(method = "getVehicle", at = @At("RETURN"), cancellable = true)
//    public void getVehicle(CallbackInfoReturnable<Entity> cir) {
//        if (machine_Max$subSystem != null && machine_Max$subSystem.getPart() != null && machine_Max$subSystem.getPart().getEntity() instanceof MMPartEntity entity) {
//            cir.setReturnValue(entity);
//        }
//    }

    @Nullable
    @Override
    public SeatSubsystem machine_Max$getRidingSubsystem() {
        return machine_Max$subSystem;
    }

    @Override
    public void machine_Max$setRidingSubsystem(SeatSubsystem subSystem) {
        this.machine_Max$subSystem = subSystem;
    }

//    @Inject(method = "removeVehicle", at = @At("HEAD"))
//    public void removeVehicle(CallbackInfo ci){
//        if(getRidingSubsystem()!= null){
//            setRidingSubsystem(null);
//        }
//    }

}
