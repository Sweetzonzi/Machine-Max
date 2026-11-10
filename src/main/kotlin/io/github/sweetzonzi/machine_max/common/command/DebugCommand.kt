package io.github.sweetzonzi.machine_max.common.command

import cn.solarmoon.spark_core.command.BaseCommand
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractGroundControllerSubsystem
import io.github.sweetzonzi.machine_max.common.mech.subsystem.WeaponControllerSubsystem
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player

class DebugCommand : BaseCommand("debug", 4) {
    override fun putExecution(context: CommandBuildContext) {
        builder.then(
            Commands.literal("telemetry")
                .then(Commands.literal("start").executes { setTelemetry(it, true) })
                .then(Commands.literal("stop").executes { setTelemetry(it, false) })
                .then(
                    Commands.literal("aim")
                        .then(Commands.literal("start").executes { setTelemetry(it, true, aim = true) })
                        .then(Commands.literal("stop").executes { setTelemetry(it, false, aim = true) })
                )
        )
        builder.then(
            Commands.literal("autofire")
                .then(Commands.literal("stop").executes { stopAutoFire(it) })
                .then(
                    Commands.literal("start")
                        .then(
                            Commands.argument("triggerInterval", IntegerArgumentType.integer(1))
                                .then(
                                    Commands.argument("fireDuration", IntegerArgumentType.integer(1))
                                        .executes { startAutoFire(it) }
                                )
                        )
                )
        )
    }

    private fun startAutoFire(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player ?: run {
            source.sendFailure(Component.literal("This command can only be executed by a player."))
            return 0
        }
        val triggerInterval = IntegerArgumentType.getInteger(ctx, "triggerInterval")
        val fireDuration = IntegerArgumentType.getInteger(ctx, "fireDuration")

        val vehicle = getPlayerVehicle(player) ?: run {
            source.sendFailure(Component.literal("You are not riding a vehicle."))
            return 0
        }

        val weaponController = vehicle.getSubsystemController().allSubsystems.stream()
            .filter { s -> s is WeaponControllerSubsystem }
            .map { s -> s as WeaponControllerSubsystem }
            .findFirst()
            .orElse(null) ?: run {
            source.sendFailure(Component.literal("No WeaponControllerSubsystem found on this vehicle."))
            return 0
        }

        weaponController.setDebugTriggerInterval(triggerInterval)
        weaponController.setDebugFireDuration(fireDuration)
        weaponController.setDebugAutoFire(true)
        source.sendSuccess(
            { Component.literal("Debug auto-fire STARTED: interval=${triggerInterval}t, duration=${fireDuration}t") },
            false
        )
        return 1
    }

    private fun stopAutoFire(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player ?: run {
            source.sendFailure(Component.literal("This command can only be executed by a player."))
            return 0
        }

        val vehicle = getPlayerVehicle(player) ?: run {
            source.sendFailure(Component.literal("You are not riding a vehicle."))
            return 0
        }

        val weaponController = vehicle.getSubsystemController().allSubsystems.stream()
            .filter { s -> s is WeaponControllerSubsystem }
            .map { s -> s as WeaponControllerSubsystem }
            .findFirst()
            .orElse(null) ?: run {
            source.sendFailure(Component.literal("No WeaponControllerSubsystem found on this vehicle."))
            return 0
        }

        weaponController.setDebugAutoFire(false)
        source.sendSuccess(
            { Component.literal("Debug auto-fire STOPPED.") },
            false
        )
        return 1
    }

    /**
     * 主线程为当前乘坐载具开关服务端 CSV 录制。
     */
    private fun setTelemetry(ctx: CommandContext<CommandSourceStack>, recording: Boolean, aim: Boolean = false): Int {
        val source = ctx.source
        val player = source.player ?: run {
            source.sendFailure(Component.literal("This command can only be executed by a player."))
            return 0
        }
        val vehicle = getPlayerVehicle(player) ?: run {
            source.sendFailure(Component.literal("You are not riding a vehicle."))
            return 0
        }
        if (aim) return setAimTelemetry(source, vehicle, recording)

        val controllers = vehicle.subsystemController.allSubsystems
            .filterIsInstance<AbstractGroundControllerSubsystem>()
        if (controllers.isEmpty()) {
            source.sendFailure(Component.literal("No ground controller subsystem found on this vehicle."))
            return 0
        }

        if (!recording) {
            val summaries = controllers.mapNotNull { it.stopTelemetry() }
            if (summaries.isEmpty()) {
                source.sendFailure(Component.literal("Telemetry is not recording."))
                return 0
            }
            for (summary in summaries) {
                source.sendSuccess({ Component.literal("Telemetry written: $summary") }, false)
            }
            return 1
        }

        val started = controllers.count { it.startTelemetry() }
        if (started == 0) {
            source.sendFailure(Component.literal("Telemetry is already recording, or this side is the client."))
            return 0
        }
        source.sendSuccess(
            { Component.literal("Telemetry STARTED on $started controller(s): logs/machine_max/") },
            false
        )
        return 1
    }

    private fun setAimTelemetry(source: CommandSourceStack, vehicle: VehicleCore, recording: Boolean): Int {
        val controllers = vehicle.subsystemController.allSubsystems.filterIsInstance<WeaponControllerSubsystem>()
        if (controllers.isEmpty()) {
            source.sendFailure(Component.literal("No weapon controller subsystem found on this vehicle."))
            return 0
        }
        if (recording) {
            val started = controllers.count { it.startAimTelemetry() }
            if (started == 0) {
                source.sendFailure(Component.literal("Could not start aim telemetry. Check active recordings and the server log."))
                return 0
            }
            source.sendSuccess(
                { Component.literal("Aim telemetry STARTED on $started controller(s): logs/machine_max/") },
                false
            )
        } else {
            val summaries = controllers.mapNotNull { it.stopAimTelemetry() }
            if (summaries.isEmpty()) {
                source.sendFailure(Component.literal("Aim telemetry is not recording."))
                return 0
            }
            for (summary in summaries) {
                source.sendSuccess({ Component.literal("Aim telemetry closed: $summary") }, false)
            }
        }
        return 1
    }

    private fun getPlayerVehicle(player: Player): VehicleCore? {
        val controlling = (player as IEntityMixin).`machine_Max$getControllingSubsystem`() ?: return null
        val subPart = controlling.owner.subPart ?: return null
        val part = subPart.part ?: return null
        val assembly = part.assembly ?: return null
        return if (assembly is VehicleCore) assembly else null
    }
}
