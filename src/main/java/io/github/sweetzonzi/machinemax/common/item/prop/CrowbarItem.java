package io.github.sweetzonzi.machinemax.common.item.prop;

import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.util.PPhase;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.item.ICustomModelItem;
import io.github.sweetzonzi.machinemax.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machinemax.common.item.IPartInteractableItem;
import io.github.sweetzonzi.machinemax.common.registry.MMAttachments;
import io.github.sweetzonzi.machinemax.common.registry.MMDataComponents;
import io.github.sweetzonzi.machinemax.common.registry.MMItems;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.PartType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.awt.*;
import java.util.*;

public class CrowbarItem extends Item implements IPartInteractableItem, ICustomModelItem {
    public CrowbarItem() {
        super(new Properties()
                .stacksTo(1)
                .durability(1000)
                .attributes(CrowbarItem.createAttributes(Tiers.IRON, 1f, 0f)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand usedHand) {
        if (!player.level().isClientSide) {
            ItemStack crowbar = player.getItemInHand(usedHand);
            LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
            Part part = eyesight.getPart();
            if (part != null) {
                PartType partType = part.type;
                float durability = part.durability;
                if (part.integrity > 0.05 * partType.basicIntegrity || !player.isCreative()) {
                    if (part.entity != null) {
                        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
                        float scale = player.getAttackStrengthScale(0.5f);
                        DamageSource damageSource = level.damageSources().playerAttack(player);
                        float finalDamage = EnchantmentHelper.modifyDamage((ServerLevel) level, player.getWeaponItem(), part.entity, damageSource, damage);
                        level.getPhysicsLevel().submitDeduplicatedTask("disassembly_" + player.getStringUUID(), PPhase.PRE, () -> {
                            if (part.entity != null) {
                                part.integrity = Math.clamp(part.integrity - finalDamage * scale, 0, part.type.basicIntegrity);
                                part.entity.hurt(damageSource, finalDamage * scale * 2);
                                part.syncStatus();
                            }
                            return null;
                        });
                    }
                } else {
                    part.vehicle.removePart(part);
                    if (!player.isCreative()) {//非创造模式，则尝试获取为物品
                        ItemStack itemStack = new ItemStack(MMItems.getPART_ITEM());
                        itemStack.set(MMDataComponents.getPART_TYPE(), partType.registryKey);
                        itemStack.set(DataComponents.MAX_DAMAGE, (int) partType.basicDurability);
                        itemStack.set(DataComponents.DAMAGE, (int) Math.clamp(partType.basicDurability - durability, 0, Math.ceil(partType.basicDurability)));
                        if (!player.addItem(itemStack)) {//尝试直接放入物品栏，失败则掉落为实体
                            Entity itemStackEntity = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), itemStack);
                            level.addFreshEntity(itemStackEntity);
                        }
                    }
                }
                player.resetAttackStrengthTicker();
                crowbar.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                return InteractionResultHolder.success(player.getItemInHand(usedHand));
            } else return InteractionResultHolder.pass(player.getItemInHand(usedHand));
        } else return InteractionResultHolder.pass(player.getItemInHand(usedHand));
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int portId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, portId, isSelected);
        if (isSelected && level.isClientSide() && entity instanceof Player player) {
            LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
            Part part = eyesight.getPart();
            if (part != null) {//提示信息
                if (part.integrity > 0.05 * part.type.basicIntegrity && !player.isCreative())
                    player.displayClientMessage(Component.translatable("tooltip.machine_max.crowbar.unsafe_disassembly",
                            part.integrity, part.type.basicIntegrity, Component.translatable(part.type.registryKey.toLanguageKey())).withColor(Color.ORANGE.getRGB()), true);
                else
                    player.displayClientMessage(Component.translatable("tooltip.machine_max.crowbar.safe_disassembly",
                            Component.translatable(part.type.registryKey.toLanguageKey())), true);
            } else player.displayClientMessage(Component.empty(), true);
        }
    }

    public static ItemAttributeModifiers createAttributes(Tier tier, float attackDamage, float attackSpeed) {
        return ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(
                                BASE_ATTACK_DAMAGE_ID, attackDamage + tier.getAttackDamageBonus(), AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.MAINHAND
                )
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, attackSpeed, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND
                )
                .build();
    }

    @Override
    public void postHurtEnemy(ItemStack stack, @NotNull LivingEntity target, @NotNull LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public void interactWitchPart(@NotNull Part part, @NotNull Player player) {

    }

    @Override
    public void watchingPart(@NotNull Part part, @NotNull Player player) {
        if (player.level().isClientSide)
            player.displayClientMessage(Component.translatable("tooltip.machine_max.crowbar.interact").append(part.name), true);
    }

    @Override
    public void stopWatchingPart(@NotNull Player player) {
        if (player.level().isClientSide)
            player.displayClientMessage(Component.empty(), true);
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
