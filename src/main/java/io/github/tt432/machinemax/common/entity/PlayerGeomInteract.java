package io.github.tt432.machinemax.common.entity;

import cn.solarmoon.spark_core.phys.attached_body.AttachedBody;
import cn.solarmoon.spark_core.phys.attached_body.AttachedBodyHelper;
import cn.solarmoon.spark_core.phys.attached_body.AttachedBodyHelperKt;
import cn.solarmoon.spark_core.registry.common.SparkAttachments;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;

import static io.github.tt432.machinemax.MachineMax.MOD_ID;

@EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class PlayerGeomInteract {
    @SubscribeEvent
    private static void join(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            LivingEntityEyesight interactionSight = new LivingEntityEyesight("interactionSight", player);
            AttachedBodyHelperKt.putBody(player, interactionSight);
        }
    }

    @SubscribeEvent
    private static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            HashMap<String, AttachedBody> attachedBodies = player.getData(SparkAttachments.getBODY().get());
            var attachedBody = attachedBodies.get("interactionSight");
            if (attachedBody instanceof LivingEntityEyesight) {
                attachedBody.getPhysLevel().getPhysWorld().laterConsume(() -> {
                    ((LivingEntityEyesight) attachedBody).ray.destroy();
                    ((LivingEntityEyesight) attachedBody).body.destroy();
                    return null;
                });
            }
        }
    }

    @SubscribeEvent
    private static void interact(PlayerInteractEvent.RightClickEmpty event) {

    }
}
