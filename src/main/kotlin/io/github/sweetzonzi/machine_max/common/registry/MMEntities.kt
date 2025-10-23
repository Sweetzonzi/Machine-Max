package io.github.sweetzonzi.machine_max.common.registry

import cn.solarmoon.spark_core.entry_builder.entityTypeBuilder
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory

object MMEntities {
    @JvmStatic
    fun register() {
    }

    //部件实体
    @JvmStatic
    val PART_ENTITY = MachineMax.REGISTER.entityType{
        id = "part"
        factory = entityTypeBuilder(::MMPartEntity, MobCategory.MISC){
            fireImmune()
            noSummon()
            noSave()
            eyeHeight(0.0F)
            setShouldReceiveVelocityUpdates(false)
        }
    }

}