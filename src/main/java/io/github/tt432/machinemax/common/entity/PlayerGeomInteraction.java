package io.github.tt432.machinemax.common.entity;

import io.github.tt432.machinemax.common.attachment.LivingEntityEyesightAttachment;
import io.github.tt432.machinemax.common.registry.MMAttachments;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.tt432.machinemax.common.vehicle.subsystem.SeatSubsystem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import static io.github.tt432.machinemax.MachineMax.MOD_ID;

@EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class PlayerGeomInteraction {
    @SubscribeEvent
    private static void join(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            LivingEntityEyesightAttachment interactionSight = new LivingEntityEyesightAttachment(player);
            player.setData(MMAttachments.getENTITY_EYESIGHT(), interactionSight);
            //TODO:玩家附加碰撞箱
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
            //TODO:玩家移除碰撞箱
        }
    }

    @SubscribeEvent
    private static void interact(PlayerInteractEvent.EntityInteract event) {
        //TODO:仅服务端触发？
        LivingEntityEyesightAttachment ray = event.getEntity().getData(MMAttachments.getENTITY_EYESIGHT());
        Part part = ray.getPart();
        if (part != null && !event.getEntity().isShiftKeyDown()) {
            for (AbstractSubsystem subSystem : part.subsystems.values()) {
                if (subSystem instanceof SeatSubsystem seatSubSystem) {
                    boolean success = seatSubSystem.setPassenger(event.getEntity());
                    if (success) break;
                    event.setCancellationResult(InteractionResult.SUCCESS);
                }
            }
        }
    }
}
