package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.client.render.renderer.BlockEntityItemRenderer
import io.github.sweetzonzi.machine_max.client.render.renderer.CustomModelItemRenderer
import io.github.sweetzonzi.machine_max.common.item.MaterialItem
import io.github.sweetzonzi.machine_max.common.item.prop.AssemblyItem
import io.github.sweetzonzi.machine_max.common.item.prop.CrowbarItem
import io.github.sweetzonzi.machine_max.common.item.prop.EmptyBlueprintItem
import io.github.sweetzonzi.machine_max.common.item.prop.FabricatingBlueprintItem
import io.github.sweetzonzi.machine_max.common.item.prop.PadItem
import io.github.sweetzonzi.machine_max.common.item.prop.PartItem
import io.github.sweetzonzi.machine_max.common.item.prop.SprayCanItem
import io.github.sweetzonzi.machine_max.common.item.prop.VehicleBlueprintItem
import io.github.sweetzonzi.machine_max.common.item.prop.WeldingTorchItem
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
    val PART_ITEM = MachineMax.REGISTER.item {
        id="part"
        factory = { PartItem() }
    }

    //装配体物品原型
    @JvmStatic
    val ASSEMBLY_ITEM = MachineMax.REGISTER.item {
        id="assembly"
        factory = { AssemblyItem() }
    }

    //制造台
//    @JvmStatic
//    val FABRICATOR_BLOCK_ITEM = MachineMax.REGISTER.item{
//        id="fabricator"
//        factory = { BlockItem(MMBlocks.FABRICATOR_BLOCK.get(), Item.Properties()) }
//    }

    //研究台
    @JvmStatic
    val RESEARCH_TABLE_BLOCK_ITEM = MachineMax.REGISTER.item{
        id="research_table"
        factory = { BlockItem(MMBlocks.RESEARCH_TABLE_BLOCK.get(), Item.Properties()) }
    }

    //全站仪
    @JvmStatic
    val TOTAL_STATION_BLOCK_ITEM = MachineMax.REGISTER.item{
        id="total_station"
        factory = { BlockItem(MMBlocks.TOTAL_STATION_BLOCK.get(), Item.Properties()) }
    }

    //PAD
//    @JvmStatic
//    val PAD_ITEM = MachineMax.REGISTER.item{
//        id="pad"
//        factory = { PadItem() }
//    }

    //载具蓝图物品原型
    @JvmStatic
    val VEHICLE_BLUEPRINT = MachineMax.REGISTER.item {
        id="vehicle_blueprint"
        factory = { VehicleBlueprintItem() }
    }

    //载具蓝图物品原型
    @JvmStatic
    val FABRICATING_BLUEPRINT = MachineMax.REGISTER.item {
        id="fabricating_blueprint"
        factory = { FabricatingBlueprintItem() }
    }

    //载具保存物品原型
    @JvmStatic
    val EMPTY_BLUEPRINT = MachineMax.REGISTER.item{
        id="empty_blueprint"
        factory = { EmptyBlueprintItem() }
    }

    //撬棍，用于拆卸载具部件，也可作为武器
    @JvmStatic
    val CROWBAR_ITEM = MachineMax.REGISTER.item {
        id="crowbar"
        factory = { CrowbarItem() }
    }

    //焊枪，用于修复和组装载具部件，也可作为武器
    @JvmStatic
    val WELDING_TORCH_ITEM = MachineMax.REGISTER.item{
        id="welding_torch"
        factory = { WeldingTorchItem() }
    }

    //油漆喷罐，为部件切换贴图
    @JvmStatic
    val SPRAY_CAN_ITEM = MachineMax.REGISTER.item{
        id="spray_can"
        factory = { SprayCanItem() }
    }

    //材料:结构部件
    @JvmStatic
    val STRUCTURAL_COMPONENT_1_ITEM = MachineMax.REGISTER.item{
        id="structural_component_1"
        factory = { MaterialItem() }
    }

    //材料:机械零件
    @JvmStatic
    val MECHANIC_COMPONENT_1_ITEM = MachineMax.REGISTER.item{
        id="mechanic_component_1"
        factory = { MaterialItem() }
    }

    //材料:武器部件
    @JvmStatic
    val WEAPON_COMPONENT_1_ITEM = MachineMax.REGISTER.item{
        id="weapon_component_1"
        factory = { MaterialItem() }
    }

    //材料:电子元件
    @JvmStatic
    val ELECTRONIC_COMPONENT_1_ITEM = MachineMax.REGISTER.item{
        id="electronic_component_1"
        factory = { MaterialItem() }
    }

    //材料:能源组件
    @JvmStatic
    val POWER_COMPONENT_1_ITEM = MachineMax.REGISTER.item{
        id="power_component_1"
        factory = { MaterialItem() }
    }

    //材料:含能材料
    @JvmStatic
    val ENERGETIC_COMPONENT_1_ITEM = MachineMax.REGISTER.item{
        id="energetic_component_1"
        factory = { MaterialItem() }
    }

    //路基方块
//    @JvmStatic
//    val ROAD_BASE_BLOCK = MachineMax.REGISTER.item<BlockItem>()
//        .id("road_base_block")
//        .bound { BlockItem(MMBlocks.ROAD_BASE_BLOCK.get(), Item.Properties()) }

    @SubscribeEvent
    private fun regCustomModel(event: RegisterClientExtensionsEvent) {
        //在此注册拥有自定义模型物品的渲染器，物品需要继承ICustomModelItem接口
        //Register custom model item renderer here, items need to implement ICustomModelItem interface
        event.registerItem(
            CustomModelItemExtension(),
            CROWBAR_ITEM,
            SPRAY_CAN_ITEM,
            WELDING_TORCH_ITEM,
            PART_ITEM,
            FABRICATING_BLUEPRINT,
            VEHICLE_BLUEPRINT,
            EMPTY_BLUEPRINT,
            ASSEMBLY_ITEM
        )
        event.registerItem(
            CustomModelBlockEntityExtension(),
//            FABRICATOR_BLOCK_ITEM,
            RESEARCH_TABLE_BLOCK_ITEM,
            TOTAL_STATION_BLOCK_ITEM
        )
    }

    class CustomModelItemExtension : IClientItemExtensions {
        private val renderer = CustomModelItemRenderer()

        override fun getCustomRenderer(): BlockEntityWithoutLevelRenderer {
            return renderer
        }
    }

    class CustomModelBlockEntityExtension : IClientItemExtensions {
        private val renderer = BlockEntityItemRenderer()

        override fun getCustomRenderer(): BlockEntityWithoutLevelRenderer {
            return renderer
        }
    }
}