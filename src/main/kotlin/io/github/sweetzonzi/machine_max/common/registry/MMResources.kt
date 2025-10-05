package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe

object MMResources {
    @JvmStatic
    val FABRICATE_RECIPE_TYPE = MachineMax.REGISTER.recipe<FabricatingRecipe>()
        .id("fabricating")
        .serializer(FabricatingRecipe.Serializer::INSTANCE)
        .build()

    @JvmStatic
    fun register() {}
}