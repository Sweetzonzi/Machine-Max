package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.api.ParticleEffects;
import cn.solarmoon.spark_core.api.SpreadingSoundHelper;
import cn.solarmoon.spark_core.util.SparkMathKt;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import io.github.sweetzonzi.machine_max.common.attachment.BlueprintAttachment;
import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machine_max.common.item.ICustomModelItem;
import io.github.sweetzonzi.machine_max.common.mech.control.view.ViewMath;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLink;
import io.github.sweetzonzi.machine_max.util.data.RpAddReason;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;


public class WeldingTorchItem extends Item implements ICustomModelItem {
    public static final float ASSEMBLY_PER_TICK = 1f;
    public static final float SUBPART_REPAIR_PER_TICK = 1f;
    public static final float SUBSYSTEM_REPAIR_PER_TICK = 1f;
    public static final float CONNECTOR_REPAIR_PER_TICK = 1f;
    public static final float VEHICLE_REPAIR_PER_TICK = 1f;
    private static final SoundEvent WELDING_START_SOUND = SoundEvent.createFixedRangeEvent(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item.welding_torch.start"), 32f);
    private static final SoundEvent WELDING_LOOP_SOUND = SoundEvent.createFixedRangeEvent(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item.welding_torch.loop"), 32f);
    private static final SoundEvent WELDING_END_SOUND = SoundEvent.createFixedRangeEvent(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item.welding_torch.end"), 32f);
    /** 焊接火花用的内容包粒子效果，与命中火花是同一套：火花之外，烟尘与闪光由效果自身的事件带出。 */
    private static final ResourceLocation WELD_EFFECT =
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "armor_spall");
    /**
     * 焊接火花的喷射间隔（tick）。
     *
     * <p>单次喷射 28 颗火花，逐 tick 喷射相当于约 560 颗/秒、同屏两百余颗，远密于焊接应有的观感；
     * 每 2 tick 一次是约 280 颗/秒。</p>
     */
    private static final int WELD_EFFECT_INTERVAL = 2;

    public WeldingTorchItem() {
        super(new Properties()
                .stacksTo(1)
                .durability(6000));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        player.startUsingItem(usedHand);
        if (level.isClientSide()) {
            playWeldingStartSound(level, player);
        }
        return InteractionResultHolder.consume(player.getItemInHand(usedHand));
    }

    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity livingEntity, @NotNull ItemStack stack, int remainingUseDuration) {
        super.onUseTick(level, livingEntity, stack, remainingUseDuration);
        if (livingEntity instanceof Player player) {
            // 座位上禁止使用物品时不推进焊接，也不产生任何 actionbar 提示
            if (!SeatSubsystem.canUseItems(player)) return;
            LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
            SubPart subPart = eyesight.getSubPart();
            Entity entity = eyesight.getEntity();
            if (subPart != null) {
                if (!(subPart.part instanceof RegularPart part)) return;
                if (!level.isClientSide()) { // 服务端负责实际数值的更新
                    stack.hurtAndBreak(1, livingEntity, EquipmentSlot.MAINHAND);
                    if (getUseDuration(stack, livingEntity) - remainingUseDuration < 15 || remainingUseDuration % 5 != 0)
                        return;
                    if (!livingEntity.isCrouching() && !subPart.isDestroyed()) { // 一般状态下按「装配 → 轮子 → 履带 → 车体」串行焊接
                        float repairStep = 5;
                        float assembleStep = 5;
                        boolean progressed = false;
                        // 1. 零件装配：进度先走，耐久按增量跟着补，后面的维修才有更高的上限可修
                        boolean assembled = false;
                        BlueprintAttachment blueprint = player.getData(MMAttachments.getBLUEPRINT());
                        if (blueprint.canAdvanceAssembly(player, part)) {
                            assembled = part.assemble(player.getInventory(), assembleStep * ASSEMBLY_PER_TICK);
                            progressed = assembled;
                        } else {
                            player.displayClientMessage(Component.translatable(
                                    MMLanguageRegistry.MESSAGE_ASSEMBLY_LOCKED.tag,
                                    Component.translatable(part.getName())), true);
                        }
                        // 2. 轮子：子部件耐久 / 子系统 / 连接点。装配有进展的拍不再分给它
                        if (!progressed) {
                            progressed = subPart.repair(
                                    repairStep * SUBPART_REPAIR_PER_TICK,
                                    repairStep * SUBSYSTEM_REPAIR_PER_TICK,
                                    repairStep * CONNECTOR_REPAIR_PER_TICK);
                        }
                        // 3. 履带：建造或维修。履带要焊时本拍不再给车体 HP——「缺料」的提示
                        //    若被车体维修顶掉，玩家永远看不到原因
                        boolean trackPending = false;
                        if (!progressed) {
                            TrackLink track = subPart.trackLink;
                            if (track != null && !track.isDisposed() && track.needsWelding()) {
                                trackPending = true;
                                if (!track.canWeld()) {
                                    player.displayClientMessage(Component.translatable(
                                            MMLanguageRegistry.MESSAGE_TRACK_WAITING_WHEELS.tag), true);
                                } else if (track.needsBuild()) {
                                    int cost = track.linkCount();
                                    if (track.buildTrack(player.getInventory())) {
                                        progressed = true;
                                    } else {
                                        player.displayClientMessage(Component.translatable(
                                                MMLanguageRegistry.MESSAGE_TRACK_BUILD_NEED.tag, cost,
                                                track.heldLinks(player.getInventory())), true);
                                    }
                                } else if (track.repair(player.getInventory(), repairStep)) {
                                    progressed = true;
                                } else {
                                    int need = track.missingTornLinks();
                                    player.displayClientMessage(need > 0
                                            ? Component.translatable(MMLanguageRegistry.MESSAGE_TRACK_BREACH_NEED.tag,
                                            need, track.heldLinks(player.getInventory()))
                                            : Component.translatable(MMLanguageRegistry.MESSAGE_TRACK_FULL.tag), true);
                                }
                            }
                        }
                        // 4. 车体 HP 是最后一个口袋
                        if (!progressed && !trackPending) {
                            progressed = subPart.getPart().getAssembly() instanceof VehicleCore vc
                                    && vc.repair(repairStep * VEHICLE_REPAIR_PER_TICK);
                        }
                        // 研发点按本拍实际做的那件事记：装配与维修互斥，同一拍不重复记两份
                        if (remainingUseDuration % 10 == 0) {
                            if (assembled) {
                                BlueprintAttachment.giveRp(player, (int) assembleStep, RpAddReason.ASSEMBLY);
                            } else if (progressed) {
                                BlueprintAttachment.giveRp(player, (int) repairStep, RpAddReason.REPAIR);
                            }
                        }
                    } else { // 潜行时拆解部件为原材料
                        if (part.getAssemblingProgress() > 0) {
                            part.disassemble(player.getInventory(), 5 * ASSEMBLY_PER_TICK);
                            if (part.getAssemblingProgress() <= 0) {
                                // 停止使用动作，保留0进度的部件
                                livingEntity.stopUsingItem();
                            }
                        } else { // 对0进度的部件再次潜行使用物品才会拆除
                            // 零件内若含履带成员，先整条结算并掉落在零件位置
                            TrackLink.dissolveTracksIn(part.subParts.values(), player, player.position());
                            part.assembly.removePart(part);
                        }
                    }
                } else { // 客户端仅负责音效与粒子效果
                    boolean shouldPlayEffect = shouldPlayEffect(subPart);
                    if (shouldPlayEffect) {
                        if (player.tickCount % WELD_EFFECT_INTERVAL == 0) {
                            var ray = eyesight.getTargetBodyCache().get(subPart.body);
                            var normal = ray.getHitNormalLocal(null);
                            // 法线统一取"朝向持枪者"的那一侧：焊接火花是从焊点朝焊工喷出来的，
                            // 引擎给的法线朝向取决于射线从哪一面命中
                            if (normal.dot(eyesight.getView()) > 0f) normal.negateLocal();
                            spawnWeldSparks(level,
                                    SparkMathKt.toVec3(eyesight.getHitPoint(ray)),
                                    SparkMathKt.toVec3(normal));
                        }
                        playWeldingLoopSound(level, player, 1.0f, 1.0f);
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
                        playWeldingLoopSound(level, player, 1.5f, 0.3f);
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
                    Vec3 center = entity.getBoundingBox().getCenter();
                    if (player.tickCount % WELD_EFFECT_INTERVAL == 0) {
                        // 火花从实体身上朝持枪的人喷回来
                        spawnWeldSparks(level, center, player.getEyePosition().subtract(center));
                    }
                    playWeldingLoopSound(level, player, 1.0f, 1.0f);
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
                playWeldingLoopSound(level, player, 1.5f, 0.3f);
            }
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged) {
        super.releaseUsing(stack, level, livingEntity, timeCharged);
        if (level.isClientSide() && livingEntity instanceof Player player) {
            playWeldingEndSound(level, player);
        }
    }

    private boolean shouldPlayEffect(SubPart subPart) {
        boolean shouldPlayEffect = subPart.getPart().getAssembly() instanceof VehicleCore vc ? vc.getHp() < vc.getMaxHp() : false;
        if (!shouldPlayEffect) {
            shouldPlayEffect = subPart.getDurability() < subPart.getMaxDurability();
        }
        if (!shouldPlayEffect)
            for (AbstractSubsystem subsystem : subPart.ownSubsystems()) {
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
        if (!shouldPlayEffect)
            shouldPlayEffect = subPart.part instanceof RegularPart regular && regular.getAssemblingProgress() < 1;
        // 履带要焊（未建造 / 断口 / 战损）就冒火花：判据是「未满」，缺料时照常冒火花——
        // 火花不能接在 repair(...) 的返回值上，缺料与整条已满这两种 false 的表现相反
        if (!shouldPlayEffect && subPart.trackLink != null && !subPart.trackLink.isDisposed()) {
            shouldPlayEffect = subPart.trackLink.needsWelding();
        }
        return shouldPlayEffect;
    }

    /**
     * 在 {@code pos} 沿 {@code axis} 喷射一簇焊接火花，只在客户端调用。
     *
     * @param pos  喷射点（世界坐标）
     * @param axis 喷射轴（世界坐标，无需预先归一化）
     */
    private static void spawnWeldSparks(Level level, Vec3 pos, Vec3 axis) {
        ParticleEffects.burst(level, WELD_EFFECT, pos, SparkMathKt.toQuaternionf(ViewMath.axisAlong(axis)));
    }

    private void playWeldingStartSound(Level level, Player player) {
        SpreadingSoundHelper.playSpreadingSound(level, WELDING_START_SOUND, SoundSource.PLAYERS, player.getPosition(1), player.getDeltaMovement().scale(20), (float) (1.0f + 0.2f * (Math.random() - 0.5f)), 1.0f);
    }

    private void playWeldingLoopSound(Level level, Player player, float pitch, float volume) {
        SoundEvent sound = SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item.part.painted"), 32f);
        SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.PLAYERS, player.getPosition(1), player.getDeltaMovement().scale(20), (float) (pitch + 0.2f * (Math.random() - 0.5f)), volume);
    }

    private void playWeldingEndSound(Level level, Player player) {
        SpreadingSoundHelper.playSpreadingSound(level, WELDING_END_SOUND, SoundSource.PLAYERS, player.getPosition(1), player.getDeltaMovement().scale(20), (float) (1.0f + 0.2f * (Math.random() - 0.5f)), 1.0f);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }


    public IAnimatable<?> createItemAnimatable(ItemStack itemStack, Level level, ItemDisplayContext context) {
        var animatable = new ItemAnimatable(itemStack, level);
        animatable.getModelController().setModel(
                new ModelIndex("item", ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "welding_torch")));
        animatable.getModelController().setTextureLocation(
                ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/item/welding_torch.png"));
        cacheAnimatable(itemStack, context, animatable);
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
        return new Vector3f(25, 30, 0).mul((float) (Math.PI / 180));
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
