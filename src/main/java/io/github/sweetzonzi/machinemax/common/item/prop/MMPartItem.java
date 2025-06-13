package io.github.sweetzonzi.machinemax.common.item.prop;

import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.client.renderer.VisualEffectHelper;
import io.github.sweetzonzi.machinemax.common.item.ICustomModelItem;
import io.github.sweetzonzi.machinemax.common.component.PartAssemblyCacheComponent;
import io.github.sweetzonzi.machinemax.common.component.PartAssemblyInfoComponent;
import io.github.sweetzonzi.machinemax.common.registry.MMAttachments;
import io.github.sweetzonzi.machinemax.common.registry.MMDataComponents;
import io.github.sweetzonzi.machinemax.common.registry.MMRegistries;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.PartType;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleManager;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.ConnectorAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.AttachPointConnector;
import io.github.sweetzonzi.machinemax.external.MMDynamicRes;
import io.github.sweetzonzi.machinemax.network.payload.RegularInputPayload;
import io.github.sweetzonzi.machinemax.util.data.KeyInputMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;

public class MMPartItem extends Item implements ICustomModelItem {
    public MMPartItem(Properties properties) {
        super(properties);
        properties.stacksTo(1);
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
                PartAssemblyInfoComponent info = getPartAssemblyInfo(stack, level);//获取物品保存的组装信息
                String variant = info.variant();//获取物品保存的部件变体
                String connectorName = info.connector();//获取物品保存的部件接口
                String connectorType = info.connectorType();//获取物品保存的部件接口类型
                var eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
                AbstractConnector targetConnector = eyesight.getConnector();
                int damage = stack.getDamageValue();
                float durability = Math.clamp(partType.basicDurability - damage + 1, 1f, partType.basicDurability);
                if (targetConnector != null) {//若有可用的接口
                    if (targetConnector.conditionCheck(variant)) {//检查变体条件
                        //TODO:检查connectorType，骑乘姿态拆卸零件后这一内容会变null
                        if ((targetConnector instanceof AttachPointConnector || connectorType.equals("AttachPoint"))) {//检查接口条件
                            VehicleCore vehicleCore = targetConnector.subPart.part.vehicle;//获取目标对接口所属的载具
                            Part part = new Part(partType, variant, level);
                            part.durability = durability;
                            targetConnector.adjustTransform(part, part.externalConnectors.get(connectorName));
                            vehicleCore.attachConnector(targetConnector, part.externalConnectors.get(connectorName), part);//尝试将新部件连接至接口
                        }
                    }
                } else {
                    Part part = new Part(partType, variant, level);
                    part.durability = durability;
                    part.setTransform(
                            new Transform(
                                    PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                                            player.getEyePosition(),
                                            player.getEyePosition().add(player.getViewVector(1).scale(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation()),
                                    Quaternion.IDENTITY
                            )
                    );
                    VehicleManager.addVehicle(new VehicleCore(level, part));//否则直接放置零件
                }
            } catch (Exception e) {
                MachineMax.LOGGER.error("Error while using part item: {}", stack.getDisplayName(), e);
                player.sendSystemMessage(Component.translatable("error.machine_max.use_part_item", stack.getDisplayName(), e));
            }
        }
        return super.use(level, player, usedHand);
    }


    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int portId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, portId, isSelected);
        if (level.isClientSide() && isSelected) {
            PartType partType = getPartType(stack, level);//获取物品保存的部件类型
            PartAssemblyInfoComponent info = getPartAssemblyInfo(stack, level);//获取物品保存的组装信息
            String variant = info.variant();//获取物品保存的部件变体
            String connectorName = info.connector();//获取物品保存的部件接口
            String connectorType = info.connectorType();//获取物品保存的部件接口类型
            var eyesight = entity.getData(MMAttachments.getENTITY_EYESIGHT());
            AbstractConnector targetConnector = eyesight.getConnector();
            MutableComponent message = Component.empty();
            if (targetConnector != null) {
                if (targetConnector.conditionCheck(variant)) {
                    if ((targetConnector instanceof AttachPointConnector || connectorType.equals("AttachPoint"))) {
                        message.append("目标接口:" + targetConnector.name + "部件接口:" + connectorName);
                        if (!variant.equals("default") && partType.variants.size() > 1)
                            message.append(" 部件变体类型:" + variant);
                        if (VisualEffectHelper.partToAssembly != null) {
                            VisualEffectHelper.partToAssembly.setTransform(
                                    targetConnector.mergeTransform(new Transform(
                                            PhysicsHelperKt.toBVector3f(info.offset()),
                                            SparkMathKt.toBQuaternion(info.rotation())
                                    ).invert())
                            );
                        }
                    } else message.append("无法连接两个非AttachPoint接口");
                } else {
                    for (String variantName : partType.variants.keySet()) {
                        if (targetConnector.conditionCheck(variantName)) {
                            PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.CYCLE_PART_VARIANTS.getValue(), 0));
                            return;
                        }
                    }
                    message = Component.empty().append(" 连接口" + targetConnector.name + "不接受部件" + partType.name + "的" + variant + "变体");
                }
            } else {
                message.append("未选中可用的部件接口，右键将直接放置零件");
                if (VisualEffectHelper.partToAssembly != null)
                    VisualEffectHelper.partToAssembly.setTransform(
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
            Minecraft.getInstance().player.displayClientMessage(message, true);
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
            return Component.translatable("item." + type.toLanguageKey());
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
        if (partType != null) {
            return (int) Math.ceil(partType.basicDurability);
        } else return super.getMaxDamage(stack);
    }

    public static PartAssemblyCacheComponent getPartAssemblyCache(ItemStack stack, Level level) {
        if (!stack.has(MMDataComponents.getPART_ASSEMBLY_CACHE())) {
            PartAssemblyCacheComponent cache = new PartAssemblyCacheComponent(getPartType(stack, level));
            stack.set(MMDataComponents.getPART_ASSEMBLY_CACHE(), cache);
            return cache;
        } else return stack.get(MMDataComponents.getPART_ASSEMBLY_CACHE());
    }

    public static PartAssemblyInfoComponent getPartAssemblyInfo(ItemStack stack, Level level) {
        PartType partType = getPartType(stack, level);
        if (!stack.has(MMDataComponents.getPART_ASSEMBLY_INFO())) {//若物品Component中无组装信息，则新建
            PartAssemblyCacheComponent iterators = getPartAssemblyCache(stack, level);//获取物品保存的组装信息
            var connectors = partType.getPartOutwardConnectors();
            String variant = iterators.getNextVariant();
            String connectorName = iterators.getNextConnector();
            ConnectorAttr connectorAttr = connectors.get(connectorName);
            ModelIndex modelIndex = new ModelIndex(partType.variants.get(variant), ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty"));
            if (modelIndex.getModel().getBones().isEmpty())
                throw new IllegalStateException("未找到部件" + partType.name + "的" + variant + "变体的模型:" + modelIndex.getModelPath());
            var locators = modelIndex.getModel().getLocators();
            OLocator partConnectorLocator = locators.get(connectorAttr.locatorName());
            if (partConnectorLocator == null)
                throw new NullPointerException("部件" + partType.name + "的" + variant + "变体缺少" + connectorAttr.locatorName() + "定位器");
            Vector3f offset = partConnectorLocator.getOffset().toVector3f();
            Vector3f rotation = partConnectorLocator.getRotation().toVector3f();
            Quaternionf quaternion = new Quaternionf().rotationZYX(rotation.x, rotation.y, rotation.z);
            PartAssemblyInfoComponent info = new PartAssemblyInfoComponent(variant, connectorName, connectorAttr.type(), offset, quaternion);
            stack.set(MMDataComponents.getPART_ASSEMBLY_INFO(), info);//将组装信息存入物品，并自动同步至客户端
            return info;
        } else return stack.get(MMDataComponents.getPART_ASSEMBLY_INFO());
    }

    public static PartType getPartType(ItemStack stack, Level level) {
        PartType partType;
        if (stack.has(MMDataComponents.getPART_TYPE())) {
            //从物品Component中获取部件类型
            partType = MMRegistries.getRegistryAccess(level).registry(PartType.PART_REGISTRY_KEY).get().get(stack.get(MMDataComponents.getPART_TYPE()));
            if (partType == null) {
                if (level.isClientSide) {
                    partType = MMDynamicRes.PART_TYPES.get(stack.get(MMDataComponents.getPART_TYPE()));//为null说明是外部包 尝试还原
                } else
                    partType = MMDynamicRes.SERVER_PART_TYPES.get(stack.get(MMDataComponents.getPART_TYPE()));
            } else return partType;
        } else throw new IllegalStateException("物品" + stack + "中未找到部件类型数据");//如果物品Component中部件类型为空，则抛出异常
        if (partType == null) throw new IllegalStateException("未找到物品" + stack + "中存储的数据类型");
        return partType;
    }

    public ItemAnimatable createItemAnimatable(ItemStack itemStack, Level level, ItemDisplayContext context) {
        var animatable = new ItemAnimatable(itemStack, level);
        PartType partType = getPartType(itemStack, level);//获取物品保存的部件类型
        String variant = getPartAssemblyInfo(itemStack, level).variant();
        HashMap<ItemDisplayContext, ItemAnimatable> customModels;
        if (itemStack.has(MMDataComponents.getCUSTOM_ITEM_MODEL()))
            customModels = itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL());
        else customModels = new HashMap<>();
        if (((ICustomModelItem) itemStack.getItem()).use2dModel(itemStack, level, context) && context == ItemDisplayContext.GUI) {
            animatable.setModelIndex(
                    new ModelIndex(
                            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item/item_icon_2d_128x.geo"),
                            partType.icon)
            );
        } else {
            animatable.setModelIndex(
                    new ModelIndex(
                            partType.variants.get(variant),
                            partType.textures.getFirst())
            );
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
