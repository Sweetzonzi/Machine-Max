package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import io.github.sweetzonzi.machine_max.client.input.CameraController;
import io.github.sweetzonzi.machine_max.client.render.gui.MMGuiManager;
import io.github.sweetzonzi.machine_max.client.render.renderable.GuiAnimatable;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CameraSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
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
            // 炮镜模式：加载摄像机HUD组件
            if (CameraController.isCameraMode()) {
                CameraSubsystem camera = CameraController.getActiveCamera();
                if (camera != null && camera.isActive()) {
                    for (ResourceLocation path : camera.attr.staticAttribute.getHudComponents()) {
                        vehicleHud.computeIfAbsent(path, p -> new GuiAnimatable(MMDynamicRes.CUSTOM_HUD.get(p)));
                    }
                    for (Map.Entry<ResourceLocation, GuiAnimatable> entry : vehicleHud.entrySet()){
                        if(!camera.attr.staticAttribute.getHudComponents().contains(entry.getKey())) {
                            vehicleHud.remove(entry.getKey());
                            entry.getValue().destroy();
                        }
                    }
                }
                return;
            }

            AbstractControllableSubsystem subsystem = ((IEntityMixin) player).machine_Max$getControllingSubsystem();
            if (subsystem instanceof SeatSubsystem seat) {
                if (view.isFirstPerson()) {
                    //添加缺少的HUD组件
                    for (ResourceLocation path : seat.attr.staticAttribute.views.firstPersonHud()) {
                        vehicleHud.computeIfAbsent(path, p -> new GuiAnimatable(MMDynamicRes.CUSTOM_HUD.get(p)));
                    }
                    //移除不匹配的HUD组件
                    for (Map.Entry<ResourceLocation, GuiAnimatable> entry : vehicleHud.entrySet()){
                        if(!seat.attr.staticAttribute.views.firstPersonHud().contains(entry.getKey())) {
                            vehicleHud.remove(entry.getKey());
                            entry.getValue().destroy();
                        }
                    }
                } else {
                    //添加缺少的HUD组件
                    for (ResourceLocation path : seat.attr.staticAttribute.views.thirdPersonHud()){
                        vehicleHud.computeIfAbsent(path, p -> new GuiAnimatable(MMDynamicRes.CUSTOM_HUD.get(p)));
                    }
                    //移除不匹配的HUD组件
                    for (Map.Entry<ResourceLocation, GuiAnimatable> entry : vehicleHud.entrySet()){
                        if(!seat.attr.staticAttribute.views.thirdPersonHud().contains(entry.getKey())) {
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
        if (Minecraft.getInstance().options.hideGui) return;

        // 炮镜模式：渲染摄像机HUD组件
        if (CameraController.isCameraMode()) {
            CameraSubsystem camera = CameraController.getActiveCamera();
            if (camera != null && camera.isActive()) {
                for (ResourceLocation hudRl : camera.attr.staticAttribute.getHudComponents()) {
                    GuiAnimatable hud = vehicleHud.get(hudRl);
                    if (hud != null) hud.render(guiGraphics, 0, 0, deltaTracker.getGameTimeDeltaTicks());
                }
            }
            return; // 炮镜下不渲染座椅HUD
        }

        if (!vehicleHud.isEmpty()) {
            for (GuiAnimatable renderable : vehicleHud.values()) {
                renderable.render(guiGraphics, 0, 0, deltaTracker.getGameTimeDeltaTicks());
            }
        }
    }
}
