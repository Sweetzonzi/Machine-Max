package io.github.sweetzonzi.machinemax.common.command

import com.mojang.brigadier.context.CommandContext
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleManager
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

class VehicleRemoveCommand : VehicleCommand(2) {

    override fun putExecution(context: CommandBuildContext) {
        builder.then(Commands.literal("clear").executes { clear(it) })
    }

    private fun clear(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val level = source.level
        try {
            val num = VehicleManager.removeAllVehiclesInLevel(level)
            source.sendSuccess({ Component.literal("$num vehicles in ${level.dimension().location()} have been removed.") }, false)
            return 1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("Failed to remove all vehicles in ${level.dimension().location()}: ${e.message}"))
            return 0
        }
    }
}
