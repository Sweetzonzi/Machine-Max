package io.github.sweetzonzi.machine_max.client.gui.hud;

import io.github.sweetzonzi.machine_max.client.gui.MMGuiManager;
import io.github.sweetzonzi.machine_max.client.renderable.GuiAnimatable;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
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
    private final ConcurrentMap<ResourceLocation, GuiAnimatable> vehicleHud = new ConcurrentHashMap<>();

    public CustomHud() {
        MMGuiManager.customHud = this;
    }

    public void tick() {
        Player player = Minecraft.getInstance().player;
        CameraType view = Minecraft.getInstance().options.getCameraType();
        if (player != null) {
            AbstractControllableSubsystem subsystem = ((IEntityMixin) player).machine_Max$getControllingSubsystem();
            if (subsystem instanceof SeatSubsystem seat) {
                if (view.isFirstPerson()) {
                    //添加缺少的HUD组件
                    for (ResourceLocation path : seat.attr.views.firstPersonHud()) {
                        vehicleHud.computeIfAbsent(path, p -> new GuiAnimatable(MMDynamicRes.CUSTOM_HUD.get(p)));
                    }
                    //移除不匹配的HUD组件
                    for (Map.Entry<ResourceLocation, GuiAnimatable> entry : vehicleHud.entrySet()){
                        if(!seat.attr.views.firstPersonHud().contains(entry.getKey())) {
                            vehicleHud.remove(entry.getKey());
                            entry.getValue().destroy();
                        }
                    }
                } else {
                    //添加缺少的HUD组件
                    for (ResourceLocation path : seat.attr.views.thirdPersonHud()){
                        vehicleHud.computeIfAbsent(path, p -> new GuiAnimatable(MMDynamicRes.CUSTOM_HUD.get(p)));
                    }
                    //移除不匹配的HUD组件
                    for (Map.Entry<ResourceLocation, GuiAnimatable> entry : vehicleHud.entrySet()){
                        if(!seat.attr.views.thirdPersonHud().contains(entry.getKey())) {
                            vehicleHud.remove(entry.getKey());
                            entry.getValue().destroy();
                        }
                    }
                }
            } else {
                for (GuiAnimatable renderable : vehicleHud.values()) renderable.destroy();
                vehicleHud.clear();
            }
        }
    }

    public void physicsTick() {
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        if (!vehicleHud.isEmpty()) {
            for (GuiAnimatable renderable : vehicleHud.values()) {
                renderable.render(guiGraphics, 0, 0, deltaTracker.getGameTimeDeltaTicks());
            }
        }
    }
}
