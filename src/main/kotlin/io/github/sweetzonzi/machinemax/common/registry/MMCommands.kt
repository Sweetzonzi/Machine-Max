package io.github.sweetzonzi.machinemax.common.registry

import io.github.sweetzonzi.machinemax.common.command.VehicleRemoveCommand
import net.minecraft.commands.Commands
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent

object MMCommands {
    private fun reg(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("mm")
                .then(VehicleRemoveCommand().create(event.buildContext))
        )
    }

    @JvmStatic
    fun register() {
        NeoForge.EVENT_BUS.addListener(::reg)
    }
}