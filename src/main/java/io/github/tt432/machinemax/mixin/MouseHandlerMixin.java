package io.github.tt432.machinemax.mixin;

import io.github.tt432.machinemax.client.input.CameraController;
import io.github.tt432.machinemax.client.input.RawInputHandler;
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
            locals = LocalCapture.CAPTURE_FAILHARD,
            cancellable = true
    )
    public void beforeTurnPlayer(double movementTime, CallbackInfo ci, CalculatePlayerTurnEvent event, double d2, double d3, double d4, double d0, double d1, int i) {
        CameraController.turnCamera(d0, d1*i);//传输镜头控制量
        if (RawInputHandler.freeCam) ci.cancel();//自由视角模式下不旋转玩家朝向
    }
}
