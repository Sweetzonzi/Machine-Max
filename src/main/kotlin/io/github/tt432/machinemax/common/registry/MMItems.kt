package io.github.tt432.machinemax.common.registry

import io.github.tt432.machinemax.MachineMax
import io.github.tt432.machinemax.common.component.PartTypeComponent
import io.github.tt432.machinemax.common.item.prop.MMPartItem
import io.github.tt432.machinemax.common.item.prop.TestCarSpawnerItem
import net.minecraft.client.renderer.item.ItemProperties
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item

object MMItems {
    @JvmStatic
    fun register() {
    }

    //测试车生成器
    @JvmStatic
    val TEST_CAR_SPAWNER = MachineMax.REGISTER.item<TestCarSpawnerItem>()
        .id("test_car_spawner")
        .bound { TestCarSpawnerItem(Item.Properties()) }
        .build()

    //载具部件物品原型
    @JvmStatic
    val PART_ITEM = MachineMax.REGISTER.item<MMPartItem>()
        .id("part_item")
        .bound { MMPartItem(Item.Properties().stacksTo(1)) }
        .build()

    //路基方块
    @JvmStatic
    val ROAD_BASE_BLOCK = MachineMax.REGISTER.item<BlockItem>()
        .id("road_base_block")
        .bound { BlockItem(MMBlocks.ROAD_BASE_BLOCK.get(), Item.Properties()) }
}