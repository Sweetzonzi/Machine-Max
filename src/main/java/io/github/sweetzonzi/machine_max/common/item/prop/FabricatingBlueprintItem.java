package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.ICustomModelItem;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.PartType;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.HashMap;
import java.util.Objects;

/**
 * 通用制造蓝图：生存模式下代表某个通用制造配方的凭证物品。
 *
 * <p>不可放置、不参与装配候选，只有展示语义：显示名取产物名 + 本物品名，模型与图标读
 * {@code machine_max:recipe_type} 指向的配方产物。其作为制造机门禁钥匙的能力尚未实装。</p>
 */
public class FabricatingBlueprintItem extends Item implements ICustomModelItem {
    public static final Color COLOR = new Color(150, 200, 255);

    public FabricatingBlueprintItem() {
        super(new Properties());
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        Component productName = getProductName(stack);
        if (productName == null) return super.getName(stack);
        return productName.copy()
                .append(Component.translatable(getDescriptionId()));
    }

    /**
     * 获取蓝图对应的产物显示名称。
     * <p>优先读零件的 {@code machine_max:part_type} 组件；其次取 {@code machine_max:recipe_type} 指向配方的产物名称。
     * 两者都取不到时返回 {@code null}，由调用方回退到蓝图自身的名称。</p>
     *
     * @param stack 蓝图物品堆
     * @return 产物名称；无法解析时返回 {@code null}
     */
    @Nullable
    protected static Component getProductName(ItemStack stack) {
        ResourceLocation partTypeId = stack.get(MMDataComponents.getPART_TYPE());
        if (partTypeId != null) return Component.translatable(partTypeId.toLanguageKey());
        ResourceLocation recipeId = stack.get(MMDataComponents.getRECIPE_TYPE());
        if (recipeId != null) {
            RecipeHolder<FabricatingRecipe> recipeHolder = MMDynamicRes.ALL_FABRICATING_RECIPES.get(recipeId);
            if (recipeHolder != null) return recipeHolder.value().getResult().getHoverName();
        }
        return null;
    }

    /**
     * 构造蓝图的物品动画体：GUI 中按 {@code machine_max:part_type} 显示零件图标，其余场景显示蓝图模型。
     *
     * @param itemStack 物品堆
     * @param level     世界
     * @param context   渲染场景
     * @return 物品动画体
     */
    public IAnimatable<?> createItemAnimatable(ItemStack itemStack, Level level, ItemDisplayContext context) {
        var animatable = new ItemAnimatable(itemStack, level);
        ResourceLocation partTypeId = itemStack.get(MMDataComponents.getPART_TYPE());
        PartType partType = partTypeId == null ? null : PartType.get(level, partTypeId);
        HashMap<ItemDisplayContext, IAnimatable<?>> customModels;
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
    public Color getColor(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        return displayContext == ItemDisplayContext.GUI ? COLOR : Color.WHITE;
    }

}
