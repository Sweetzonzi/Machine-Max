package cn.solarmoon.spark_core.printer

import cn.solarmoon.spark_core.SparkCore
import io.github.sweetzonzi.machine_max.MachineMax
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.data.event.GatherDataEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

object PrinterRegister {
    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::data)
    }

    @JvmStatic
    fun clientRegister(bus: IEventBus) {
        if (FMLEnvironment.dist.isClient) {
            bus.addListener(::renderer)
        }
    }

    val PRINTER = MachineMax.REGISTER.block<PrinterBlock>()
        .id("printer")
        .bound { PrinterBlock(BlockBehaviour.Properties.of().noCollission()) }
        .build()

    val PRINTER_BE = MachineMax.REGISTER.blockentity<PrinterBlockEntity>()
        .id("printer")
        .bound(::PrinterBlockEntity)
        .validBlocks { arrayOf(PRINTER.get()) }
        .build()

    val PRINTER_RECIPE_TYPE = MachineMax.REGISTER.recipe<PrinterRecipe>()
        .id("printer")
        .serializer(PrinterRecipe::Serializer)
        .build()

    fun renderer(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerBlockEntityRenderer(PRINTER_BE.get(), ::PrinterBlockEntityRenderer)
    }

    fun data(event: GatherDataEvent) {
        val generator = event.generator
        generator.addProvider(event.includeServer(), PrinterRecipe.DataProvider(generator.packOutput, event.lookupProvider))
    }

}