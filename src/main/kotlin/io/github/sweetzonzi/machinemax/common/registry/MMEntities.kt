package io.github.sweetzonzi.machinemax.common.registry

import io.github.sweetzonzi.machinemax.MachineMax
import io.github.sweetzonzi.machinemax.common.entity.MMPartEntity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory

object MMEntities {
    @JvmStatic
    fun register() {
    }

    //部件实体
    @JvmStatic
    val PART_ENTITY = MachineMax.REGISTER.entityType<MMPartEntity>().id("part")
        .builder(EntityType.Builder.of(::MMPartEntity, MobCategory.MISC).fireImmune().noSummon().noSave().eyeHeight(0.0F).setShouldReceiveVelocityUpdates(false))
        .build()

//    //载具核心实体
//    @JvmStatic
//    val CORE_ENTITY = MachineMax.REGISTER.entityType<CoreEntity>().id("core")
//        .builder(EntityType.Builder.of(::CoreEntity, MobCategory.MISC).fireImmune().noSummon())
//        .build()
}