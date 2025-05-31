package io.github.sweetzonzi.machinemax.common.item.prop;

import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machinemax.common.entity.MMPartEntity;
import io.github.sweetzonzi.machinemax.common.item.ICustomModelItem;
import io.github.sweetzonzi.machinemax.common.item.IPartInteractableItem;
import io.github.sweetzonzi.machinemax.common.registry.MMAttachments;
import io.github.sweetzonzi.machinemax.common.registry.MMDataComponents;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.HashMap;

public class SprayCanItem extends Item implements IPartInteractableItem, ICustomModelItem {
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
        } else return InteractionResultHolder.pass(player.getItemInHand(usedHand));
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

    @Override
    public void interactWitchPart(@NotNull Part part, @NotNull Player player) {

    }

    @Override
    public void watchingPart(@NotNull Part part, @NotNull Player player) {
        if (player.level().isClientSide)
            Minecraft.getInstance().player.displayClientMessage(Component.translatable("tooltip.machinemax.spray_can.interact").append(part.name), true);
    }

    @Override
    public void stopWatchingPart(@NotNull Player player) {
        if (player.level().isClientSide)
            Minecraft.getInstance().player.displayClientMessage(Component.empty(), true);
    }

    public ItemAnimatable createItemAnimatable(ItemStack itemStack, Level level, ItemDisplayContext context) {
        var animatable = new ItemAnimatable(itemStack, level);
        HashMap<ItemDisplayContext, ItemAnimatable> customModels;
        if (itemStack.has(MMDataComponents.getCUSTOM_ITEM_MODEL()))
            customModels = itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL());
        else customModels = new HashMap<>();
        animatable.setModelIndex(
                new ModelIndex(
                        ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item/spray_can.geo"),
                        ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/item/spray_can.png"))
        );
        if (customModels != null) {
            customModels.put(context, animatable);
            itemStack.set(MMDataComponents.getCUSTOM_ITEM_MODEL(), customModels);
        }
        return animatable;
    }

    @Override
    public Vector3f getRenderOffset(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        if (displayContext.firstPerson())
            return new Vector3f(0, 0, 0);
        else return new Vector3f(0.05f, -0.1f, 0);
    }

    @Override
    public Vector3f getRenderRotation(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        if (displayContext.firstPerson()
                ||displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
            return ICustomModelItem.super.getRenderRotation(itemStack, level, displayContext);
        return new Vector3f(0, 45, 30).mul((float) (Math.PI/180));
    }

    @Override
    public Vector3f getRenderScale(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        if (displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.GROUND
                || displayContext == ItemDisplayContext.FIXED)
            return new Vector3f(0.5f, 0.5f, 0.5f);
        return ICustomModelItem.super.getRenderScale(itemStack, level, displayContext);
    }
}
