package io.github.sweetzonzi.machine_max.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;

public class MultiAnimatableRenderer extends BlockEntityWithoutLevelRenderer {
    public MultiAnimatableRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }
}
