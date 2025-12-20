package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.sound.SpreadingSoundHelper;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class WeldingTorchItem extends Item {
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
            if (subPart != null) {
                if (!level.isClientSide()) { // 服务端负责实际数值的更新
                    stack.hurtAndBreak(1, livingEntity, EquipmentSlot.MAINHAND);
                    if (remainingUseDuration % 5 != 0) return;
                    Part part = subPart.part;
                    if (!livingEntity.isCrouching() && !subPart.destroyed) { // 一般状态下组装部件并尝试维修
                        part.assemble(player.getInventory(), 5 * ASSEMBLY_PER_TICK);
                        subPart.repair(
                                5 * SUBPART_REPAIR_PER_TICK,
                                5 * SUBSYSTEM_REPAIR_PER_TICK,
                                5 * CONNECTOR_REPAIR_PER_TICK);
                    } else { // 潜行时拆解部件为原材料
                        part.disassemble(player.getInventory(), 5 * ASSEMBLY_PER_TICK);
                        if (part.assemblingProgress <= 0) part.vehicle.removePart(part);
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
                    } else playWeldingSound(level, player, 1.5f, 0.5f);
                }
            } else if (level.isClientSide()) playWeldingSound(level, player, 1.5f, 0.5f);
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

}
