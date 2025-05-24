package io.github.sweetzonzi.machinemax.common.registry

import cn.solarmoon.spark_core.event.ItemStackInventoryTickEvent
import cn.solarmoon.spark_core.registry.common.SparkCapabilities
import io.github.sweetzonzi.machinemax.MachineMax
import io.github.sweetzonzi.machinemax.client.renderer.CustomModelItemRenderer
import io.github.sweetzonzi.machinemax.common.item.prop.CrossbarItem
import io.github.sweetzonzi.machinemax.common.item.prop.MMPartItem
import io.github.sweetzonzi.machinemax.common.item.prop.SprayCanItem
import io.github.sweetzonzi.machinemax.common.item.prop.TestCarSpawnerItem
import io.github.sweetzonzi.machinemax.common.item.prop.VehicleRecoderItem
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.EventBusSubscriber.Bus
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent

@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = Bus.MOD)
object MMItems {
    @JvmStatic
    fun register() {
    }

    //测试车生成器
    @JvmStatic
    val TEST_CAR_SPAWNER = MachineMax.REGISTER.item<TestCarSpawnerItem>()
        .id("test_car_spawner")
        .bound { TestCarSpawnerItem(Item.Properties().stacksTo(1)) }
        .build()

    //载具部件物品原型
    @JvmStatic
    val PART_ITEM = MachineMax.REGISTER.item<MMPartItem>()
        .id("part_item")
        .bound { MMPartItem(Item.Properties().stacksTo(1)) }
        .build()

    //载具保存物品原型
    @JvmStatic
    val VEHICLE_RECORDER_ITEM = MachineMax.REGISTER.item<VehicleRecoderItem>()
        .id("vehicle_recorder_item")
        .bound {
            VehicleRecoderItem(
                Item.Properties().stacksTo(1)
            )
        }
        .build()

    //撬棍，用于拆卸载具部件，也可作为武器
    @JvmStatic
    val CROSSBAR_ITEM = MachineMax.REGISTER.item<CrossbarItem>()
        .id("crossbar_item")
        .bound { CrossbarItem(Item.Properties().stacksTo(1).durability(1000)) }
        .build()

    //油漆喷罐，为部件切换贴图
    @JvmStatic
    val SPRAY_CAN_ITEM = MachineMax.REGISTER.item<SprayCanItem>()
        .id("spray_can_item")
        .bound { SprayCanItem(Item.Properties().stacksTo(64)) }
        .build()

    //路基方块
    @JvmStatic
    val ROAD_BASE_BLOCK = MachineMax.REGISTER.item<BlockItem>()
        .id("road_base_block")
        .bound { BlockItem(MMBlocks.ROAD_BASE_BLOCK.get(), Item.Properties()) }

    @JvmStatic
    @SubscribeEvent
    private fun regCustomModel(event: RegisterClientExtensionsEvent) {
        //在此注册拥有自定义模型物品的渲染器，物品需要继承ICustomModelItem接口
        //Register custom model item renderer here, items need to implement ICustomModelItem interface
        event.registerItem(
            CustomModelItemExtension(),
            CROSSBAR_ITEM, SPRAY_CAN_ITEM, PART_ITEM
        )
    }

    class CustomModelItemExtension : IClientItemExtensions {
        private val renderer = CustomModelItemRenderer()

        override fun getCustomRenderer(): BlockEntityWithoutLevelRenderer {
            return renderer
        }
    }
}