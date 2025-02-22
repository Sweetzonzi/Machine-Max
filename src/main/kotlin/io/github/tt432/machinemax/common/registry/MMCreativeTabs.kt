package io.github.tt432.machinemax.common.registry

import io.github.tt432.machinemax.MachineMax
import io.github.tt432.machinemax.common.vehicle.PartType
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
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
                output.accept(MMItems.TEST_CAR_SPAWNER.get())
                output.accept(MMItems.CROSSBAR_ITEM.get())
                output.accept(MMItems.SPRAY_CAN_ITEM.get())
            }
        )
        .build()

    @JvmStatic
    @SubscribeEvent
    fun putPartsIntoCreativeTab(event: BuildCreativeModeTabContentsEvent) {
        if(event.tab == MACHINE_MAX_TAB.get()) {
            MachineMax.LOGGER.info("Putting parts into creative tab")
            val list = ArrayList<ItemStack>(1)//将所有注册了的零件的物品形式加入创造物品栏
            for (partType in MMRegistries.getRegistryAccess(Minecraft.getInstance().level).registry(PartType.PART_REGISTRY_KEY).get()) {
                val itemStack = ItemStack(MMItems.PART_ITEM)
                itemStack.set(MMDataComponents.PART_TYPE, partType)
                //TODO:根据零件最大生命值调整物品耐久上限
                list.add(itemStack)
            }
            list.forEach{event.accept(it)}
        }
    }
}