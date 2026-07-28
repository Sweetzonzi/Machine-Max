package io.github.sweetzonzi.machine_max.common.command

import cn.solarmoon.spark_core.command.BaseCommand
import com.mojang.brigadier.context.CommandContext
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class PartCommand : BaseCommand("part", 4) {
    override fun putExecution(context: CommandBuildContext) {
        builder.then(Commands.literal("list_parts").executes { listParts(it) })
    }

    private fun listParts(ctx: CommandContext<CommandSourceStack>): Int {
        val ids = MMDynamicRes.SERVER_PART_TYPES.keys.toMutableList()
        ids.sortWith(ResourceLocation::compareTo)
        ctx.source.sendSuccess({ Component.literal("已注册零件数: ${ids.size}") }, false)
        for (id in ids) {
            ctx.source.sendSuccess({ Component.literal("  $id") }, false)
        }
        return 1
    }
}
