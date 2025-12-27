package io.github.sweetzonzi.machine_max.common.attachment;

import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.sound.SpreadingSoundHelper;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.prop.PartAssemblyItem;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.vehicle.ObjectManager;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AttachPointConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.SpecialConnector;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import io.github.sweetzonzi.machine_max.network.payload.assembly.PlayerPartAssemblyCacheSyncPayload;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Iterator;

@Getter
@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class VehicleAssemblyAttachment {
    public final LivingEntity owner;
    @Nullable
    public PartType partType = null;
    @Nullable
    public Iterator<String> variantIterator = null;
    @Nullable
    @Setter
    private String variantName = null;
    @Nullable
    public Iterator<Pair<String, String>> connectorIterator = null;
    @Nullable
    @Setter
    private Pair<String, String> connectorName = null;
    @Setter
    private Vector3f offset = new Vector3f();
    @Setter
    private Quaternionf quaternion = new Quaternionf();

    public VehicleAssemblyAttachment(LivingEntity entity) {
        this.owner = entity;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTick(EntityTickEvent.Pre event) {
        Level level = event.getEntity().level();
        if (event.getEntity() instanceof Player player && !player.hasData(MMAttachments.getVEHICLE_ASSEMBLY().get())) {
            // 自动为所有玩家初始化载具组装用缓存
            player.setData(MMAttachments.getVEHICLE_ASSEMBLY().get(), new VehicleAssemblyAttachment(player));
        }
        if (event.getEntity() instanceof LivingEntity entity && entity.hasData(MMAttachments.getVEHICLE_ASSEMBLY().get())) {
            VehicleAssemblyAttachment cache = entity.getData(MMAttachments.getVEHICLE_ASSEMBLY().get());
            ItemStack stack = null; // 获取玩家手中可用于组装载具的物品
            if (entity.getMainHandItem().getItem() instanceof PartAssemblyItem) {
                stack = entity.getMainHandItem();
            } else if (entity.getOffhandItem().getItem() instanceof PartAssemblyItem) {
                stack = entity.getOffhandItem();
            }
            if (stack != null) {
                PartType newPartType = PartAssemblyItem.getPartType(stack, level);
                cache.setPartType(newPartType);
                if (!level.isClientSide() && entity.hasData(MMAttachments.getENTITY_EYESIGHT().get())) {
                    var eyesight = entity.getData(MMAttachments.getENTITY_EYESIGHT());
                    AbstractConnector targetConnector = eyesight.getConnector();
                    // 服务端额外根据选中的目标连接点自动切换使用的变体和连接点
                    if (targetConnector != null && !targetConnector.hasPart() && cache.getConnector() != null) {
                        if (cache.getVariant() != null && !targetConnector.conditionCheck(cache.getPartType(), cache.getVariantName())) {
                            cache.cycleVariants();
                        }
                        if (targetConnector instanceof SpecialConnector && cache.getConnector().type().equals("Special")) {
                            cache.cycleConnectors();
                        }
                    }
                }
            } else cache.setPartType(null);
        }
    }

    public void cycleConnectors() {
        var eyesight = owner.getData(MMAttachments.getENTITY_EYESIGHT());
        AbstractConnector targetConnector = eyesight.getConnector();//获取视线看着的部件连接点
        if (targetConnector != null && !targetConnector.hasPart()) {
            PartType partType = this.getPartType();
            if (partType == null) return;
            String variantName = this.getVariantName();
            VariantAttr variantAttr = this.getVariant();
            if (variantAttr == null) return;
            int i = partType.getVariant(variantName).getPartOutwardConnectors().size();//设置最大迭代次数
            while (i > 0) {
                //循环获取下一个端口，直到找到合适的接口或到达迭代次数上限
                ConnectorAttr connectorAttr = this.getNextConnector();//获取下一个部件接口
                if (connectorAttr == null) return;
                if (connectorAttr.type().equals("AttachPoint") || targetConnector instanceof AttachPointConnector) {
                    //检查部件Tag是否与目标接口接受的类型匹配
                    if (targetConnector.conditionCheck(partType, variantName) && connectorAttr.conditionCheck(partType, variantName)) {
                        OModel model = OModel.getOrEmpty(new ModelIndex("part", partType.variants.get(variantName).getModel("default")));
                        var locators = model.getLocators();
                        OLocator partConnectorLocator = locators.get(connectorAttr.locatorName());
                        this.offset = partConnectorLocator.getOffset().toVector3f();
                        Vector3f rotation = partConnectorLocator.getRotation().toVector3f();
                        this.quaternion = new Quaternionf().rotationZYX(rotation.x, rotation.y, rotation.z);
                        break;
                    }
                }
                i--;
            }
            if (owner instanceof Player player) {
                boolean hasConnector = this.getConnectorName() != null;
                PacketDistributor.sendToPlayer((ServerPlayer) player, new PlayerPartAssemblyCacheSyncPayload(
                        partType.getRegistryKey(),
                        variantName,
                        hasConnector ? getConnectorName().getFirst() : null,
                        hasConnector ? getConnectorName().getSecond() : null,
                        this.quaternion,
                        this.offset
                ));
            }
        }
    }

    public void cycleVariants() {
        var eyesight = owner.getData(MMAttachments.getENTITY_EYESIGHT());
        AbstractConnector targetConnector = eyesight.getConnector();//获取视线看着的部件连接点
        PartType partType = this.getPartType();
        if (partType == null) return;
        int i = partType.variants.size();//设置最大迭代次数
        while (i >= 0) {
            //循环获取下一个部件变体，直到找到合适的部件变体或到达迭代次数上限
            this.getNextVariant();//获取下一个部件变体
            String variantName = this.getVariantName();
            if (targetConnector == null || targetConnector.conditionCheck(partType, variantName)) {
                this.cycleConnectors(); // 再找到最合适的连接点
                break;
            }
            i--;
        }
    }

    public InteractionResultHolder<ItemStack> assembly(Level level, LivingEntity entity, ItemStack stack) {
        ConnectorAttr connector = getConnector();
        if (level.isClientSide() || connector == null || partType == null || variantName == null) {
            return InteractionResultHolder.pass(stack);
        } else {
            try {
                var eyesight = entity.getData(MMAttachments.getENTITY_EYESIGHT());
                AbstractConnector targetConnector = eyesight.getConnector();
                if (targetConnector != null && connectorName != null) {//若有可用的接口
                    if (targetConnector.conditionCheck(partType, variantName)) {//检查变体条件
                        if ((targetConnector instanceof AttachPointConnector || connector.type().equals("AttachPoint"))) {//检查接口条件
                            VehicleCore vehicleCore = targetConnector.subPart.part.vehicle;//获取目标连接点所属的载具
                            Part part = new Part(partType, variantName, level);
                            targetConnector.adjustTransform(part, part.externalConnectors.get(connectorName));
                            vehicleCore.attachConnector(targetConnector, part.externalConnectors.get(connectorName), part);//尝试将新部件连接至接口
                            if (!entity.hasInfiniteMaterials()) VisualEffectHelper.partToPlace = null;
                            var pos = part.rootSubPart.getPosition();
                            stack.consume(1, entity);
                            SoundEvent sound = SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item.part.placed"), 32f);
                            SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.PLAYERS, entity.getPosition(1), entity.getDeltaMovement().scale(20), (float) (1f + 0.2f * (Math.random() - 0.5f)), 1.0f);
                            ((ServerLevel) level).sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 10, 1, 1, 1, 0.2f);
                            return InteractionResultHolder.consume(stack);
                        } else return InteractionResultHolder.pass(stack);
                    } else return InteractionResultHolder.pass(stack);
                } else {
                    Part part = new Part(partType, variantName, level);
                    Transform transform = new Transform(
                            PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                                    entity.getEyePosition(),
                                    entity.getEyePosition().add(entity.getViewVector(1).scale(entity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getLocation()),
                            Quaternion.IDENTITY
                    );
                    part.setTransform(transform);
                    var pos = transform.getTranslation();
                    ObjectManager.addVehicle(new VehicleCore(level, part));//否则直接放置零件
                    stack.consume(1, entity);
                    SoundEvent sound = SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item.part.placed"), 32f);
                    SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.PLAYERS, entity.getPosition(1), entity.getDeltaMovement().scale(20), (float) (1f + 0.2f * (Math.random() - 0.5f)), 1.0f);
                    ((ServerLevel) level).sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 10, 1, 1, 1, 0.01);
                    return InteractionResultHolder.consume(stack);
                }
            } catch (Exception e) {
                return InteractionResultHolder.fail(stack);
            }
        }
    }

    public void setPartType(@Nullable PartType newPartType) {
        if (newPartType != null) {
            if (this.partType == null || !this.partType.equals(newPartType)) {
                this.partType = newPartType;
                this.variantIterator = null;
                this.connectorIterator = null;
                this.getNextVariant();
                this.getNextConnector();
            }
        } else {
            this.partType = null;
            this.variantIterator = null;
            this.connectorIterator = null;
        }
    }

    @Nullable
    public VariantAttr getVariant() {
        PartType partType = getPartType();
        if (partType == null || variantName == null) return null;
        return partType.getVariant(variantName);
    }

    @Nullable
    public VariantAttr getNextVariant() {
        PartType partType = getPartType();
        if (partType == null) return null;
        // 若当前变体迭代器为空或已遍历完所有变体，则重新从头遍历
        if (variantIterator == null || !variantIterator.hasNext()) this.variantIterator = partType.getVariantIterator();
        this.variantName = variantIterator.next();
        return partType.getVariant(variantName);
    }

    @Nullable
    public ConnectorAttr getConnector() {
        VariantAttr variant = getVariant();
        if (variant == null || connectorName == null) return null;
        return variant.getPartOutwardConnectors().get(connectorName);
    }

    @Nullable
    public ConnectorAttr getNextConnector() {
        VariantAttr variant = getVariant();
        if (variant == null) return null;
        if (variant.getPartOutwardConnectors().isEmpty()) return null;
        if (connectorIterator == null || !connectorIterator.hasNext())
            this.connectorIterator = variant.getConnectorIterator();
        if (connectorIterator == null) return null;
        this.connectorName = connectorIterator.next();
        return variant.getPartOutwardConnectors().get(connectorName);
    }
}
