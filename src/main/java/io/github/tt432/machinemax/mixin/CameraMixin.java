package io.github.tt432.machinemax.mixin;

import io.github.tt432.machinemax.client.event.ComputeCameraPosEvent;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Unique
    private final Camera machine_Max$camera = (Camera) (Object) this;

    @Shadow
    public abstract Vec3 getPosition();

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V", shift = At.Shift.AFTER))
    private void setup(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        var cameraPosSetup = NeoForge.EVENT_BUS.post(new ComputeCameraPosEvent(machine_Max$camera, partialTick));
        Vec3 modifiedPos = cameraPosSetup.getCameraPos();
        setPosition(modifiedPos.x, modifiedPos.y, modifiedPos.z);
    }

}
