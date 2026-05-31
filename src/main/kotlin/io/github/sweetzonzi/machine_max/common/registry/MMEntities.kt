package io.github.sweetzonzi.machine_max.common.registry

import cn.solarmoon.spark_core.entry_builder.entityTypeBuilder
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity
import io.github.sweetzonzi.machine_max.common.entity.MMProjectileEntity
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
            noSummon()
            noSave()
            updateInterval(Int.MAX_VALUE)
            fireImmune() //TODO: 检查为什么放出来瞬间有可能被判定处于岩浆中
            eyeHeight(0.0F)
            setShouldReceiveVelocityUpdates(false)
        }
    }

    //投射物实体兼容层
    @JvmStatic
    val PROJECTILE_ENTITY = MachineMax.REGISTER.entityType{
        id = "projectile"
        factory = entityTypeBuilder(::MMProjectileEntity, MobCategory.MISC){
            noSummon()
            noSave()
            fireImmune()
            eyeHeight(0.0F)
            setShouldReceiveVelocityUpdates(false)
            updateInterval(Int.MAX_VALUE)
            sized(0.1f, 0.1f)
        }
    }

}