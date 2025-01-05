package io.github.tt432.machinemax.mixin;

import cn.solarmoon.spark_core.phys.thread.PhysThreadApplier;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PhysThreadApplier.class)
public class PhysThreadApplierMixin {
    @Inject(method = "levelTicker", at = @At("HEAD"), cancellable = true)
    private void levelTicker(LevelTickEvent.Pre event, CallbackInfo ci){
        //TODO:处理与战斗动作Mod的线程冲突
        ci.cancel();//临时手段，禁用原本的levelTicker方法
    }
}
