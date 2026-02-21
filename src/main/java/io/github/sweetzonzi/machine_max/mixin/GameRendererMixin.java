package io.github.sweetzonzi.machine_max.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.client.event.RenderLevelLastEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入 GameRenderer#renderLevel 的末尾，
 * 在 profiler.pop() 之前发布 RenderLevelLastEvent。
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow @Final
    Minecraft minecraft;

    @Shadow
    @Final
    private Camera mainCamera;

    @Shadow
    public abstract void resetProjectionMatrix(Matrix4f matrix);

    @Shadow
    public abstract Matrix4f getProjectionMatrix(double fov);

    @Shadow
    protected abstract double getFov(Camera activeRenderInfo, float partialTicks, boolean useFOVSetting);

    /**
     * 注入点说明：
     * - 目标：Profiler#pop()
     * - 位置：调用之前（BEFORE）
     * - 效果：确保手部渲染（若存在）已完成
     */
    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/ProfilerFiller;pop()V"
            )
    )
    private void machine_max$onRenderLevelLast(
            DeltaTracker deltaTracker, CallbackInfo ci
    ) {
        LevelRenderer levelRenderer = this.minecraft.levelRenderer;

        Quaternionf quaternionf = mainCamera.rotation().conjugate(new Quaternionf());
        Matrix4f modelViewMatrix = (new Matrix4f()).rotation(quaternionf);

        // 发布事件
        NeoForge.EVENT_BUS.post(new RenderLevelLastEvent(
                levelRenderer,
                null, // 与 RenderLevelStageEvent 保持一致，内部会创建 PoseStack
                modelViewMatrix,
                getProjectionMatrix(Math.max(getFov(mainCamera, deltaTracker.getGameTimeDeltaPartialTick(true), true), (double) this.minecraft.options.fov().get())),
                levelRenderer.getTicks(),
                deltaTracker,
                mainCamera,
                levelRenderer.getFrustum()
        ));
    }
}
