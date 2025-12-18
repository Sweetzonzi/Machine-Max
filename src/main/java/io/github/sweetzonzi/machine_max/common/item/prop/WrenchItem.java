package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.util.PPhase;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class WrenchItem extends Item {
    public WrenchItem() {
        super(new Properties()
                .stacksTo(1)
                .durability(500)
                .attributes(createAttributes(Tiers.IRON, 0f, -2f)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand usedHand) {
        if (!player.level().isClientSide) {
            ItemStack wrench = player.getItemInHand(usedHand);
            LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
            SubPart subPart = eyesight.getSubPart();
            if (subPart != null) {
                Part part = subPart.part;
                if ((player.isCrouching() || subPart.destroyed)) {
                    //潜行时拆除模式
                    float repair = 10;
                    float scale = player.getAttackStrengthScale(0.5f);
                    level.getPhysicsLevel().submitDeduplicatedTask("repair_" + player.getStringUUID(), PPhase.PRE, () -> {
                        if (subPart.entity != null) {
//                            part.integrity = Math.clamp(part.integrity - repair * scale, 0, part.type.basicIntegrity);
//                            subPart.syncToClient();
                        }
                        return null;
                    });
                } else {
                    //一般状态下修复模式
                    float repair = 10;
                    float scale = player.getAttackStrengthScale(0.5f);
                    level.getPhysicsLevel().submitDeduplicatedTask("repair_" + player.getStringUUID(), PPhase.PRE, () -> {
                        if (subPart.entity != null) {
//                            part.integrity = Math.clamp(part.integrity + repair * scale, 0, part.type.basicIntegrity);
//                            part.sharedDurability = Math.clamp(part.sharedDurability + repair * scale, 0, part.type.basicDurability);
                            subPart.syncToClient();
                        }
                        return null;
                    });
                }
                player.resetAttackStrengthTicker();
                wrench.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                return InteractionResultHolder.success(player.getItemInHand(usedHand));
            } else return InteractionResultHolder.pass(player.getItemInHand(usedHand));
        } else return InteractionResultHolder.pass(player.getItemInHand(usedHand));
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int portId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, portId, isSelected);
        if (isSelected && !level.isClientSide() && entity instanceof Player player) {
            if (player.tickCount % 5 != 0) return;
            LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
            SubPart subPart = eyesight.getSubPart();
            if (subPart != null) {//提示信息
                Part part = subPart.part;
                if (entity.isCrouching()) {
                    part.assemble(player.getInventory(), 5);
                    player.displayClientMessage(Component.translatable("tooltip.machine_max.wrench.disassembly", Component.translatable(part.type.registryKey.toLanguageKey())).withColor(Color.ORANGE.getRGB()), true);
                } else if (!subPart.destroyed) {
                    part.disAssemble(player.getInventory(), 5);
                    player.displayClientMessage(Component.translatable("tooltip.machine_max.wrench.repair",
                            Component.translatable(part.type.registryKey.toLanguageKey()).withColor(Color.GREEN.getRGB())), true);
                } else {
                    player.displayClientMessage(Component.translatable("tooltip.machine_max.wrench.cant_repair",
                            Component.translatable(part.type.registryKey.toLanguageKey())).withColor(Color.RED.getRGB()), true);
                }
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
                .add(
                        Attributes.ATTACK_KNOCKBACK,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "wrench_knockback"), 0.5f, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND
                )
                .build();
    }

    @Override
    public void postHurtEnemy(ItemStack stack, @NotNull LivingEntity target, @NotNull LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }
}
