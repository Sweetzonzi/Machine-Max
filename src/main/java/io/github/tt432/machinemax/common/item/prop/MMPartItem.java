package io.github.tt432.machinemax.common.item.prop;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.component.PartItemComponent;
import io.github.tt432.machinemax.common.component.PartPortIteratorComponent;
import io.github.tt432.machinemax.common.component.PartTypeComponent;
import io.github.tt432.machinemax.common.entity.CoreEntity;
import io.github.tt432.machinemax.common.entity.MMPartEntity;
import io.github.tt432.machinemax.common.vehicle.AbstractPart;
import io.github.tt432.machinemax.common.vehicle.PartType;
import io.github.tt432.machinemax.common.vehicle.port.AbstractPortPort;
import io.github.tt432.machinemax.common.registry.MMAttachments;
import io.github.tt432.machinemax.common.registry.MMDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MMPartItem extends Item {
    public MMPartItem(Properties properties) {
        super(properties);
        properties.stacksTo(1);
    }

//    @Override
//    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
//        if (!level.isClientSide()) {
//            PartType partType = getPartType(player.getItemInHand(usedHand));
//            var eyesightBody = player.getData(MMAttachments.getENTITY_EYESIGHT());
//            AbstractPortPort targetPort = eyesightBody.getPort();
//            if (targetPort != null) {
//                MMPartEntity partEntity = new MMPartEntity(partType, level, targetPort, getSelectedPort(player.getItemInHand(usedHand), level).getNameA());
//                level.addFreshEntity(partEntity);
//            } else {
//                CoreEntity coreEntity = new CoreEntity(partType, level);
//                level.addFreshEntity(coreEntity);
//            }
//        }
//        return super.use(level, player, usedHand);
//    }

//    @Override
//    public void inventoryTick(ItemStack stack, Level level, Entity entity, int portId, boolean isSelected) {
//        super.inventoryTick(stack, level, entity, portId, isSelected);
//        AbstractPortPort selectedPort = getSelectedPort(stack, level);
//        if (selectedPort == null) selectedPort = getNextPort(stack, level);
//        if (level.isClientSide() && isSelected) {
//            var eyesightBody = entity.getData(MMAttachments.getENTITY_EYESIGHT());
//            AbstractPortPort targetPort = eyesightBody.getPort();
//            if (targetPort != null && selectedPort != null) {
//                Minecraft.getInstance().player.displayClientMessage(Component.empty().append("部件接口:" + targetPort.getNameA() + "选择接口:" + selectedPort.getNameA()), true);
//            } else {
//                Minecraft.getInstance().player.displayClientMessage(Component.empty().append("未选中可用的部件接口，右键将直接放置零件"), true);
//            }
//        }
//    }

    //TODO:之后用这个改Tooltip，为零件添加各类详细信息
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    /**
     * 根据物品Component中的部件类型修改物品显示的名称
     *
     * @param stack
     * @return 翻译键
     */
//    @Override
//    public @NotNull Component getNameA(ItemStack stack) {
//        PartType partType;
//        //从物品Component中获取部件类型
//        PartTypeComponent partTypeComponent = stack.get(MMDataComponents.getPART_TYPE());
//        //如果物品Component中部件类型为空，则使用默认的部件类型
//        if (partTypeComponent != null && partTypeComponent.partType() != null) partType = partTypeComponent.partType();
//        else
//            partType = PartType.PART_REGISTRY.get(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "test_cube_part"));
//        return Component.translatable(PartType.PART_REGISTRY.getKey(partType).toLanguageKey());
//    }
/*
    public static PartType getPartType(ItemStack stack) {
        PartType partType;
        //从物品Component中获取部件类型
        PartTypeComponent partTypeComponent = stack.get(MMDataComponents.getPART_TYPE());
        //如果物品Component中部件类型为空，则使用默认的部件类型
        if (partTypeComponent != null && partTypeComponent.partType() != null)
            partType = partTypeComponent.partType();
        else
            partType = PartType.PART_TYPE.getRegistry().get().get(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "test_cube_part"));
        return partType;
    }

    public static AbstractPart getPart(ItemStack stack, Level level) {
        PartType partType = getPartType(stack);
        AbstractPart part;
        //TODO:变体处理(例如左右侧轮胎)
        //TODO:GC处理，例如离开世界时清除部件
        //从物品Component中获取部件
        PartItemComponent partItemComponent = stack.get(MMDataComponents.getPART());
        if (partItemComponent == null) {//如果物品Component为空，则创建新的Component和部件
            part = partType.createPart(level);
            part.rootBody.getBody().setPosition(0, -256, 0);
            stack.set(MMDataComponents.getPART().get(), new PartItemComponent(part));
        } else {
            if (partItemComponent.part() == null) {
                part = partType.createPart(level);
                stack.set(MMDataComponents.getPART().get(), new PartItemComponent(part));
            } else {
                part = partItemComponent.part();
            }
        }
        return part;
    }

    public static PartPortIteratorComponent getPortIteratorComponent(ItemStack stack, Level level) {
        AbstractPart part = getPart(stack, level);
        PartPortIteratorComponent iteratorComponent = stack.get(MMDataComponents.getPART_PORT_ITERATOR());
        if (iteratorComponent == null || iteratorComponent.getIterator() == null) {
            Iterator<Map.Entry<String, AbstractPortPort>> iterator = part.getBodyPorts().entrySet().iterator();
            iteratorComponent = new PartPortIteratorComponent(iterator);
            stack.set(MMDataComponents.getPART_PORT_ITERATOR().get(), iteratorComponent);
        }
        return iteratorComponent;
    }

    public static AbstractPortPort getSelectedPort(ItemStack stack, Level level) {
        PartPortIteratorComponent iteratorComponent = getPortIteratorComponent(stack, level);
        return iteratorComponent.getCurrentPort();
    }

    public static AbstractPortPort getNextPort(ItemStack stack, Level level) {
        AbstractPart part = getPart(stack, level);
        PartPortIteratorComponent iteratorComponent = getPortIteratorComponent(stack, level);
        AbstractPortPort port = iteratorComponent.getNextPort();
        if (port == null) {
            // 如果迭代器已经结束，重新开始
            Iterator<Map.Entry<String, AbstractPortPort>> newIterator = part.getBodyPorts().entrySet().iterator();
            iteratorComponent.setIterator(newIterator);
            port = iteratorComponent.getNextPort();
        }
        return port;
    }

 */
}
