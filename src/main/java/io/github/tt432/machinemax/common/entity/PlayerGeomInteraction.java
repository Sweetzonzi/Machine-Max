package io.github.tt432.machinemax.common.entity;

import cn.solarmoon.spark_core.registry.common.SparkAttachments;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.registry.MMAttachments;
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
            player.setData(MMAttachments.getENTITY_EYESIGHT(), interactionSight);
            //TODO:玩家附加碰撞箱
        }
    }

    @SubscribeEvent
    private static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            LivingEntityEyesightBody attachedBody;
            if (player.hasData(MMAttachments.getENTITY_EYESIGHT())) {
                attachedBody = player.getData(MMAttachments.getENTITY_EYESIGHT());
                attachedBody.getPhysLevel().getWorld().laterConsume(() -> {
                    attachedBody.ray.destroy();
                    attachedBody.getBody().destroy();
                    player.removeData(MMAttachments.getENTITY_EYESIGHT());
                    return null;
                });
            }
            //TODO:玩家移除碰撞箱
        }
    }

    @SubscribeEvent
    private static void interact(PlayerInteractEvent.EntityInteract event) {
        MachineMax.LOGGER.info("By Entity Target: " + event.getTarget());
        LivingEntityEyesightBody ray = event.getEntity().getData(MMAttachments.getENTITY_EYESIGHT());
        if (ray.getTarget() != null) {
            MachineMax.LOGGER.info("By Entity Target: " + ray.getTarget().getBody().getOwner());

        }
    }
}
