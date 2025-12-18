package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe
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
    fun register() {}
}