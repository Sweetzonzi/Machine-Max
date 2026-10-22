package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.item.FittingItems
import io.github.sweetzonzi.machine_max.common.recipe.PartFabricatingRecipe
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent

@EventBusSubscriber(modid = MachineMax.MOD_ID)
object MMCreativeTabs {
    @JvmStatic
    fun register() {
    }

    @JvmStatic
    val MACHINE_MAX_TAB = MachineMax.REGISTER.creativeModeTab {
        id = "machine_max_tab_main"
        factory = {
            CreativeModeTab.builder()
                .title(MMLanguageRegistry.ITEM_GROUP_MAIN.translated())
                .icon { ItemStack(MMItems.CROWBAR_ITEM) }
                .displayItems { _, output ->
//                    output.accept(MMItems.FABRICATOR_BLOCK_ITEM.get())
                    output.accept(MMItems.RESEARCH_TABLE_BLOCK_ITEM.get())
//                    output.accept(MMItems.TOTAL_STATION_BLOCK_ITEM.get())
                    output.accept(MMItems.PDA_ITEM.get())
                    output.accept(MMItems.CROWBAR_ITEM.get())
                    output.accept(MMItems.WELDING_TORCH_ITEM.get())
                    output.accept(MMItems.SPRAY_CAN_ITEM.get())
                    output.accept(MMItems.EMPTY_BLUEPRINT.get())
                    output.accept(MMItems.ENDER_GK_RESIN_ITEM.get())
//                    output.accept(MMItems.ENDER_SCANNER_ITEM.get())
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
                .title(MMLanguageRegistry.ITEM_GROUP_ASSEMBLY.translated())
                .icon {
                    if (MMDynamicRes.TEMPLATES.isEmpty()) ItemStack(MMItems.EMPTY_BLUEPRINT)
                    else {
                        val randomIndex = (0 until MMDynamicRes.TEMPLATES.size).random()
                        val item = ItemStack(MMItems.ASSEMBLY_ITEM)
                        item.set(MMDataComponents.ASSEMBLY_PATH, MMDynamicRes.TEMPLATES.keys.toList()[randomIndex])
                        item
                    }
                }
                .build()
        }
    }

    @JvmStatic
    val MACHINE_MAX_VEHICLE_BLUEPRINT_TAB = MachineMax.REGISTER.creativeModeTab {
        id = "machine_max_tab_vehicle_blueprint"
        factory = {
            CreativeModeTab.builder()
                .title(MMLanguageRegistry.ITEM_GROUP_VEHICLE_BLUEPRINT.translated())
                .icon {
                    if (MMDynamicRes.TEMPLATES.isEmpty()) ItemStack(MMItems.EMPTY_BLUEPRINT)
                    else {
                        val randomIndex = (0 until MMDynamicRes.TEMPLATES.size).random()
                        val item = ItemStack(MMItems.VEHICLE_BLUEPRINT)
                        item.set(MMDataComponents.VEHICLE_BLUEPRINT_PATH, MMDynamicRes.TEMPLATES.keys.toList()[randomIndex])
                        item
                    }
                }
                .build()
        }
    }

    @JvmStatic
    val MACHINE_MAX_PART_TAB = MachineMax.REGISTER.creativeModeTab {
        id = "machine_max_tab_part"
        factory = {
            CreativeModeTab.builder()
                .title(MMLanguageRegistry.ITEM_GROUP_PART.translated())
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
    val MACHINE_MAX_FITTING_TAB = MachineMax.REGISTER.creativeModeTab {
        id = "machine_max_tab_fitting"
        factory = {
            CreativeModeTab.builder()
                .title(MMLanguageRegistry.ITEM_GROUP_FITTING.translated())
                .icon {
                    // 用字典序第一个型号做图标；注册表为空时退回一个不带型号的履带物品
                    val first = MMDynamicRes.FITTING_TYPES.entries.minByOrNull { it.key.toString() }
                    val stack = ItemStack(MMItems.TRACK)
                    if (first != null) stack.set(MMDataComponents.FITTING_TYPE, first.key)
                    stack
                }
                .build()
        }
    }

    @JvmStatic
    val MACHINE_MAX_FABRICATING_BLUEPRINT_TAB = MachineMax.REGISTER.creativeModeTab {
        id = "machine_max_tab_fabricating_blueprint"
        factory = {
            CreativeModeTab.builder()
                .title(MMLanguageRegistry.ITEM_GROUP_FABRICATING_BLUEPRINT.translated())
                .icon {
                    if (MMDynamicRes.PART_TYPES.isEmpty()) ItemStack(MMItems.EMPTY_BLUEPRINT)
                    else {
                        val randomIndex = (0 until MMDynamicRes.PART_TYPES.size).random()
                        val item = ItemStack(MMItems.PART_FABRICATING_BLUEPRINT)
                        val partId = MMDynamicRes.PART_TYPES.keys.toList()[randomIndex]
                        item.set(MMDataComponents.PART_TYPE, partId)
                        item.set(MMDataComponents.RECIPE_TYPE, PartFabricatingRecipe.recipeIdFromPartType(partId))
                        item
                    }
                }
                .build()
        }
    }

    @SubscribeEvent
    fun putPartsIntoCreativeTab(event: BuildCreativeModeTabContentsEvent) {
        when (event.tab) {
            MACHINE_MAX_PART_TAB.get() -> {
                MachineMax.LOGGER.info("Putting parts into creative tab...")
                val externalParts = ArrayList<ItemStack>(1)//将所有外部包部件加入创造物品栏
                MMDynamicRes.PART_TYPES.forEach { (loc, type) ->
                    val itemStack = ItemStack(MMItems.PART_ITEM)
                    itemStack.set(MMDataComponents.PART_TYPE, loc)
                    itemStack.set(DataComponents.MAX_STACK_SIZE, type.maxStackSize)
                    externalParts.add(itemStack)
                }
                externalParts.forEach { event.accept(it) }
            }
            MACHINE_MAX_FITTING_TAB.get() -> {
                MachineMax.LOGGER.info("Putting fittings into creative tab...")
                val externalFittings = ArrayList<ItemStack>(1)//将所有外部包配件型号加入创造物品栏
                MMDynamicRes.FITTING_TYPES.entries.sortedBy { it.key.toString() }.forEach { (loc, type) ->
                    val item = FittingItems.itemFor(type) ?: return@forEach
                    val itemStack = ItemStack(item)
                    itemStack.set(MMDataComponents.FITTING_TYPE, loc)
                    externalFittings.add(itemStack)
                }
                externalFittings.forEach { event.accept(it) }
            }
            MACHINE_MAX_ASSEMBLY_TAB.get() -> {
                MachineMax.LOGGER.info("Putting assemblies into creative tab...")
                val externalAssemblies = ArrayList<ItemStack>(1)//将所有外部包预装配体加入创造物品栏
                MMDynamicRes.TEMPLATES.forEach { (loc, _) ->
                    val itemStack = ItemStack(MMItems.ASSEMBLY_ITEM)
                    itemStack.set(MMDataComponents.ASSEMBLY_PATH, loc)
                    externalAssemblies.add(itemStack)
                }
                externalAssemblies.forEach { event.accept(it) }
            }
            MACHINE_MAX_VEHICLE_BLUEPRINT_TAB.get() -> {
                MachineMax.LOGGER.info("Putting blueprints into creative tab...")
                val externalBlueprints = ArrayList<ItemStack>(1)//将所有外部包载具蓝图加入创造物品栏
                MMDynamicRes.TEMPLATES.forEach { (loc, _) ->
                    val itemStack = ItemStack(MMItems.VEHICLE_BLUEPRINT)
                    itemStack.set(MMDataComponents.VEHICLE_BLUEPRINT_PATH, loc)
                    externalBlueprints.add(itemStack)
                }
                externalBlueprints.forEach { event.accept(it) }
            }
            MACHINE_MAX_FABRICATING_BLUEPRINT_TAB.get() -> {
                MachineMax.LOGGER.info("Putting fabricating blueprints into creative tab...")
                val externalBlueprints = ArrayList<ItemStack>(1)//将所有外部包零件制造蓝图加入创造物品栏
                MMDynamicRes.PART_TYPES.forEach { (loc, _) ->
                    val itemStack = ItemStack(MMItems.PART_FABRICATING_BLUEPRINT)
                    itemStack.set(MMDataComponents.PART_TYPE, loc)
                    itemStack.set(MMDataComponents.RECIPE_TYPE, PartFabricatingRecipe.recipeIdFromPartType(loc))
                    externalBlueprints.add(itemStack)
                }
                externalBlueprints.forEach { event.accept(it) }
            }
        }
    }
}
