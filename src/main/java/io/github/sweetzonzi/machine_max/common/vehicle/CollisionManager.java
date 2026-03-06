package io.github.sweetzonzi.machine_max.common.vehicle;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
public class CollisionManager {

    public static Map<Entity, Vec3> impulse = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onCollision(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (impulse.containsKey(entity) && (
                entity instanceof Player && entity.level().isClientSide()
                        || !(entity instanceof Player) && !entity.level().isClientSide()
        )) {
            Vec3 vec = impulse.remove(entity);
            entity.addDeltaMovement(vec);
        }
    }

    public static void addImpulse(Entity entity, Vec3 vec) {
        impulse.merge(entity, vec, Vec3::add);
    }

    public static void setImpulse(Entity entity, Vec3 vec) {
        impulse.put(entity, vec);
    }

    public static Vec3 getImpulse(Entity entity) {
        return impulse.getOrDefault(entity, Vec3.ZERO);
    }
}
