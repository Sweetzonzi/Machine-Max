package io.github.sweetzonzi.machine_max.mixin;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.fbo.FboManager;
import io.github.sweetzonzi.machine_max.client.fbo.OffscreenFbo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LevelRenderer Mixin — 注入 FBO 生命周期钩子：
 * <ul>
 *   <li>{@code initOutline} → 初始化/重载 FBO</li>
 *   <li>{@code resize} → 窗口尺寸变化时更新 FBO</li>
 *   <li>{@code close} → 退出世界时释放 GPU 资源</li>
 * </ul>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow @Final
    private Minecraft minecraft;

    /** 初始化/重载所有离屏 FBO（在 initOutline 末尾执行） */
    @Inject(method = "initOutline", at = @At("RETURN"))
    private void machine_max$initOffscreenFbos(CallbackInfo ci) {
        FboManager.INSTANCE.closeAll();

        // Inspector: PostChain 模式
        var inspector = OffscreenFbo.postProcess(
            ResourceLocation.parse("machine_max:inspector"),
            ResourceLocation.parse("machine_max:shaders/post/inspector_highlight.json")
        );
        if (inspector.loadPostChain(minecraft)) {
            FboManager.INSTANCE.register(inspector);
            MachineMax.LOGGER.debug("Inspector FBO 初始化成功");
        } else {
            inspector.close();
            MachineMax.LOGGER.warn("Inspector FBO 初始化失败（着色器可能缺失）");
        }
    }

    /** 窗口 resize 时更新所有 FBO */
    @Inject(method = "resize", at = @At("RETURN"))
    private void machine_max$resizeOffscreenFbos(int width, int height, CallbackInfo ci) {
        FboManager.INSTANCE.resizeAll(width, height);
    }

    /** 退出世界时释放所有 FBO GPU 资源 */
    @Inject(method = "close", at = @At("HEAD"))
    private void machine_max$closeOffscreenFbos(CallbackInfo ci) {
        FboManager.INSTANCE.closeAll();
        MachineMax.LOGGER.debug("所有 FBO 资源已释放");
    }
}
