package io.github.tt432.machinemax.common.registry

import io.github.tt432.machinemax.MachineMax
import io.github.tt432.machinemax.common.entity.old.entity.TestCarEntity
import io.github.tt432.machinemax.common.entity.part.MMPartEntity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory

object MMEntities {
    @JvmStatic
    fun register() {
    }

    //测试用车辆
    @JvmStatic
    val TEST_CAR_ENTITY = MachineMax.REGISTER.entityType<TestCarEntity>().id("test_car")
        .builder(EntityType.Builder.of(::TestCarEntity, MobCategory.MISC).fireImmune())
        .build()

    //部件实体
    @JvmStatic
    val PART_ENTITY = MachineMax.REGISTER.entityType<MMPartEntity>().id("part")
        .builder(EntityType.Builder.of(::MMPartEntity, MobCategory.MISC).fireImmune())
        .build()
}