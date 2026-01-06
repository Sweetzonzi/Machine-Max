package io.github.sweetzonzi.machine_max.client.render.gui.screen;


import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * 科研配方在 UI 中的展示模型
 * 仅用于 UI 渲染，不包含任何逻辑判断。
 */
public record ResearchState(
        RecipeHolder<FabricatingRecipe> recipe,
        int researchLevel,
        float levelProgress,     // 0.0 ~ 1.0
        int currentRp,
        int requiredRp,
        boolean hasProduct,
        boolean researching,
        boolean canResearch,
        boolean unlocked,
        boolean canReclaim
) {
}