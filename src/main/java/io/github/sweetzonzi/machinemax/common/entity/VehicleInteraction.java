package io.github.sweetzonzi.machinemax.common.entity;

import io.github.sweetzonzi.machinemax.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machinemax.common.registry.MMAttachments;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import static io.github.sweetzonzi.machinemax.MachineMax.MOD_ID;

@EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class VehicleInteraction {
    @SubscribeEvent
    private static void join(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            LivingEntityEyesightAttachment interactionSight = new LivingEntityEyesightAttachment(player);
            player.setData(MMAttachments.getENTITY_EYESIGHT(), interactionSight);
        }
    }

    @SubscribeEvent
    private static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            LivingEntityEyesightAttachment eyeSight;
            if (player.hasData(MMAttachments.getENTITY_EYESIGHT())) {
                eyeSight = player.getData(MMAttachments.getENTITY_EYESIGHT());
                eyeSight.getTargets().clear();
                player.removeData(MMAttachments.getENTITY_EYESIGHT());
            }
        }
    }
}
