package io.github.sweetzonzi.machinemax.client.gui;

import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.client.gui.renderable.AnimatableRenderable;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machinemax.external.MMDynamicRes;
import io.github.sweetzonzi.machinemax.mixin_interface.IEntityMixin;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@OnlyIn(Dist.CLIENT)
public class CustomHud implements LayeredDraw.Layer {
    //TODO:常驻hud或装备hud？
    private final ConcurrentMap<ResourceLocation, AnimatableRenderable> vehicleHud = new ConcurrentHashMap<>();

    public CustomHud() {
        MMGuiManager.customHud = this;
    }

    public void tick() {
        Player player = Minecraft.getInstance().player;
        CameraType view = Minecraft.getInstance().options.getCameraType();
        if (player != null) {
            SeatSubsystem seat = ((IEntityMixin) player).machine_Max$getRidingSubsystem();
            if (seat != null) {
                if (view.isFirstPerson()) {
                    //添加缺少的HUD组件
                    for (ResourceLocation path : seat.attr.views.firstPersonHud()) {
                        vehicleHud.computeIfAbsent(path, p -> new AnimatableRenderable(MMDynamicRes.CUSTOM_HUD.get(p)));
                    }
                    //移除不匹配的HUD组件
                    for (Map.Entry<ResourceLocation, AnimatableRenderable> entry : vehicleHud.entrySet()){
                        if(!seat.attr.views.firstPersonHud().contains(entry.getKey())) vehicleHud.remove(entry.getKey());
                    }
                } else {
                    //添加缺少的HUD组件
                    for (ResourceLocation path : seat.attr.views.thirdPersonHud()){
                        vehicleHud.computeIfAbsent(path, p -> new AnimatableRenderable(MMDynamicRes.CUSTOM_HUD.get(p)));
                    }
                    //移除不匹配的HUD组件
                    for (Map.Entry<ResourceLocation, AnimatableRenderable> entry : vehicleHud.entrySet()){
                        if(!seat.attr.views.thirdPersonHud().contains(entry.getKey())) vehicleHud.remove(entry.getKey());
                    }
                }
                vehicleHud.values().forEach(AnimatableRenderable::animTick);
            } else vehicleHud.clear();
        }
    }

    public void physicsTick() {
        vehicleHud.values().forEach(AnimatableRenderable::physicsTick);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        if (!vehicleHud.isEmpty()) {
            for (AnimatableRenderable renderable : vehicleHud.values()) {
                renderable.render(guiGraphics, 0, 0, deltaTracker.getGameTimeDeltaTicks());
            }
        }
    }
}
