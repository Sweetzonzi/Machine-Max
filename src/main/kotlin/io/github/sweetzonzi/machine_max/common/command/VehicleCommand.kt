package io.github.sweetzonzi.machine_max.common.command

import cn.solarmoon.spark_core.command.BaseCommand
import com.mojang.brigadier.context.CommandContext
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class VehicleCommand(): BaseCommand("vehicle", 4) {
    override fun putExecution(context: CommandBuildContext) {
        builder.then(Commands.literal("clear").executes { clear(it) })
        builder.then(Commands.literal("list_templates").executes { listTemplates(it) })
    }

    private fun listTemplates(ctx: CommandContext<CommandSourceStack>): Int {
        val ids = MMDynamicRes.TEMPLATES.keys.toMutableList()
        ids.sortWith(ResourceLocation::compareTo)
        ctx.source.sendSuccess({ Component.literal("已注册载具模板数: ${ids.size}") }, false)
        for (id in ids) {
            ctx.source.sendSuccess({ Component.literal("  $id") }, false)
        }
        return 1
    }

    private fun clear(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val level = source.level
        try {
            val num = ObjectManager.removeAllVehiclesInLevel(level)
            source.sendSuccess({ Component.literal("$num vehicles in ${level.dimension().location()} have been removed.") }, false)
            return 1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("Failed to remove all vehicles in ${level.dimension().location()}: ${e.message}"))
            return 0
        }
    }
}