package io.github.tt432.machinemax.datagen

import io.github.tt432.machinemax.MachineMax
import io.github.tt432.machinemax.common.vehicle.PartType
import net.minecraft.core.RegistrySetBuilder
import net.minecraft.data.DataProvider
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.data.event.GatherDataEvent
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, modid = MachineMax.MOD_ID)
object MMGenerator {
    @JvmStatic
    @SubscribeEvent
    private fun gather(event: GatherDataEvent) {
        val generator = event.generator
        val output = generator.packOutput
        val lookupProvider = event.lookupProvider
        val helper = event.existingFileHelper

        fun addServerProvider(provider: DataProvider) = generator.addProvider(event.includeServer(), provider)
        fun addClinetProvider(provider: DataProvider) = generator.addProvider(event.includeClient(), provider)
        //语言文件 Language Files
        addClinetProvider(MMLanguageProviderZH_CN(output, MachineMax.MOD_ID, "zh_cn"))//中文
        addClinetProvider(MMLanguageProviderEN_US(output, MachineMax.MOD_ID, "en_us"))//English
        //部件类 PartTypes
    }
}