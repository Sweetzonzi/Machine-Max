package io.github.sweetzonzi.machinemax.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.sweetzonzi.machinemax.client.input.CameraController;
import io.github.sweetzonzi.machinemax.client.input.RawInputHandler;
import net.minecraft.client.MouseHandler;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(
            method = "turnPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
            ),
            cancellable = true
    )
    public void beforeTurnPlayer(double movementTime, CallbackInfo ci, @Local(ordinal = 4) double d0, @Local(ordinal = 5) double d1, @Local int i) {
        CameraController.turnCamera(d0, d1*i);//传输镜头控制量
        if (RawInputHandler.freeCam) ci.cancel();//自由视角模式下不旋转玩家朝向
    }
}
