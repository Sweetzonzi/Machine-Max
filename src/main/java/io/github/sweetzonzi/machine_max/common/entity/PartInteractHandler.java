package io.github.sweetzonzi.machine_max.common.entity;

import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import static io.github.sweetzonzi.machine_max.MachineMax.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class PartInteractHandler {
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
                eyeSight.getTargetBodies().clear();
                player.removeData(MMAttachments.getENTITY_EYESIGHT());
            }
        }
    }

    @SubscribeEvent
    private static void hurt(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        DamageSource source = event.getSource();
        //乘坐载具的实体首先进行射线检测，由载具阻挡伤害
        if (((IEntityMixin) (entity)).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
            if (seat.getSubPart().getEntity() != null) {
                boolean hit = seat.getSubPart().getEntity().hurt(source, event.getAmount());//射线检测由部件实体负责
                if (!hit) { //若未命中任何部件
                    //额外检测乘客的载具是否全包裹，即是否无条件阻挡伤害
                    if (seat.attr.staticAttribute.blockDamage) {
                        seat.getSubPart().getEntity().hurtWithoutRayTest(source, event.getAmount());
                        event.setCanceled(true);
                    }
                } else event.setCanceled(true);//命中部件则伤害被部件阻挡
            }
        }
    }
}
