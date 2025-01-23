package io.github.tt432.machinemax.common.item.prop;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.component.PartTypeComponent;
import io.github.tt432.machinemax.common.entity.MMPartEntity;
import io.github.tt432.machinemax.common.part.slot.AbstractBodySlot;
import io.github.tt432.machinemax.common.registry.MMAttachments;
import io.github.tt432.machinemax.common.registry.MMDataComponents;
import io.github.tt432.machinemax.common.registry.PartType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class MMPartItem extends Item {
    public MMPartItem(Properties properties) {
        super(properties);
        properties.stacksTo(1);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (!level.isClientSide()) {
            PartType partType;
            //从物品Component中获取部件类型
            PartTypeComponent partTypeComponent = player.getItemInHand(usedHand).get(MMDataComponents.getPART_TYPE());
            //如果物品Component中部件类型为空，则使用默认的部件类型
            if (partTypeComponent != null && partTypeComponent.partType() != null)
                partType = partTypeComponent.partType();
            else partType = PartType.TEST_CUBE_PART.get();

            var attachedBody = player.getData(MMAttachments.getENTITY_EYESIGHT());
            AbstractBodySlot slot = attachedBody.getSlot();
            if (slot != null) {
                MMPartEntity partEntity = new MMPartEntity(partType, level, slot);
                level.addFreshEntity(partEntity);
            }

            MachineMax.LOGGER.info(player + " tried to place a part.");
        }
        return super.use(level, player, usedHand);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide() && isSelected) {
            var attachedBody = entity.getData(MMAttachments.getENTITY_EYESIGHT());
            AbstractBodySlot slot = attachedBody.getSlot();
            if (slot != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.empty().append("部件槽:" + slot.getName()), true);
            } else {
                Minecraft.getInstance().player.displayClientMessage(Component.empty().append("未选中可用的部件槽"), true);
            }
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
     * @param stack
     * @return 翻译键
     */
    @Override
    public Component getName(ItemStack stack) {
        PartType partType;
        //从物品Component中获取部件类型
        PartTypeComponent partTypeComponent = stack.get(MMDataComponents.getPART_TYPE());
        //如果物品Component中部件类型为空，则使用默认的部件类型
        if (partTypeComponent != null && partTypeComponent.partType() != null) partType = partTypeComponent.partType();
        else partType = PartType.TEST_CUBE_PART.get();
        return Component.translatable(partType.getRegistryKey().toLanguageKey());
    }
}
