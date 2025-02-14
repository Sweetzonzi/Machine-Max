package io.github.tt432.machinemax.common.registry

import io.github.tt432.machinemax.MachineMax
import io.github.tt432.machinemax.common.component.PartTypeComponent
import io.github.tt432.machinemax.common.vehicle.PartType
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack

object MMCreativeTabs {
    @JvmStatic
    fun register() {
    }

    @JvmStatic
    val MACHINE_MAX_TAB = MachineMax.REGISTER.creativeTab()
        .id("machine_max_tab")
        .bound(CreativeModeTab.builder()
            .title(Component.translatable("machine_max.tab.main"))
            .icon { ItemStack(MMItems.PART_ITEM) }
            .displayItems { params, output ->
                //将所有注册了的零件的物品形式加入创造物品栏
                var list = ArrayList<ItemStack>(1)
//                for (partType in PartType.PART_TYPE.entries.map { it.get() }) {
//                    var itemStack = ItemStack(MMItems.PART_ITEM)
//                    itemStack.set(MMDataComponents.PART_TYPE, PartTypeComponent(partType))
//                    //TODO:根据零件最大生命值调整物品耐久上限
//                    list.add(itemStack)
//                }
//                list.forEach(output::accept)
                output.accept(MMItems.TEST_CAR_SPAWNER.get())
            }
        )
        .build()
}