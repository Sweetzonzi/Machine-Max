package io.github.sweetzonzi.machine_max.client.render.gui.screen;

import io.github.sweetzonzi.machine_max.common.recipe.ResearchRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

/**
 * 科研配方在 UI 中的展示模型
 * 仅用于 UI 渲染，不包含任何逻辑判断。
 */
public record ResearchState(
        RecipeHolder<ResearchRecipe> recipe,
        boolean completed,
        boolean unlockable,
        boolean canComplete,
        int currentFreeRp,
        int requiredRp,
        int missingPrerequisites,
        boolean blueprintResearch,
        @Nullable ResourceLocation unlockedFabricatingRecipe,
        ItemStack previewItem,
        boolean hasProduct,
        boolean canReclaim
) {
}
