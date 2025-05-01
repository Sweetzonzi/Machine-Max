package io.github.tt432.machinemax.common.item.prop;

import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.component.PartAssemblyCacheComponent;
import io.github.tt432.machinemax.common.component.PartAssemblyInfoComponent;
import io.github.tt432.machinemax.common.registry.MMAttachments;
import io.github.tt432.machinemax.common.registry.MMDataComponents;
import io.github.tt432.machinemax.common.registry.MMRegistries;
import io.github.tt432.machinemax.common.registry.MMVisualEffects;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.PartType;
import io.github.tt432.machinemax.common.vehicle.VehicleCore;
import io.github.tt432.machinemax.common.vehicle.VehicleManager;
import io.github.tt432.machinemax.common.vehicle.attr.ConnectorAttr;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.tt432.machinemax.common.vehicle.connector.AttachPointConnector;
import io.github.tt432.machinemax.external.MMDynamicRes;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public class MMPartItem extends Item {
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
        if (!level.isClientSide()) {
            ItemStack stack = player.getItemInHand(usedHand);
            PartType partType = getPartType(stack, level);//获取物品保存的部件类型
            PartAssemblyInfoComponent info = getPartAssemblyInfo(stack, level);//获取物品保存的组装信息
            String variant = info.variant();//获取物品保存的部件变体
            String connectorName = info.connector();//获取物品保存的部件接口
            String connectorType = info.connectorType();//获取物品保存的部件接口类型
            var eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
            AbstractConnector targetConnector = eyesight.getConnector();
            if (targetConnector != null) {//若有可用的接口
                if (targetConnector.conditionCheck(variant)) {//检查变体条件
                    //TODO:检查connectorType，骑乘姿态拆卸零件后这一内容会变null
                    if ((targetConnector instanceof AttachPointConnector || connectorType.equals("AttachPoint"))) {//检查接口条件
                        VehicleCore vehicleCore = targetConnector.subPart.part.vehicle;//获取目标对接口所属的载具
                        Part part = new Part(partType, variant, level);
                        targetConnector.adjustTransform(part, part.externalConnectors.get(connectorName));
                        vehicleCore.attachConnector(targetConnector, part.externalConnectors.get(connectorName), part);//尝试将新部件连接至接口
                    }
                }
            } else {
                Part part = new Part(partType, variant, level);
                part.setTransform(new Transform(PhysicsHelperKt.toBVector3f(player.getPosition(1)), Quaternion.IDENTITY));
                VehicleManager.addVehicle(new VehicleCore(level, part));//否则直接放置零件
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
                        if (MMVisualEffects.getPART_ASSEMBLY().partToAssembly != null) {
                            MMVisualEffects.getPART_ASSEMBLY().partToAssembly.setTransform(
                                    targetConnector.mergeTransform(new Transform(
                                            PhysicsHelperKt.toBVector3f(info.offset()),
                                            SparkMathKt.toBQuaternion(info.rotation())
                                    ).invert())
                            );
                        }
                    } else message.append("无法连接两个非AttachPoint接口");
                } else
                    message = Component.empty().append(" 连接口" + targetConnector.name + "不接受部件" + partType.name + "的" + variant + "变体");
            } else {
                message.append("未选中可用的部件接口，右键将直接放置零件");
                if (MMVisualEffects.getPART_ASSEMBLY().partToAssembly != null)
                    MMVisualEffects.getPART_ASSEMBLY().partToAssembly.setTransform(
                            new Transform(
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
     * 根据物品Component中的部件类型修改物品显示的名称
     *
     * @param stack 物品堆
     * @return 翻译键
     */
    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        String partName = stack.get(MMDataComponents.getPART_NAME());
        return Component.translatable(MachineMax.MOD_ID + ".item." + partName);
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
            var locators = modelIndex.getModel().getLocators();
            OLocator partConnectorLocator = locators.get(connectorAttr.locatorName());
            Vector3f offset = partConnectorLocator.getOffset().toVector3f();
            Vector3f rotation = partConnectorLocator.getRotation().toVector3f();
            Quaternionf quaternion = new Quaternionf().rotationZYX(rotation.x, rotation.y, rotation.z);
            PartAssemblyInfoComponent info = new PartAssemblyInfoComponent(variant, connectorName, connectorAttr.type(), offset, quaternion);
            stack.set(MMDataComponents.getPART_ASSEMBLY_INFO(), info);//将组装信息存入物品，并自动同步至客户端
            return info;
        } else return stack.get(MMDataComponents.getPART_ASSEMBLY_INFO());
    }

    public static PartType getPartType(ItemStack stack, Level level) {
        PartType partType = null;
        if (stack.has(MMDataComponents.getPART_TYPE())) {
            //从物品Component中获取部件类型
            partType = MMRegistries.getRegistryAccess(level).registry(PartType.PART_REGISTRY_KEY).get().get(stack.get(MMDataComponents.getPART_TYPE()));
            if (partType == null) partType = MMDynamicRes.PART_TYPES.get(stack.get(MMDataComponents.getPART_TYPE()));//为null说明是外部包 尝试还原
        } else throw new IllegalStateException("物品" + stack + "中未找到部件类型数据");//如果物品Component中部件类型为空，则抛出异常
        return partType;
    }

}
