package io.github.sweetzonzi.machinemax.client.gui;

import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.client.gui.renderable.AnimatableRenderable;
import io.github.sweetzonzi.machinemax.client.gui.renderable.RenderableAttr;
import io.github.sweetzonzi.machinemax.external.MMDynamicRes;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@OnlyIn(Dist.CLIENT)
public class CustomHud implements LayeredDraw.Layer {

    private final Set<AnimatableRenderable> hud = new CopyOnWriteArraySet<>();

    public CustomHud() {
        MMGuiManager.customHud = this;
        hud.add(new AnimatableRenderable(MMDynamicRes.CUSTOM_HUD.get(
                ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "example_pack/hud/car_hud_third_person.json"))));
    }

    public void tick() {
        hud.forEach(AnimatableRenderable::animTick);
    }

    public void physicsTick() {
        hud.forEach(AnimatableRenderable::physicsTick);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        if (!hud.isEmpty()) {
            for (AnimatableRenderable renderable : hud){
                renderable.render(guiGraphics, 0, 0, deltaTracker.getGameTimeDeltaTicks());
            }
        }
    }
}
