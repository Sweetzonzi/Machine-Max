package io.github.sweetzonzi.machine_max.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.sweetzonzi.machine_max.client.input.CameraController;
import io.github.sweetzonzi.machine_max.client.input.RawInputHandler;
import io.github.sweetzonzi.machine_max.external.js.hook.AxisHook;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Unique
    private double lastX = 0;

    @Unique
    private double lastY = 0;

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

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void onMove(long window, double x, double y, CallbackInfo ci) {
        double deltaX = x - lastX;
        double deltaY = y - lastY;
        // 注入鼠标拖动信号
        AxisHook.putAxisData(AxisHook.AxisType.XDelta, deltaX);
        AxisHook.putAxisData(AxisHook.AxisType.YDelta, deltaY);
        // 注入鼠标当前坐标位置
        AxisHook.putAxisData(AxisHook.AxisType.XPosition, x);
        AxisHook.putAxisData(AxisHook.AxisType.YPosition, y);

        lastX = x;
        lastY = y;
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
        AxisHook.putAxisData(AxisHook.AxisType.XScroll, xOffset);
        AxisHook.putAxisData(AxisHook.AxisType.YScroll, yOffset);
    }
}
