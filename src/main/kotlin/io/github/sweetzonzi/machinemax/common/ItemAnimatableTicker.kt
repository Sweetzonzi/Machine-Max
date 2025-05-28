package io.github.sweetzonzi.machinemax.common

import cn.solarmoon.spark_core.event.ItemStackInventoryTickEvent
import io.github.sweetzonzi.machinemax.MachineMax
import io.github.sweetzonzi.machinemax.common.registry.MMDataComponents
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.EventBusSubscriber.Bus

@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = Bus.GAME)
object ItemAnimatableTicker {
    //TODO:暂时放在这里，之后挪到更合适的位置
    @JvmStatic
    @SubscribeEvent
    private fun itemTick(event: ItemStackInventoryTickEvent) {
        val stack = event.stack
        if (stack.has(MMDataComponents.CUSTOM_ITEM_MODEL))
            stack.get(MMDataComponents.CUSTOM_ITEM_MODEL)?.values?.forEach{
                it.inventoryTick(event.entity)
            }
    }
}