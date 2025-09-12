package io.github.sweetzonzi.machine_max.client.renderable;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.client.gui.MMGuiManager;
import net.minecraft.client.renderer.MultiBufferSource;

import java.lang.ref.WeakReference;

public interface ITickableRenderable {
    default void create(){
        MMGuiManager.animatableWidgets.add(new WeakReference<>(this, MMGuiManager.referenceQueue));
    }

    void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTick);

    void animTick();

    void physicsTick();
}
