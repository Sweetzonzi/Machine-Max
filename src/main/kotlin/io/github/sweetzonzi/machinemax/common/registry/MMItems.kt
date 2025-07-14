package io.github.sweetzonzi.machinemax.common.registry

import io.github.sweetzonzi.machinemax.MachineMax
import io.github.sweetzonzi.machinemax.client.renderer.CustomModelItemRenderer
import io.github.sweetzonzi.machinemax.common.item.MaterialItem
import io.github.sweetzonzi.machinemax.common.item.prop.VehicleBlueprintItem
import io.github.sweetzonzi.machinemax.common.item.prop.CrowbarItem
import io.github.sweetzonzi.machinemax.common.item.prop.MMPartItem
import io.github.sweetzonzi.machinemax.common.item.prop.SprayCanItem
import io.github.sweetzonzi.machinemax.common.item.prop.EmptyBlueprintItem
import io.github.sweetzonzi.machinemax.common.item.prop.FabicatingBlueprintItem
import io.github.sweetzonzi.machinemax.common.item.prop.WrenchItem
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
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

    //部件制造台
//    @JvmStatic
//    val FABRICATOR_BLOCK_ITEM = MachineMax.REGISTER.item<BlockItem>()
//        .id("fabricator")
//        .bound { BlockItem(MMBlocks.FABRICATOR_BLOCK.get(), Item.Properties()) }
//        .build()

    //载具蓝图物品原型
    @JvmStatic
    val VEHICLE_BLUEPRINT = MachineMax.REGISTER.item<VehicleBlueprintItem>()
        .id("vehicle_blueprint")
        .bound { VehicleBlueprintItem() }
        .build()

    //载具蓝图物品原型
    @JvmStatic
    val FABRICATING_BLUEPRINT = MachineMax.REGISTER.item<FabicatingBlueprintItem>()
        .id("fabricating_blueprint")
        .bound { FabicatingBlueprintItem() }
        .build()

    //载具保存物品原型
    @JvmStatic
    val EMPTY_BLUEPRINT = MachineMax.REGISTER.item<EmptyBlueprintItem>()
        .id("empty_vehicle_blueprint")
        .bound { EmptyBlueprintItem() }
        .build()

    //撬棍，用于拆卸载具部件，也可作为武器
    @JvmStatic
    val CROWBAR_ITEM = MachineMax.REGISTER.item<CrowbarItem>()
        .id("crowbar")
        .bound { CrowbarItem() }
        .build()

    //扳手，用于修复和组装载具部件，也可作为武器
    @JvmStatic
    val WRENCH_ITEM = MachineMax.REGISTER.item<WrenchItem>()
        .id("wrench")
        .bound { WrenchItem() }
        .build()

    //油漆喷罐，为部件切换贴图
    @JvmStatic
    val SPRAY_CAN_ITEM = MachineMax.REGISTER.item<SprayCanItem>()
        .id("spray_can")
        .bound { SprayCanItem() }
        .build()

    //材料:结构部件
    @JvmStatic
    val STRUCTURAL_COMPONENT_1_ITEM = MachineMax.REGISTER.item<MaterialItem>()
        .id("structural_component_1")
        .bound { MaterialItem() }
        .build()

    //材料:机械零件
    @JvmStatic
    val MECHANIC_COMPONENT_1_ITEM = MachineMax.REGISTER.item<MaterialItem>()
        .id("mechanic_component_1")
        .bound { MaterialItem() }
        .build()

    //材料:武器部件
    @JvmStatic
    val WEAPON_COMPONENT_1_ITEM = MachineMax.REGISTER.item<MaterialItem>()
        .id("weapon_component_1")
        .bound { MaterialItem() }
        .build()

    //材料:电子元件
    @JvmStatic
    val ELECTRONIC_COMPONENT_1_ITEM = MachineMax.REGISTER.item<MaterialItem>()
        .id("electronic_component_1")
        .bound { MaterialItem() }
        .build()

    //材料:能源组件
    @JvmStatic
    val POWER_COMPONENT_1_ITEM = MachineMax.REGISTER.item<MaterialItem>()
        .id("power_component_1")
        .bound { MaterialItem() }
        .build()

    //材料:火控品
    @JvmStatic
    val ENERGETIC_COMPONENT_1_ITEM = MachineMax.REGISTER.item<MaterialItem>()
        .id("energetic_component_1")
        .bound { MaterialItem() }
        .build()

    //路基方块
//    @JvmStatic
//    val ROAD_BASE_BLOCK = MachineMax.REGISTER.item<BlockItem>()
//        .id("road_base_block")
//        .bound { BlockItem(MMBlocks.ROAD_BASE_BLOCK.get(), Item.Properties()) }

    @JvmStatic
    @SubscribeEvent
    private fun regCustomModel(event: RegisterClientExtensionsEvent) {
        //在此注册拥有自定义模型物品的渲染器，物品需要继承ICustomModelItem接口
        //Register custom model item renderer here, items need to implement ICustomModelItem interface
        event.registerItem(
            CustomModelItemExtension(),
            CROWBAR_ITEM, SPRAY_CAN_ITEM, PART_ITEM, FABRICATING_BLUEPRINT, VEHICLE_BLUEPRINT, EMPTY_BLUEPRINT
        )
    }

    class CustomModelItemExtension : IClientItemExtensions {
        private val renderer = CustomModelItemRenderer()

        override fun getCustomRenderer(): BlockEntityWithoutLevelRenderer {
            return renderer
        }
    }
}