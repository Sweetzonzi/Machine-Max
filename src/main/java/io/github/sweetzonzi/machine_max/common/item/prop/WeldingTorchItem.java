package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.sound.SpreadingSoundHelper;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machine_max.common.item.ICustomModelItem;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Objects;

public class WeldingTorchItem extends Item implements ICustomModelItem {
    public static final float ASSEMBLY_PER_TICK = 1f;
    public static final float SUBPART_REPAIR_PER_TICK = 1f;
    public static final float SUBSYSTEM_REPAIR_PER_TICK = 1f;
    public static final float CONNECTOR_REPAIR_PER_TICK = 1f;

    public WeldingTorchItem() {
        super(new Properties()
                .stacksTo(1)
                .durability(6000));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        player.startUsingItem(usedHand);
        return InteractionResultHolder.consume(player.getItemInHand(usedHand));
    }

    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity livingEntity, @NotNull ItemStack stack, int remainingUseDuration) {
        super.onUseTick(level, livingEntity, stack, remainingUseDuration);
        if (livingEntity instanceof Player player) {
            LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
            SubPart subPart = eyesight.getSubPart();
            Entity entity = eyesight.getEntity();
            if (subPart != null) {
                if (!level.isClientSide()) { // 服务端负责实际数值的更新
                    stack.hurtAndBreak(1, livingEntity, EquipmentSlot.MAINHAND);
                    if (getUseDuration(stack, livingEntity) - remainingUseDuration < 15 || remainingUseDuration % 5 != 0)
                        return;
                    Part part = subPart.part;
                    if (!livingEntity.isCrouching() && !subPart.destroyed) { // 一般状态下组装部件并尝试维修
                        part.assemble(player.getInventory(), 5 * ASSEMBLY_PER_TICK);
                        subPart.repair(
                                5 * SUBPART_REPAIR_PER_TICK,
                                5 * SUBSYSTEM_REPAIR_PER_TICK,
                                5 * CONNECTOR_REPAIR_PER_TICK);
                    } else { // 潜行时拆解部件为原材料
                        if (part.getAssemblingProgress() > 0) {
                            part.disassemble(player.getInventory(), 5 * ASSEMBLY_PER_TICK);
                            if (part.assemblingProgress <= 0) {
                                // 停止使用动作，保留0进度的部件
                                livingEntity.stopUsingItem();
                            }
                        } else { // 对0进度的部件再次潜行使用物品才会拆除
                            part.vehicle.removePart(part);
                        }
                    }
                } else { // 客户端仅负责音效与粒子效果
                    boolean shouldPlayEffect = subPart.part.getAssemblingProgress() < 1;
                    if (!shouldPlayEffect)
                        shouldPlayEffect = subPart.getDurability() < subPart.getMaxDurability();
                    if (!shouldPlayEffect)
                        for (AbstractSubsystem subsystem : subPart.getSubsystems().values()) {
                            if (subsystem.getDurability() < subsystem.getMaxDurability()) {
                                shouldPlayEffect = true;
                                break;
                            }
                        }
                    if (!shouldPlayEffect)
                        for (AbstractConnector connector : subPart.getConnectors().values()) {
                            if (connector.getIntegrity() < connector.getBasicIntegrity()) {
                                shouldPlayEffect = true;
                                break;
                            }
                        }
                    if (shouldPlayEffect) {
                        var ray = eyesight.getTargetsCache().get(subPart.body);
                        var hitPos = eyesight.getHitPoint(ray);
                        var normal = ray.getHitNormalLocal(null).mult(0.2f);
                        for (int i = 0; i < 5; i++) {
                            level.addParticle(
                                    ParticleTypes.FIREWORK,
                                    hitPos.x,
                                    hitPos.y,
                                    hitPos.z,
                                    normal.x + 0.2 * (Math.random() - 0.5),
                                    normal.y + 0.2 * (Math.random() - 0.5),
                                    normal.z + 0.2 * (Math.random() - 0.5)
                            );
                        }
                        playWeldingSound(level, player, 1.0f, 1.0f);
                    } else {
                        if (Math.random() < 0.1) {
                            level.addParticle(
                                    ParticleTypes.FIREWORK,
                                    player.getEyePosition().x,
                                    player.getEyePosition().y,
                                    player.getEyePosition().z,
                                    0.2 * (Math.random() - 0.5),
                                    0.2 * (Math.random() - 0.5),
                                    0.2 * (Math.random() - 0.5)
                            );
                        }
                        playWeldingSound(level, player, 1.5f, 0.3f);
                    }
                }
            } else if (entity != null) {
                if (!level.isClientSide()) {
                    stack.hurtAndBreak(1, livingEntity, EquipmentSlot.MAINHAND);
                    if (getUseDuration(stack, livingEntity) - remainingUseDuration < 10) return; // 0.5秒预热时间
                    DamageSource damageSource = level.damageSources().source(DamageTypes.IN_FIRE, player);
                    entity.hurt(damageSource, 0.1f);
                    if (entity instanceof LivingEntity living) living.invulnerableTime = 0;
                    entity.igniteForSeconds(3);
                } else {
                    for (int i = 0; i < 5; i++) {
                        level.addParticle(
                                ParticleTypes.FIREWORK,
                                entity.getX(),
                                entity.getY(),
                                entity.getZ(),
                                1.5 * (Math.random() - 0.5),
                                1.5 * (Math.random() - 0.5),
                                1.5 * (Math.random() - 0.5)
                        );
                    }
                    playWeldingSound(level, player, 1.0f, 1.0f);
                }
            } else if (level.isClientSide()) {
                if (Math.random() < 0.1) {
                    level.addParticle(
                            ParticleTypes.FIREWORK,
                            player.getEyePosition().x,
                            player.getEyePosition().y,
                            player.getEyePosition().z,
                            0.2 * (Math.random() - 0.5),
                            0.2 * (Math.random() - 0.5),
                            0.2 * (Math.random() - 0.5)
                    );
                }
                playWeldingSound(level, player, 1.5f, 0.3f);
            }
        }
    }

    private void playWeldingSound(Level level, Player player, float pitch, float volume) {
        SoundEvent sound = SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item.part.painted"), 32f);
        SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.PLAYERS, player.getPosition(1), player.getDeltaMovement().scale(20), (float) (pitch + 0.2f * (Math.random() - 0.5f)), volume);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }


    public ItemAnimatable createItemAnimatable(ItemStack itemStack, Level level, ItemDisplayContext context) {
        var animatable = new ItemAnimatable(itemStack, level);
        HashMap<ItemDisplayContext, ItemAnimatable> customModels;
        if (itemStack.has(MMDataComponents.getCUSTOM_ITEM_MODEL()) && !Objects.requireNonNull(itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL())).isEmpty())
            customModels = itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL());
        else customModels = new HashMap<>();
        animatable.getModelController().setModel(
                new ModelIndex("item", ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "welding_torch")));
        animatable.getModelController().setTextureLocation(
                ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/item/welding_torch.png"));
        if (customModels != null) {
            customModels.put(context, animatable);
            itemStack.set(MMDataComponents.getCUSTOM_ITEM_MODEL(), customModels);
        }
        return animatable;
    }

    @Override
    public Vector3f getRenderOffset(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        if (displayContext.firstPerson())
            return new Vector3f(0.4f, -0.3f, 0);
        if (displayContext == ItemDisplayContext.GROUND) return new Vector3f(0, -0.2f, 0);
        if (displayContext == ItemDisplayContext.GUI) return new Vector3f(0.23f, -0.28f, 0);
        return new Vector3f(0, -0.15f, 0);
    }

    @Override
    public Vector3f getRenderRotation(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        if (displayContext.firstPerson()
                || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.GROUND)
            return ICustomModelItem.super.getRenderRotation(itemStack, level, displayContext);
        return new Vector3f(0, 45, 30).mul((float) (Math.PI / 180));
    }

    @Override
    public Vector3f getRenderScale(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        if (displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.GROUND
                || displayContext == ItemDisplayContext.FIXED)
            return new Vector3f(0.45f);
        if (displayContext == ItemDisplayContext.GUI)
            return new Vector3f(0.75f);
        return ICustomModelItem.super.getRenderScale(itemStack, level, displayContext);
    }

}
