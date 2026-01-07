package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent

@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object MMCreativeTabs {
    @JvmStatic
    fun register() {
    }

    @JvmStatic
    val MACHINE_MAX_TAB = MachineMax.REGISTER.creativeModeTab {
        id = "machine_max_tab_main"
        factory = {
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.machine_max.main"))
                .icon { ItemStack(MMItems.CROWBAR_ITEM) }
                .displayItems { _, output ->
//                    output.accept(MMItems.FABRICATOR_BLOCK_ITEM.get())
                    output.accept(MMItems.RESEARCH_TABLE_BLOCK_ITEM.get())
//                    output.accept(MMItems.PAD_ITEM.get())
                    output.accept(MMItems.CROWBAR_ITEM.get())
                    output.accept(MMItems.WELDING_TORCH_ITEM.get())
                    output.accept(MMItems.SPRAY_CAN_ITEM.get())
                    output.accept(MMItems.EMPTY_BLUEPRINT.get())
                    //材料
                    output.accept(MMItems.STRUCTURAL_COMPONENT_1_ITEM.get())
                    output.accept(MMItems.MECHANIC_COMPONENT_1_ITEM.get())
                    output.accept(MMItems.WEAPON_COMPONENT_1_ITEM.get())
                    output.accept(MMItems.ELECTRONIC_COMPONENT_1_ITEM.get())
                    output.accept(MMItems.POWER_COMPONENT_1_ITEM.get())
                    output.accept(MMItems.ENERGETIC_COMPONENT_1_ITEM.get())
                }
                .build()
        }
    }

    @JvmStatic
    val MACHINE_MAX_ASSEMBLY_TAB = MachineMax.REGISTER.creativeModeTab {
        id = "machine_max_tab_assembly"
        factory = {
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.machine_max.assembly"))
                .icon { ItemStack(MMItems.EMPTY_BLUEPRINT) }
                .build()
        }
    }

    @JvmStatic
    val MACHINE_MAX_VEHICLE_BLUEPRINT_TAB = MachineMax.REGISTER.creativeModeTab {
        id = "machine_max_tab_vehicle_blueprint"
        factory = {
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.machine_max.vehicle_blueprint"))
                .icon { ItemStack(MMItems.EMPTY_BLUEPRINT) }
                .build()
        }
    }

    @JvmStatic
    val MACHINE_MAX_PART_TAB = MachineMax.REGISTER.creativeModeTab {
        id = "machine_max_tab_part"
        factory = {
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.machine_max.part"))
                // 动态设置图标
                .icon {
                    if (MMDynamicRes.PART_TYPES.isEmpty()) ItemStack(MMItems.EMPTY_BLUEPRINT)
                    else {
                        val randomIndex = (0 until MMDynamicRes.PART_TYPES.size).random() //随机的一个蓝图在图标上展示
                        val item = ItemStack(MMItems.PART_ITEM)
                        item.set(MMDataComponents.PART_TYPE, MMDynamicRes.PART_TYPES.keys.toList()[randomIndex])
                        item
                    }
                }
                .build()
        }
    }

    @JvmStatic
    val MACHINE_MAX_FABRICATING_BLUEPRINT_TAB = MachineMax.REGISTER.creativeModeTab {
        id = "machine_max_tab_fabricating_blueprint"
        factory = {
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.machine_max.fabricating_blueprint"))
                .icon { ItemStack(MMItems.EMPTY_BLUEPRINT) }
                .build()
        }
    }

    @SubscribeEvent
    fun putPartsIntoCreativeTab(event: BuildCreativeModeTabContentsEvent) {
        if (event.tab == MACHINE_MAX_PART_TAB.get()) {
            MachineMax.LOGGER.info("Putting parts into creative tab...")
            val externalParts = ArrayList<ItemStack>(1)//将所有外部包物品加入创造物品栏
            MMDynamicRes.PART_TYPES.forEach { (loc, _) ->
                val itemStack = ItemStack(MMItems.PART_ITEM)
                itemStack.set(MMDataComponents.RECIPE_TYPE, loc)
                itemStack.set(MMDataComponents.PART_TYPE, loc)
                externalParts.add(itemStack)
            }
            externalParts.forEach { event.accept(it) }
        } else if (event.tab == MACHINE_MAX_ASSEMBLY_TAB.get()) {
            MachineMax.LOGGER.info("Putting assemblies into creative tab...")
            val externalAssemblies = ArrayList<ItemStack>(1)//将所有外部包预装配体加入创造物品栏
            MMDynamicRes.TEMPLATES.forEach { (loc, _) ->
                val itemStack = ItemStack(MMItems.ASSEMBLY_ITEM)
                itemStack.set(MMDataComponents.ASSEMBLY_PATH, loc)
                externalAssemblies.add(itemStack)
            }
            externalAssemblies.forEach { event.accept(it) }
        }  else if (event.tab == MACHINE_MAX_VEHICLE_BLUEPRINT_TAB.get()) {
            MachineMax.LOGGER.info("Putting blueprints into creative tab...")
            val externalBlueprints = ArrayList<ItemStack>(1)//将所有外部包载具蓝图加入创造物品栏
            MMDynamicRes.TEMPLATES.forEach { (loc, _) ->
                val itemStack = ItemStack(MMItems.VEHICLE_BLUEPRINT)
                itemStack.set(MMDataComponents.VEHICLE_BLUEPRINT_PATH, loc)
                externalBlueprints.add(itemStack)
            }
            externalBlueprints.forEach { event.accept(it) }
        } else if (event.tab == MACHINE_MAX_FABRICATING_BLUEPRINT_TAB.get()) {
            MachineMax.LOGGER.info("Putting fabricating blueprints into creative tab...")
            val externalBlueprints = ArrayList<ItemStack>(1)//将所有外部包部件蓝图加入创造物品栏
            MMDynamicRes.PART_TYPES.forEach { (loc, _) ->
                val itemStack = ItemStack(MMItems.FABRICATING_BLUEPRINT)
                itemStack.set(MMDataComponents.RECIPE_TYPE, loc)
                itemStack.set(MMDataComponents.RESEARCH_LEVEL, 0)
                itemStack.set(MMDataComponents.PART_TYPE, loc)
                externalBlueprints.add(itemStack)
            }
            externalBlueprints.forEach { event.accept(it) }
        }
    }
}