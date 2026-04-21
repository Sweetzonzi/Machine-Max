package io.github.sweetzonzi.machine_max.client.compat.jei;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.recipe.BlueprintResearchRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

public final class MMJeiRecipeTypes {
    public static final RecipeType<RecipeHolder<FabricatingRecipe>> FABRICATING =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "fabricating")
            );

    public static final RecipeType<RecipeHolder<BlueprintResearchRecipe>> BLUEPRINT_RESEARCH =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "blueprint_research")
            );

    private MMJeiRecipeTypes() {
    }
}
