package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.common.command.DebugCommand
import io.github.sweetzonzi.machine_max.common.command.PartCommand
import io.github.sweetzonzi.machine_max.common.command.PhysicsTestCommand
import io.github.sweetzonzi.machine_max.common.command.ResearchCommand
import io.github.sweetzonzi.machine_max.common.command.VehicleCommand
import net.minecraft.commands.Commands
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent

object MMCommands {
    private fun reg(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("mm")
                .then(VehicleCommand().create(event.buildContext))
                .then(ResearchCommand().create(event.buildContext))
                .then(DebugCommand().create(event.buildContext))
                .then(PartCommand().create(event.buildContext))
                .then(PhysicsTestCommand().create(event.buildContext))
        )
    }

    @JvmStatic
    fun register() {
        NeoForge.EVENT_BUS.addListener(::reg)
    }
}