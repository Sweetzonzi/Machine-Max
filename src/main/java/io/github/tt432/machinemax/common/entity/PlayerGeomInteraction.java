package io.github.tt432.machinemax.common.entity;

import cn.solarmoon.spark_core.phys.attached_body.AttachedBody;
import cn.solarmoon.spark_core.phys.attached_body.AttachedBodyHelperKt;
import cn.solarmoon.spark_core.registry.common.SparkAttachments;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.sloarphys.body.EntityBoundingBoxBody;
import io.github.tt432.machinemax.common.sloarphys.body.LivingEntityEyesightBody;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;

import static io.github.tt432.machinemax.MachineMax.MOD_ID;

@EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class PlayerGeomInteraction {
    @SubscribeEvent
    private static void join(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            LivingEntityEyesightBody interactionSight = new LivingEntityEyesightBody("interactionSight", player);
            AttachedBodyHelperKt.putBody(player, interactionSight);
            AttachedBodyHelperKt.putBody(player, new EntityBoundingBoxBody(player));
        }
    }

    @SubscribeEvent
    private static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            HashMap<String, AttachedBody> attachedBodies = player.getData(SparkAttachments.getBODY().get());
            var attachedBody = attachedBodies.get("interactionSight");
            if (attachedBody instanceof LivingEntityEyesightBody) {
                AttachedBody finalAttachedBody = attachedBody;
                attachedBody.getPhysLevel().getPhysWorld().laterConsume(() -> {
                    ((LivingEntityEyesightBody) finalAttachedBody).ray.destroy();
                    finalAttachedBody.getBody().destroy();
                    return null;
                });
            }
            attachedBody = attachedBodies.get("BoundingBox");
            if (attachedBody instanceof EntityBoundingBoxBody) {
                AttachedBody finalAttachedBody1 = attachedBody;
                attachedBody.getPhysLevel().getPhysWorld().laterConsume(() -> {
                    ((EntityBoundingBoxBody) finalAttachedBody1).getGeoms().getFirst().destroy();
                    finalAttachedBody1.getBody().destroy();
                    return null;
                });
            }
        }
    }

    @SubscribeEvent
    private static void interact(PlayerInteractEvent.EntityInteract event) {
        MachineMax.LOGGER.info("By Entity Target: "+event.getTarget());
        LivingEntityEyesightBody ray = (LivingEntityEyesightBody) event.getEntity().getData(SparkAttachments.getBODY().get()).get("interactionSight");
        if(ray.getTarget()!= null){
            MachineMax.LOGGER.info("By Entity Target: " + ray.getTarget().getBody().getOwner());

        }
    }
}
