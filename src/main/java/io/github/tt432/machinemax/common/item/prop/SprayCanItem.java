package io.github.tt432.machinemax.common.item.prop;

import io.github.tt432.machinemax.common.attachment.LivingEntityEyesightAttachment;
import io.github.tt432.machinemax.common.entity.MMPartEntity;
import io.github.tt432.machinemax.common.registry.MMAttachments;
import io.github.tt432.machinemax.common.vehicle.Part;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SprayCanItem extends Item {
    public SprayCanItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (!level.isClientSide) {
            LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
            Part part = eyesight.getPart();
            if (part != null) {//改变瞄准的部件的涂装
                //TODO:播放声音与粒子效果
                part.switchTexture(part.textureIndex + 1);
                return InteractionResultHolder.success(player.getItemInHand(usedHand));
            } else return InteractionResultHolder.pass(player.getItemInHand(usedHand));
        }else return InteractionResultHolder.pass(player.getItemInHand(usedHand));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int portId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, portId, isSelected);
        if (isSelected && level.isClientSide() && entity instanceof Player player) {
            LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
            Part part = eyesight.getPart();
            if (part != null) {//提示信息
                Minecraft.getInstance().player.displayClientMessage(Component.translatable("tooltip.machinemax.spray_can.interact").append(part.name), true);
            } else if (eyesight.getEntity() instanceof MMPartEntity partEntity && partEntity.part != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.translatable("tooltip.machinemax.spray_can.interact").append(partEntity.part.name), true);
            } else Minecraft.getInstance().player.displayClientMessage(Component.empty(), true);
        }
    }
}
