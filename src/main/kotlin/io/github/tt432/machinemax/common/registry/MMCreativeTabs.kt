package io.github.tt432.machinemax.common.registry

import cn.solarmoon.spark_core.entry_builder.common.CreativeTabBuilder
import io.github.tt432.machinemax.MachineMax
import io.github.tt432.machinemax.common.component.PartTypeComponent
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
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
                for (partType in PartType.PART_TYPE.entries.map { it.get() }) {
                    var itemStack = ItemStack(partType.asItem())
                    itemStack.set(MMDataComponents.PART_TYPE, PartTypeComponent(partType))
                    list.add(itemStack)
                }
                list.forEach(output::accept)
//                var list2 =MachineMax.REGISTER.itemDeferredRegister.entries.map { it.get() }.toMutableList()
//                list2.forEach(output::accept)
            }
        )
        .build()
}