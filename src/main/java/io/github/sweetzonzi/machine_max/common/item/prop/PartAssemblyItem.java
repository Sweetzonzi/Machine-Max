package io.github.sweetzonzi.machine_max.common.item.prop;

import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.PartFabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.PartType;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 可放置为零件并参与装配的物品（零件物品与零件制造蓝图）。
 *
 * <p>配方解析一律走本侧零件配方索引，不查 {@code RecipeManager}：索引由装配侧与配方装载期共同维护，
 * 是「零件 ↔ 零件配方」的唯一权威来源。</p>
 */
public interface PartAssemblyItem {
    /**
     * 解析物品代表的零件类型：优先读 {@code machine_max:part_type} 组件，缺失时经零件配方索引反查产物。
     *
     * @param stack 物品堆
     * @param level 用于判定逻辑侧
     * @return 零件类型；无法解析时返回 {@code null}
     */
    @Nullable
    static PartType getPartType(ItemStack stack, Level level) {
        ResourceLocation partTypeId = stack.get(MMDataComponents.getPART_TYPE());
        if (partTypeId != null) {
            PartType partType = PartType.get(level, partTypeId);
            if (partType != null) return partType;
        }
        RecipeHolder<PartFabricatingRecipe> holder = getRecipeHolder(stack, level);
        if (holder == null) return null;
        ResourceLocation resolved = holder.value().getPartType();
        return resolved == null ? null : PartType.get(level, resolved);
    }

    /**
     * 按物品携带的组件查本侧零件配方索引。
     *
     * @param stack 物品堆
     * @param level 用于判定逻辑侧
     * @return 该物品对应的零件配方；未命中索引时返回 {@code null}
     */
    @Nullable
    static RecipeHolder<PartFabricatingRecipe> getRecipeHolder(ItemStack stack, Level level) {
        ResourceLocation partTypeId = stack.get(MMDataComponents.getPART_TYPE());
        if (partTypeId != null) {
            RecipeHolder<PartFabricatingRecipe> holder = MMDynamicRes.getPartRecipe(level, partTypeId);
            if (holder != null) return holder;
        }
        ResourceLocation recipeId = stack.get(MMDataComponents.getRECIPE_TYPE());
        if (recipeId == null) return null;
        RecipeHolder<FabricatingRecipe> holder = MMDynamicRes.getAllFabricating(level).get(recipeId);
        if (holder != null && holder.value() instanceof PartFabricatingRecipe partRecipe) {
            return new RecipeHolder<>(holder.id(), partRecipe);
        }
        return null;
    }

    default void appendPartTags(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        PartType partType = TextUtil.resolvePartTypeForTooltip(stack);
        if (partType == null) return;
        List<ResourceLocation> tags = TextUtil.getDistinctSortedTags(partType);
        if (tags.isEmpty()) return;
        for (ResourceLocation tag : tags) {
            String display = TextUtil.getTranslatedOrRawTag(tag);
            tooltipComponents.add(Component.literal(" - " + display).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
