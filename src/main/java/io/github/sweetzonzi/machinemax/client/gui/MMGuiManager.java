package io.github.sweetzonzi.machinemax.client.gui;

import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.client.gui.renderable.AnimatableRenderable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class MMGuiManager {
    public static Set<WeakReference<AnimatableRenderable>> animatableWidgets = new CopyOnWriteArraySet<>();
    public static ReferenceQueue<AnimatableRenderable> referenceQueue = new ReferenceQueue<>();
    public static CustomHud customHud = null;

    @SubscribeEvent
    private static void onAnimTick(LevelTickEvent.Post event) {
        try {
            if (customHud != null) customHud.tick();
            WeakReference<AnimatableRenderable> ref;
            while ((ref = (WeakReference<AnimatableRenderable>) referenceQueue.poll()) != null) {
                // 从集合中移除已经失效的弱引用
                animatableWidgets.remove(ref);
            }

            for (WeakReference<AnimatableRenderable> widget : animatableWidgets) {
                AnimatableRenderable animatable = widget.get();
                if (animatable != null) {
                    animatable.animTick();
                }
            }
        } catch (Exception e) {
            MachineMax.LOGGER.warn("Error while ticking widget at main thread: ", e);
        }
    }

    @SubscribeEvent
    private static void onPhysicsTick(LevelTickEvent.Post event) {
        try {
            if (customHud != null) customHud.physicsTick();
            WeakReference<AnimatableRenderable> ref;
            while ((ref = (WeakReference<AnimatableRenderable>) referenceQueue.poll()) != null) {
                // 从集合中移除已经失效的弱引用
                animatableWidgets.remove(ref);
            }

            for (WeakReference<AnimatableRenderable> widget : animatableWidgets) {
                AnimatableRenderable animatable = widget.get();
                if (animatable != null) {
                    animatable.physicsTick();
                }
            }
        } catch (Exception e) {
            MachineMax.LOGGER.warn("Error while ticking widget at physics thread: ", e);
        }
    }
}
