package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.ICustomModelItem;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.awt.*;
import java.util.HashMap;
import java.util.Objects;

public class FabicatingBlueprintItem extends Item implements ICustomModelItem, PartAssemblyItem {
    public FabicatingBlueprintItem() {
        super(new Properties());
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
            if (player.hasData(MMAttachments.getVEHICLE_ASSEMBLY())) {
                return player.getData(MMAttachments.getVEHICLE_ASSEMBLY()).assembly(level, player, stack);
            } else return InteractionResultHolder.pass(stack);
        } else return InteractionResultHolder.success(stack);
    }

    /**
     * 根据物品Component中的部件类型修改物品显示的名称
     *
     * @param stack 物品堆
     * @return 翻译键
     */
    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        try {
            ResourceLocation type = stack.get(MMDataComponents.getRECIPE_TYPE());
            return Component.translatable(type.toLanguageKey()).append(Component.translatable("item.machine_max.fabricating_blueprint"));
        } catch (Exception e) {
            return super.getName(stack);
        }
    }


    public ItemAnimatable createItemAnimatable(ItemStack itemStack, Level level, ItemDisplayContext context) {
        var animatable = new ItemAnimatable(itemStack, level);
        PartType partType = PartAssemblyItem.getPartType(itemStack, level);//获取物品保存的部件类型
        HashMap<ItemDisplayContext, ItemAnimatable> customModels;
        if (itemStack.has(MMDataComponents.getCUSTOM_ITEM_MODEL()) && !Objects.requireNonNull(itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL())).isEmpty())
            customModels = itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL());
        else customModels = new HashMap<>();
        if (context == ItemDisplayContext.GUI && partType != null) {
            animatable.getModelController().setModel(new ModelIndex(
                    "item", ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item_icon_2d_128x")));
            animatable.getModelController().setTextureLocation(partType.getDefaultIcon());
        } else {
            animatable.getModelController().setModel(new ModelIndex(
                    "item", ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "blueprint")));
            animatable.getModelController().setTextureLocation(
                    ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/item/blueprint.png"));
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

    @Override
    public Color getColor(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        return Color.blue;
    }
}
