package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.recipe.BlueprintResearchRecipe
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe
import io.github.sweetzonzi.machine_max.common.recipe.ResearchRecipe
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.crafting.RecipeType

object MMResources {
    @JvmStatic
    val FABRICATION_RECIPE_TYPE = MachineMax.REGISTER.recipeType{
        id = "fabricating"
        factory = {
            RecipeType.simple<FabricatingRecipe>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "fabricating"))
        }
    }

    @JvmStatic
    val FABRICATION_RECIPE_SERIALIZER = MachineMax.REGISTER.recipeSerializer{
        id = "fabricating"
        factory = {
            FabricatingRecipe.Serializer.INSTANCE
        }
    }

    @JvmStatic
    val RESEARCH_RECIPE_TYPE = MachineMax.REGISTER.recipeType{
        id = "research"
        factory = {
            RecipeType.simple<ResearchRecipe>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "research"))
        }
    }

    @JvmStatic
    val RESEARCH_RECIPE_SERIALIZER = MachineMax.REGISTER.recipeSerializer{
        id = "research"
        factory = {
            ResearchRecipe.Serializer.INSTANCE
        }
    }

    @JvmStatic
    val BLUEPRINT_RESEARCH_RECIPE_TYPE = MachineMax.REGISTER.recipeType{
        id = "blueprint_research"
        factory = {
            RecipeType.simple<BlueprintResearchRecipe>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "blueprint_research"))
        }
    }

    @JvmStatic
    val BLUEPRINT_RESEARCH_RECIPE_SERIALIZER = MachineMax.REGISTER.recipeSerializer{
        id = "blueprint_research"
        factory = {
            BlueprintResearchRecipe.Serializer.INSTANCE
        }
    }

    @JvmStatic
    fun register() {}
}
