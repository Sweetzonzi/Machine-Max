package io.github.sweetzonzi.machinemax.common.item.prop;

import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.item.ICustomModelItem;
import io.github.sweetzonzi.machinemax.common.attachment.LivingEntityEyesightAttachment;
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
import java.util.Map;

public class CrowbarItem extends Item implements IPartInteractableItem, ICustomModelItem {
    public CrowbarItem(Properties properties) {
        super(properties);
        properties.stacksTo(1);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (!player.level().isClientSide) {
            LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
            Part part = eyesight.getPart();
            if (part != null) {//移除部件
                part.vehicle.removePart(part);
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
                Minecraft.getInstance().player.displayClientMessage(Component.translatable("tooltip.machinemax.crossbar.interact").append(part.name), true);
            } else Minecraft.getInstance().player.displayClientMessage(Component.empty(), true);
        }
    }

    @Override
    public void interactWitchPart(@NotNull Part part, @NotNull Player player) {

    }

    @Override
    public void watchingPart(@NotNull Part part, @NotNull Player player) {
        if (player.level().isClientSide)
            Minecraft.getInstance().player.displayClientMessage(Component.translatable("tooltip.machinemax.crossbar.interact").append(part.name), true);
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
        if (context.firstPerson())
            animatable.setModelIndex(
                    new ModelIndex(
                            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item/crowbar_first_person.geo"),
                            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/item/crowbar_first_person.png"))
            );
        else
            animatable.setModelIndex(
                    new ModelIndex(
                            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item/crowbar.geo"),
                            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/item/crowbar.png"))
            );
        if (customModels != null) {
            customModels.put(context, animatable);
            itemStack.set(MMDataComponents.getCUSTOM_ITEM_MODEL(), customModels);
        }
        return animatable;
    }

    @Override
    public Vector3f getRenderOffset(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        if (displayContext == ItemDisplayContext.GUI
                || displayContext == ItemDisplayContext.FIXED
                || displayContext == ItemDisplayContext.GROUND)
            return new Vector3f(-0.1f, -0.05f, 0);
        return ICustomModelItem.super.getRenderOffset(itemStack, level, displayContext);
    }

    @Override
    public Vector3f getRenderRotation(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        if (displayContext == ItemDisplayContext.GUI
                || displayContext == ItemDisplayContext.FIXED
                || displayContext == ItemDisplayContext.GROUND)
            return new Vector3f(0, -85f, -45f).mul((float) (Math.PI / 180));
        return ICustomModelItem.super.getRenderRotation(itemStack, level, displayContext);
    }
}
