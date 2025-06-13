package io.github.sweetzonzi.machinemax.common.registry

import io.github.sweetzonzi.machinemax.MachineMax
import io.github.sweetzonzi.machinemax.client.renderer.CustomModelItemRenderer
import io.github.sweetzonzi.machinemax.common.item.prop.BlueprintItem
import io.github.sweetzonzi.machinemax.common.item.prop.CrowbarItem
import io.github.sweetzonzi.machinemax.common.item.prop.MMPartItem
import io.github.sweetzonzi.machinemax.common.item.prop.SprayCanItem
import io.github.sweetzonzi.machinemax.common.item.prop.EmptyBlueprintItem
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

    //载具部件物品原型
    @JvmStatic
    val PART_ITEM = MachineMax.REGISTER.item<MMPartItem>()
        .id("part_item")
        .bound { MMPartItem(Item.Properties().stacksTo(1).durability(100)) }
        .build()

    //载具蓝图物品原型
    @JvmStatic
    val BLUEPRINT = MachineMax.REGISTER.item<BlueprintItem>()
        .id("blueprint")
        .bound { BlueprintItem(Item.Properties().stacksTo(1)) }
        .build()

    //载具保存物品原型
    @JvmStatic
    val EMPTY_BLUEPRINT = MachineMax.REGISTER.item<EmptyBlueprintItem>()
        .id("empty_vehicle_blueprint")
        .bound {
            EmptyBlueprintItem(
                Item.Properties().stacksTo(1)
            )
        }
        .build()

    //撬棍，用于拆卸载具部件，也可作为武器
    @JvmStatic
    val CROWBAR_ITEM = MachineMax.REGISTER.item<CrowbarItem>()
        .id("crowbar")
        .bound {
            CrowbarItem(
                Item.Properties().stacksTo(1).durability(1000)
            )
        }
        .build()

    //油漆喷罐，为部件切换贴图
    @JvmStatic
    val SPRAY_CAN_ITEM = MachineMax.REGISTER.item<SprayCanItem>()
        .id("spray_can")
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
            CROWBAR_ITEM, SPRAY_CAN_ITEM, PART_ITEM, BLUEPRINT, EMPTY_BLUEPRINT
        )
    }

    class CustomModelItemExtension : IClientItemExtensions {
        private val renderer = CustomModelItemRenderer()

        override fun getCustomRenderer(): BlockEntityWithoutLevelRenderer {
            return renderer
        }
    }
}