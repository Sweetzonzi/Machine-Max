package io.github.sweetzonzi.machine_max.common.command

import cn.solarmoon.spark_core.command.BaseCommand
import com.jme3.math.Vector3f
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.context.CommandContext
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionManager
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionParams
import io.github.sweetzonzi.machine_max.common.registry.MMDamageTypes
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level

/** 本指令的爆炸统一播放的中等起爆特效与散布缩放；具体弹种的起爆粒子由内容包 JSON 的 `particles` / `particle_scale` 决定。 */
private val COMMAND_DETONATION_PARTICLES =
    listOf(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "blast_medium"))
private const val COMMAND_DETONATION_PARTICLE_SCALE = 1.0f

/**
 * 爆炸系统的"指令 / 脚本"调用方（爆炸系统设计文档 §14.1），用于调试与管理。
 *
 * 用法：`mm explosion here <nearRadius> <maxRadius> <basePenetration> <baseDamage> <baseImpulse>`
 * 在玩家脚下引爆一发参数给定的爆炸。
 */
class ExplosionCommand : BaseCommand("explosion", 4) {

    override fun putExecution(context: CommandBuildContext) {
        builder.then(
            Commands.literal("here")
                .then(
                    Commands.argument("nearRadius", FloatArgumentType.floatArg(0.1f, 32f))
                        .then(
                            Commands.argument("maxRadius", FloatArgumentType.floatArg(0.1f, 128f))
                                .then(
                                    Commands.argument("basePenetration", FloatArgumentType.floatArg(0f, 2000f))
                                        .then(
                                            Commands.argument("baseDamage", FloatArgumentType.floatArg(0f, 100000f))
                                                .then(
                                                    Commands.argument("baseImpulse", FloatArgumentType.floatArg(0f, 100000f))
                                                        .executes { detonate(it) }
                                                )
                                        )
                                )
                        )
                )
        )
    }

    private fun detonate(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player ?: run {
            source.sendFailure(Component.literal("该指令只能由玩家执行。"))
            return 0
        }
        val level: Level = source.level
        val nearRadius = FloatArgumentType.getFloat(ctx, "nearRadius")
        val maxRadius = FloatArgumentType.getFloat(ctx, "maxRadius")
        val basePenetration = FloatArgumentType.getFloat(ctx, "basePenetration")
        val baseDamage = FloatArgumentType.getFloat(ctx, "baseDamage")
        val baseImpulse = FloatArgumentType.getFloat(ctx, "baseImpulse")

        val params = ExplosionParams(
            basePenetration,
            baseDamage,
            baseImpulse,
            nearRadius,
            maxRadius,
            20f,
            true,
            false,
            false,
            COMMAND_DETONATION_PARTICLES,
            COMMAND_DETONATION_PARTICLE_SCALE
        )
        val origin = Vector3f(player.x.toFloat(), player.y.toFloat() + 1f, player.z.toFloat())
        val seed = level.random.nextLong()
        val damageSource = level.damageSources().source(MMDamageTypes.BLAST)
        ExplosionManager.get(level).detonate(origin, params, damageSource, seed)

        source.sendSuccess(
            {
                Component.literal(
                    "已在 $origin 起爆: near=$nearRadius max=$maxRadius pen=$basePenetration dmg=$baseDamage imp=$baseImpulse"
                )
            },
            true
        )
        return 1
    }
}
