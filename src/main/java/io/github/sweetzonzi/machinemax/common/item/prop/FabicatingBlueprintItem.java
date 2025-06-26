package io.github.sweetzonzi.machinemax.common.item.prop;

import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.crafting.FabricatingInput;
import io.github.sweetzonzi.machinemax.common.crafting.FabricatingRecipe;
import io.github.sweetzonzi.machinemax.common.item.ICustomModelItem;
import io.github.sweetzonzi.machinemax.common.registry.MMDataComponents;
import io.github.sweetzonzi.machinemax.common.vehicle.PartType;
import io.github.sweetzonzi.machinemax.external.MMDynamicRes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.awt.*;
import java.util.*;

public class FabicatingBlueprintItem extends Item implements ICustomModelItem {
    public FabicatingBlueprintItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        FabricatingInput input = new FabricatingInput(player.getInventory().items, 0, 0, 0);
        FabricatingRecipe recipe = getRecipe(player.getItemInHand(usedHand));
        if (recipe.matches(input, level)) {
            MachineMax.LOGGER.debug("材料充足，可以制造{}", recipe.getResult().toString());
        } else {
            for (var pair : recipe.getMaterials()) {
                Ingredient ingredient = pair.getFirst();
                int requiredCount = pair.getSecond();
                int count = recipe.checkMaterials(input.inputs(), ingredient);
                MachineMax.LOGGER.debug("缺少材料:{}{}/{}个", ingredient.getItems()[0].getDisplayName(), count, requiredCount);
            }
        }
        return super.use(level, player, usedHand);
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
            ResourceLocation type = getRecipe(stack).getResult().id();
            return Component.translatable(type.toLanguageKey()).append(Component.translatable("item.machine_max.fabricating_blueprint"));
        } catch (Exception e) {
            return super.getName(stack);
        }
    }

    public static FabricatingRecipe getRecipe(ItemStack stack) {
        ResourceLocation type = stack.get(MMDataComponents.getRECIPE_TYPE());
        FabricatingRecipe recipe = MMDynamicRes.CRAFTING_RECIPES.get(type);
        if (recipe == null) throw new IllegalStateException("未找到物品" + stack + "中存储的制造配方");
        return recipe;
    }

    public static PartType getPartType(ItemStack stack, Level level) {
        PartType partType;
        if (stack.has(MMDataComponents.getRECIPE_TYPE())) {
            ResourceLocation type = getRecipe(stack).getResult().id();
            //从物品Component中获取部件类型
            if (level.isClientSide) {
                partType = MMDynamicRes.PART_TYPES.get(type);
            } else
                partType = MMDynamicRes.SERVER_PART_TYPES.get(type);
        } else throw new IllegalStateException("物品" + stack + "中未找到部件类型数据");//如果物品Component中部件类型为空，则抛出异常
        if (partType == null) throw new IllegalStateException("未找到物品" + stack + "中存储的数据类型");
        return partType;
    }

    public ItemAnimatable createItemAnimatable(ItemStack itemStack, Level level, ItemDisplayContext context) {
        var animatable = new ItemAnimatable(itemStack, level);
        PartType partType = getPartType(itemStack, level);//获取物品保存的部件类型
        HashMap<ItemDisplayContext, ItemAnimatable> customModels;
        if (itemStack.has(MMDataComponents.getCUSTOM_ITEM_MODEL()))
            customModels = itemStack.get(MMDataComponents.getCUSTOM_ITEM_MODEL());
        else customModels = new HashMap<>();
        if (context == ItemDisplayContext.GUI) {
            animatable.setModelIndex(
                    new ModelIndex(
                            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item/item_icon_2d_128x.geo"),
                            partType.icon)
            );
        } else {
            animatable.setModelIndex(
                    new ModelIndex(
                            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item/blueprint.geo"),
                            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/item/blueprint.png"))
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

    @Override
    public Color getColor(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        return Color.blue;
    }
}
