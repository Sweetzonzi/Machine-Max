package io.github.sweetzonzi.machine_max.client.render.gui;

import cn.solarmoon.spark_core.event.PhysicsLevelTickEvent;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.input.CameraController;
import io.github.sweetzonzi.machine_max.client.render.gui.hud.CustomHud;
import io.github.sweetzonzi.machine_max.client.render.renderable.ITickableRenderable;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public class MMGuiManager {
    public static Set<WeakReference<ITickableRenderable>> animatableWidgets = new CopyOnWriteArraySet<>();
    public static ReferenceQueue<ITickableRenderable> referenceQueue = new ReferenceQueue<>();
    public static CustomHud customHud = null;

    /** 炮镜模式下需要隐藏的 HUD 图层（被这些遮挡瞄具视野） */
    private static final Set<ResourceLocation> CAMERA_MODE_HIDDEN_LAYERS = Set.of(
            VanillaGuiLayers.CAMERA_OVERLAYS,   // 原版摄像机覆盖（南瓜模糊等）
            VanillaGuiLayers.CROSSHAIR,         // 准心（炮镜有自己的分划板）
            VanillaGuiLayers.HOTBAR,            // 快捷栏
            VanillaGuiLayers.JUMP_METER,        // 跳跃蓄力条
            VanillaGuiLayers.EXPERIENCE_BAR,    // 经验条
            VanillaGuiLayers.PLAYER_HEALTH,     // 生命值
            VanillaGuiLayers.ARMOR_LEVEL,       // 护甲值
            VanillaGuiLayers.FOOD_LEVEL,        // 饱食度
            VanillaGuiLayers.VEHICLE_HEALTH,    // 载具血量（载具有自己的 HUD）
            VanillaGuiLayers.AIR_LEVEL,         // 氧气条
            VanillaGuiLayers.SELECTED_ITEM_NAME,// 选中物品名称
            VanillaGuiLayers.SPECTATOR_TOOLTIP, // 旁观者提示
            VanillaGuiLayers.EXPERIENCE_LEVEL,  // 经验等级
            VanillaGuiLayers.EFFECTS,           // 药水效果图标
            VanillaGuiLayers.SLEEP_OVERLAY      // 睡眠覆盖
    );

    @SubscribeEvent
    private static void onAnimTick(LevelTickEvent.Post event) {
        try {
            if (customHud != null) customHud.tick();
            WeakReference<ITickableRenderable> ref;
            while ((ref = (WeakReference<ITickableRenderable>) referenceQueue.poll()) != null) {
                // 从集合中移除已经失效的弱引用
                animatableWidgets.remove(ref);
            }

            for (WeakReference<ITickableRenderable> widget : animatableWidgets) {
                ITickableRenderable animatable = widget.get();
                if (animatable != null) {
                    animatable.animTick();
                }
            }
        } catch (Exception e) {
            MachineMax.LOGGER.warn("Error while ticking widget at main thread: ", e);
        }
    }

    @SubscribeEvent
    private static void onPhysicsTick(PhysicsLevelTickEvent.Post event) {
        try {
            if (customHud != null) customHud.physicsTick();
            WeakReference<ITickableRenderable> ref;
            while ((ref = (WeakReference<ITickableRenderable>) referenceQueue.poll()) != null) {
                // 从集合中移除已经失效的弱引用
                animatableWidgets.remove(ref);
            }

            for (WeakReference<ITickableRenderable> widget : animatableWidgets) {
                ITickableRenderable animatable = widget.get();
                if (animatable != null) {
                    animatable.physicsTick();
                }
            }
        } catch (Exception e) {
            MachineMax.LOGGER.warn("Error while ticking widget at physics thread: ", e);
        }
    }

    @SubscribeEvent
    private static void renderHudEvent(RenderGuiLayerEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;

        // 炮镜模式：仅隐藏遮挡瞄具视野的图层（使用黑名单，不影响其他模组添加的 HUD）
        if (CameraController.isCameraMode()) {
            if (CAMERA_MODE_HIDDEN_LAYERS.contains(event.getName())) {
                event.setCanceled(true);
            }
            return;
        }

        // 普通座椅模式：allowUseItems = false 时隐藏热栏
        if (player instanceof IEntityMixin passenger
                && passenger.machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat
                && !seat.attr.staticAttribute.allowUseItems) {
            if (event.getName() == VanillaGuiLayers.HOTBAR) event.setCanceled(true);
        }
    }
}
