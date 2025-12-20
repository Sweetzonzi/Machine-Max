package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.sound.SpreadingSoundHelper;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.component.PartAssemblyCacheComponent;
import io.github.sweetzonzi.machine_max.common.component.PartAssemblyInfoComponent;
import io.github.sweetzonzi.machine_max.common.item.ICustomModelItem;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.vehicle.ObjectManager;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AttachPointConnector;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.network.payload.RegularInputPayload;
import io.github.sweetzonzi.machine_max.util.data.KeyInputMapping;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class PartItem extends Item implements ICustomModelItem {
    public PartItem() {
        super(new Properties().stacksTo(1).durability(100));
    }

    /**
     * 右键点击物品，尝试将零件放置到世界中或尝试与选择的连接口连接
     *
     * @param level    世界
     * @param player   玩家
     * @param usedHand 玩家使用的手
     * @return 互动结果
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        stack.set(DataComponents.MAX_DAMAGE, getMaxDamage(stack));
        if (!level.isClientSide()) {
            try {
                PartType partType = getPartType(stack, level);//获取物品保存的部件类型
                if (partType == null) return InteractionResultHolder.pass(stack);
                PartAssemblyInfoComponent info = getPartAssemblyInfo(stack, partType);//获取物品保存的组装信息
                String variant = info.getVariant();//获取物品保存的部件变体
                var subpart_connector = info.getConnector();//获取物品保存的部件接口
                String connectorType = info.getConnectorType();//获取物品保存的部件接口类型
                var eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
                AbstractConnector targetConnector = eyesight.getConnector();
                if (targetConnector != null && subpart_connector!= null) {//若有可用的接口
                    if (targetConnector.conditionCheck(partType, variant)) {//检查变体条件
                        //TODO:检查connectorType，骑乘姿态拆卸零件后这一内容会变null
                        if ((targetConnector instanceof AttachPointConnector || connectorType.equals("AttachPoint"))) {//检查接口条件
                            VehicleCore vehicleCore = targetConnector.subPart.part.vehicle;//获取目标对接口所属的载具
                            Part part = new Part(partType, variant, level);
                            targetConnector.adjustTransform(part, part.externalConnectors.get(subpart_connector));
                            vehicleCore.attachConnector(targetConnector, part.externalConnectors.get(subpart_connector), part);//尝试将新部件连接至接口
                            if (!player.hasInfiniteMaterials()) VisualEffectHelper.partToPlace = null;
                            var pos = part.rootSubPart.getPosition();
                            stack.consume(1, player);
                            SoundEvent sound = SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item.part.placed"), 32f);
                            SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.PLAYERS, player.getPosition(1), player.getDeltaMovement().scale(20), (float) (1f + 0.2f * (Math.random() - 0.5f)), 1.0f);
                            ((ServerLevel) level).sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 10, 1, 1, 1, 0.2f);
                            return InteractionResultHolder.consume(stack);
                        } else return InteractionResultHolder.pass(stack);
                    } else return InteractionResultHolder.pass(stack);
                } else {
                    Part part = new Part(partType, variant, level);
                    Transform transform = new Transform(
                            PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                                    player.getEyePosition(),
                                    player.getEyePosition().add(player.getViewVector(1).scale(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation()),
                            Quaternion.IDENTITY
                    );
                    part.setTransform(transform);
                    var pos = transform.getTranslation();
                    ObjectManager.addVehicle(new VehicleCore(level, part));//否则直接放置零件
                    stack.consume(1, player);
                    SoundEvent sound = SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item.part.placed"), 32f);
                    SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.PLAYERS, player.getPosition(1), player.getDeltaMovement().scale(20), (float) (1f + 0.2f * (Math.random() - 0.5f)), 1.0f);
                    ((ServerLevel) level).sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 10, 1, 1, 1, 0.01);
                    return InteractionResultHolder.consume(stack);
                }
            } catch (Exception e) {
                MachineMax.LOGGER.error("Error while using part item: {}", stack.getDisplayName(), e);
                player.sendSystemMessage(Component.translatable("error.machine_max.use_part_item", stack.getDisplayName(), e));
                return InteractionResultHolder.fail(stack);
            }
        } else return InteractionResultHolder.success(stack);
    }


    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int portId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, portId, isSelected);
        if (level.isClientSide() && isSelected) {
            try {
                PartType partType = getPartType(stack, level);//获取物品保存的部件类型
                PartAssemblyInfoComponent info = getPartAssemblyInfo(stack, partType);//获取物品保存的组装信息
                String variant = info.getVariant();//获取物品保存的部件变体
                var subpart_connector = info.getConnector();//获取物品保存的部件接口
                String connectorType = info.getConnectorType();//获取物品保存的部件接口类型
                var eyesight = entity.getData(MMAttachments.getENTITY_EYESIGHT());
                AbstractConnector targetConnector = eyesight.getConnector();
                MutableComponent message = Component.empty();
                if (targetConnector != null) {
                    if (targetConnector.conditionCheck(partType, variant)) {
                        if ((targetConnector instanceof AttachPointConnector || connectorType.equals("AttachPoint"))) {
                            message.append("目标接口:" + targetConnector.name + "部件接口:" + subpart_connector);
                            if (!variant.equals("default") && partType.variants.size() > 1)
                                message.append(" 部件变体类型:" + variant);
                            if (VisualEffectHelper.partToPlace != null) {
                                VisualEffectHelper.partToPlace.setTransform(
                                        targetConnector.mergeTransform(new Transform(
                                                PhysicsHelperKt.toBVector3f(info.getOffset()),
                                                SparkMathKt.toBQuaternion(info.getRotation())
                                        ).invert())
                                );
                            }
                        } else message.append("无法连接两个非AttachPoint接口");
                    } else {
                        for (String variantName : partType.variants.keySet()) {
                            if (targetConnector.conditionCheck(partType, variantName)) {
                                PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.CYCLE_PART_VARIANTS.getValue(), 0));
                                return;
                            }
                        }
                        message = Component.empty().append(" 连接口" + targetConnector.name + "不接受部件" + partType.getRegistryKey() + "的" + variant + "变体");
                    }
                } else {
                    message.append("未选中可用的部件接口，右键将直接放置零件");
                    if (VisualEffectHelper.partToPlace != null)
                        VisualEffectHelper.partToPlace.setTransform(
                                entity instanceof LivingEntity livingEntity ?
                                        new Transform(
                                                PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                                                        entity.getEyePosition(),
                                                        entity.getEyePosition().add(entity.getViewVector(1).scale(livingEntity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                                                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getLocation()),
                                                Quaternion.IDENTITY
                                        ) : new Transform(
                                        PhysicsHelperKt.toBVector3f(entity.position()),
                                        Quaternion.IDENTITY
                                )

                        );
                }
                if (entity instanceof Player player)
                    player.displayClientMessage(message, true);
            } catch (NullPointerException e) {
                if (entity.tickCount % 100 == 0)
                    MachineMax.LOGGER.error("Invalid data: {}", stack.getDisplayName(), e);
            }
        }
    }

    //TODO:之后用这个改Tooltip，为零件添加各类详细信息
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    /**
     * 根据物品Component中的蓝图修改物品显示的名称
     *
     * @param stack 物品堆
     * @return 翻译键
     */
    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        ResourceLocation type = stack.get(MMDataComponents.getPART_TYPE());
        if (type != null) {
            return Component.translatable(type.toLanguageKey());
        } else return super.getName(stack);
    }

    @Override
    public boolean isDamageable(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public int getMaxDamage(@NotNull ItemStack stack) {
        ResourceLocation type = stack.get(MMDataComponents.getPART_TYPE());
        PartType partType = MMDynamicRes.PART_TYPES.get(type);
//        if (partType != null) {
//            return (int) Math.ceil(partType.basicDurability);
//        } else return super.getMaxDamage(stack);
        return super.getMaxDamage(stack);
    }

    public static PartAssemblyCacheComponent getPartAssemblyCache(ItemStack stack) {
        if (!stack.has(MMDataComponents.getPART_ASSEMBLY_CACHE())) {
            PartAssemblyCacheComponent cache = new PartAssemblyCacheComponent(stack.get(MMDataComponents.getPART_TYPE()));
            stack.set(MMDataComponents.getPART_ASSEMBLY_CACHE(), cache);
            return cache;
        } else return stack.get(MMDataComponents.getPART_ASSEMBLY_CACHE());
    }

    public static PartAssemblyInfoComponent getPartAssemblyInfo(@NotNull ItemStack stack, @NotNull PartType partType) {
        if (!stack.has(MMDataComponents.getPART_ASSEMBLY_INFO())) {//若物品Component中无组装信息，则新建
            PartAssemblyCacheComponent iterators = getPartAssemblyCache(stack);//获取物品保存的组装信息
            String variant = iterators.getNextVariant();
            VariantAttr variantAttr = partType.getVariant(variant);
            var connectors = variantAttr.getPartOutwardConnectors();
            if (connectors.isEmpty()) {
                PartAssemblyInfoComponent info = new PartAssemblyInfoComponent(variant);
                stack.set(MMDataComponents.getPART_ASSEMBLY_INFO(), info);//将组装信息存入物品，并自动同步至客户端
                return info;
            }
            var subpart_connector = iterators.getNextConnector();
            ConnectorAttr connectorAttr = connectors.get(subpart_connector);
            OModel model = OModel.getOrEmpty(new ModelIndex("part", partType.getVariant(variant).getModel("default")));
            if (model.getBones().isEmpty())
                throw new IllegalStateException("未找到部件" + partType.getRegistryKey() + "的" + variant + "变体的模型:" + partType.variants.get(variant));
            var locators = model.getLocators();
            OLocator partConnectorLocator = locators.get(connectorAttr.locatorName());
            if (partConnectorLocator == null)
                throw new NullPointerException("部件" + partType.getRegistryKey() + "的" + variant + "变体缺少" + connectorAttr.locatorName() + "定位器");
            Vector3f offset = partConnectorLocator.getOffset().toVector3f();
            Vector3f rotation = partConnectorLocator.getRotation().toVector3f();
            Quaternionf quaternion = new Quaternionf().rotationZYX(rotation.x, rotation.y, rotation.z);
            PartAssemblyInfoComponent info = new PartAssemblyInfoComponent(variant, subpart_connector, connectorAttr.type(), offset, quaternion);
            stack.set(MMDataComponents.getPART_ASSEMBLY_INFO(), info);//将组装信息存入物品，并自动同步至客户端
            return info;
        } else return stack.get(MMDataComponents.getPART_ASSEMBLY_INFO());
    }

    @Nullable
    public static PartType getPartType(ItemStack stack, Level level) {
        PartType partType;
        if (stack.has(MMDataComponents.getPART_TYPE())) {
            if (level.isClientSide) {
                partType = MMDynamicRes.PART_TYPES.get(stack.get(MMDataComponents.getPART_TYPE()));
            } else
                partType = MMDynamicRes.SERVER_PART_TYPES.get(stack.get(MMDataComponents.getPART_TYPE()));
        } else return null;
        return partType;
    }

    public ItemAnimatable createItemAnimatable(ItemStack itemStack, Level level, ItemDisplayContext context) {
        var animatable = new ItemAnimatable(itemStack, level);
        PartType partType = getPartType(itemStack, level);//获取物品保存的部件类型
        if (partType == null) return animatable;
        String variant = getPartAssemblyInfo(itemStack, partType).getVariant();
        HashMap<ItemDisplayContext, ItemAnimatable> customModels;
        if (itemStack.has(MMDataComponents.getCUSTOM_ITEM_MODEL()) && !Objects.requireNonNull(itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL())).isEmpty())
            customModels = itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL());
        else customModels = new HashMap<>();
        if (context == ItemDisplayContext.GUI) {
            animatable.getModelController().setModel(new ModelIndex(
                    "item", ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item_icon_2d_128x")));
            animatable.getModelController().setTextureLocation(partType.getDefaultIcon());
        } else {
            animatable.getModelController().setModel(new ModelIndex("part", partType.getVariant(variant).getModel("default")));
            animatable.getModelController().setTextureLocation(partType.getVariant(variant).getTextures("default").getFirst());
        }
        if (customModels != null) {
            customModels.put(context, animatable);
            itemStack.set(MMDataComponents.getCUSTOM_ITEM_MODEL(), customModels);
        }
        return animatable;
    }

    @Override
    public Vector3f getRenderScale(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        if (displayContext == ItemDisplayContext.GUI) return new Vector3f(1);
        else return new Vector3f(0.3f);
    }
}
