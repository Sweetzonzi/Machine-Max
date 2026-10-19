package io.github.sweetzonzi.machine_max.client.render.gui.world;

import io.github.sweetzonzi.machine_max.client.render.MMRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public abstract class Anchor implements WorldRenderer2DElement {

    protected abstract ResourceLocation getPictureLocation();

    @Override
    public final RenderType getRenderType() {
        return MMRenderTypes.anchorPoint(getPictureLocation());
    }
}
