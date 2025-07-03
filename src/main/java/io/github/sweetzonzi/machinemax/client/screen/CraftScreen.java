package io.github.sweetzonzi.machinemax.client.screen;

import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.client.gui.renderable.AnimatableRenderable;
import io.github.sweetzonzi.machinemax.client.gui.renderable.RenderableAttr;
import io.github.sweetzonzi.machinemax.common.item.prop.MMPartItem;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.PartType;
import io.github.sweetzonzi.machinemax.common.vehicle.visual.PartProjection;
import io.github.sweetzonzi.machinemax.external.MMDynamicRes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static io.github.sweetzonzi.machinemax.client.renderer.VisualEffectHelper.partToAssembly;

public class CraftScreen extends Screen {
    public CraftScreen() {
        super(Component.translatable("craft_screen.title"));
    }


    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiWidth();
        int x = 0;
        int y = 0;
        for (PartType partType : MMDynamicRes.PART_TYPES.values()) {
            for (ResourceLocation modelLocation : partType.getVariants().values()) {
                AnimatableRenderable renderable = new AnimatableRenderable(
                        new RenderableAttr(
                                modelLocation,
                                partType.animation,
                                partType.textures.getFirst(),
                                new Vec3(x, y, -10),
                                new Vec3(0, 0, 0),
                                15,
                                true,
                                Map.of()));

                x += 5;
                if (x > (width/2) - 50) y += 5;

                renderable.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }


    }
}
