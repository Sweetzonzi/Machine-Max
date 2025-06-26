package io.github.sweetzonzi.machinemax.client.gui.widget;

import io.github.sweetzonzi.machinemax.MachineMax;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class WidgetManager {
    public static List<WeakReference<AnimatableRenderable>> animatableWidgets = new CopyOnWriteArrayList<>();

    @SubscribeEvent
    private static void onAnimTick(LevelTickEvent.Post event) {
        Iterator<WeakReference<AnimatableRenderable>> iterator = animatableWidgets.iterator();
        while (iterator.hasNext()){
            WeakReference<AnimatableRenderable> widget = iterator.next();
            AnimatableRenderable animatable = widget.get();
            if (animatable == null) {
                iterator.remove();
            } else {
                animatable.animTick();
            }
        }
    }

    @SubscribeEvent
    private static void onPhysicsTick(LevelTickEvent.Post event) {
        Iterator<WeakReference<AnimatableRenderable>> iterator = animatableWidgets.iterator();
        while (iterator.hasNext()){
            WeakReference<AnimatableRenderable> widget = iterator.next();
            AnimatableRenderable animatable = widget.get();
            if (animatable == null) {
                iterator.remove();
            } else {
                animatable.physicsTick();
            }
        }
    }
}
