package io.github.tt432.machinemax.datagen

import io.github.tt432.machinemax.MachineMax
import io.github.tt432.machinemax.external.MMDynamicRes
import net.minecraft.data.DataProvider
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.data.event.GatherDataEvent


@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, modid = MachineMax.MOD_ID)
object MMGenerator {
    @JvmStatic
    @SubscribeEvent
    private fun gather(event: GatherDataEvent) {
        MMDynamicRes.loadData()
        OtherLanguage.injection()

        val generator = event.generator
        val output = generator.packOutput
        val lookupProvider = event.lookupProvider
        val helper = event.existingFileHelper

        fun addServerProvider(provider: DataProvider) = generator.addProvider(event.includeServer(), provider)
        fun addClinetProvider(provider: DataProvider) = generator.addProvider(event.includeClient(), provider)

        for (lang in MMDynamicRes.CUSTOM_LANGUAGE_PROVIDERS.keys) {
            if (!MMDynamicRes.LANGUAGES.containsKey(lang))
                MMDynamicRes.LANGUAGES[lang] = ArrayList()
        }
        MMDynamicRes.LANGUAGES.forEach { (locale, li) -> //动态包的每个语言的翻译
            val collection = HashMap<String, String>()
            for (translation in li) {
                translation.forEach { (tag, trans) ->
                    collection[tag] = trans
                }
            }
            addClinetProvider(DynamicLanguageProvider(output, MachineMax.MOD_ID, locale) { collection })
        }
        //部件类 PartTypes
    }
}