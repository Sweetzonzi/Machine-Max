package io.github.sweetzonzi.machine_max.common.command

import cn.solarmoon.spark_core.command.BaseCommand
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import io.github.sweetzonzi.machine_max.common.attachment.BlueprintAttachment
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents
import io.github.sweetzonzi.machine_max.common.registry.MMItems
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchAttachmentSyncPayload
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor

class ResearchCommand() : BaseCommand("research", 4) {

    override fun putExecution(context: CommandBuildContext) {
        builder.then(
            Commands.argument("target", EntityArgument.players())
                .then(
                    Commands.literal("recipe")
                        .then(Commands.literal("clear").executes { clearAllResearch(it) })
                        .then(Commands.literal("all").executes { allResearch(it) })
                        .then(
                            Commands.literal("remove")
                                .then(Commands.argument("recipe", ResourceLocationArgument.id()).executes { clearSpecificResearch(it) })
                        )
                        .then(
                            Commands.literal("add")
                                .then(Commands.argument("recipe", ResourceLocationArgument.id())
                                    .then(Commands.argument("levels", IntegerArgumentType.integer()).executes { addResearchLevel(it) })
                                )
                        )
                        .then(
                            Commands.literal("set")
                                .then(Commands.argument("recipe", ResourceLocationArgument.id())
                                    .then(Commands.argument("levels", IntegerArgumentType.integer(0)).executes { setResearchLevel(it) })
                                )
                        )
                )
                .then(
                    Commands.literal("rp")
                        .then(Commands.literal("clear").executes { clearFreeRp(it) })
                        .then(
                            Commands.literal("give")
                                .then(Commands.argument("amount", IntegerArgumentType.integer()).executes { giveFreeRp(it) })
                        )
                        .then(
                            Commands.literal("set")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes { setFreeRp(it) })
                        )
                )
        )
    }

    // 辅助方法：获取玩家列表
    private fun getPlayers(context: CommandContext<CommandSourceStack>): List<Player> {
        val source = context.source
        return try {
            val players = EntityArgument.getPlayers(context, "target")
            players.map { it as Player }
        } catch (_: Exception) {
            listOf(source.playerOrException)
        }
    }

    // 辅助方法：获取配方ID
    private fun getRecipeId(context: CommandContext<CommandSourceStack>): ResourceLocation {
        return ResourceLocationArgument.getId(context, "recipe")
    }

    // 辅助方法：获取数量参数
    private fun getAmount(context: CommandContext<CommandSourceStack>): Int {
        return IntegerArgumentType.getInteger(context, "amount")
    }

    private fun getLevels(context: CommandContext<CommandSourceStack>): Int {
        return IntegerArgumentType.getInteger(context, "levels")
    }

    private fun getValidatedResearchId(context: CommandContext<CommandSourceStack>): ResourceLocation? {
        val source = context.source
        val researchId = getRecipeId(context)
        if (!MMDynamicRes.ALL_RESEARCH_RECIPES.containsKey(researchId)) {
            source.sendFailure(Component.literal("未知研发配方: $researchId"))
            return null
        }
        return researchId
    }

    private fun clearAllResearch(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        return try {
            val players = getPlayers(context)
            players.forEach { player ->
                val research = player.getData(MMAttachments.BLUEPRINT)
                research.completedResearches.clear()
                research.products.clear()
                research.markDirty(player)
                syncResearchAttachment(player)
            }
            source.sendSuccess({ Component.literal("已清除研发状态") }, true)
            1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("执行失败: ${e.message}"))
            0
        }
    }

    private fun allResearch(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        return try {
            val players = getPlayers(context)
            players.forEach { player ->
                val research = player.getData(MMAttachments.BLUEPRINT)
                MMDynamicRes.ALL_RESEARCH_RECIPES.keys.forEach { researchId ->
                    research.completedResearches.add(researchId)
                }
                MMDynamicRes.BLUEPRINT_RESEARCH_RECIPES.keys.forEach { researchId ->
                    val blueprint = makeBlueprint(research, researchId)
                    if (!blueprint.isEmpty) {
                        research.products[researchId] = blueprint
                    }
                }
                research.markDirty(player)
                syncResearchAttachment(player)
            }
            source.sendSuccess({ Component.literal("已解锁全部研发") }, true)
            1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("执行失败: ${e.message}"))
            0
        }
    }

    private fun clearSpecificResearch(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val researchId = getValidatedResearchId(context) ?: return 0
        return try {
            val players = getPlayers(context)
            players.forEach { player ->
                val research = player.getData(MMAttachments.BLUEPRINT)
                research.completedResearches.remove(researchId)
                research.products.remove(researchId)
                research.markDirty(player)
                syncResearchAttachment(player)
            }
            source.sendSuccess({ Component.literal("已移除研发: $researchId") }, true)
            1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("执行失败: ${e.message}"))
            0
        }
    }

    private fun addResearchLevel(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val researchId = getValidatedResearchId(context) ?: return 0
        return try {
            val players = getPlayers(context)
            val levels = getLevels(context)
            players.forEach { player ->
                val research = player.getData(MMAttachments.BLUEPRINT)
                if (levels > 0) {
                    research.completedResearches.add(researchId)
                    if (MMDynamicRes.BLUEPRINT_RESEARCH_RECIPES.containsKey(researchId)) {
                        val blueprint = makeBlueprint(research, researchId)
                        if (!blueprint.isEmpty) {
                            research.products[researchId] = blueprint
                        }
                    }
                }
                research.markDirty(player)
                syncResearchAttachment(player)
            }
            source.sendSuccess({ Component.literal("已更新研发状态: $researchId") }, true)
            1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("执行失败: ${e.message}"))
            0
        }
    }

    private fun setResearchLevel(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val researchId = getValidatedResearchId(context) ?: return 0
        return try {
            val players = getPlayers(context)
            val target = getLevels(context)
            players.forEach { player ->
                val research = player.getData(MMAttachments.BLUEPRINT)
                if (target > 0) {
                    research.completedResearches.add(researchId)
                    if (MMDynamicRes.BLUEPRINT_RESEARCH_RECIPES.containsKey(researchId)) {
                        val blueprint = makeBlueprint(research, researchId)
                        if (!blueprint.isEmpty) {
                            research.products[researchId] = blueprint
                        }
                    }
                } else {
                    research.completedResearches.remove(researchId)
                    research.products.remove(researchId)
                }
                research.markDirty(player)
                syncResearchAttachment(player)
            }
            source.sendSuccess({ Component.literal("已设置研发状态: $researchId -> $target") }, true)
            1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("执行失败: ${e.message}"))
            0
        }
    }

    private fun clearFreeRp(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        return try {
            getPlayers(context).forEach { player ->
                player.getData(MMAttachments.BLUEPRINT).setRp(player, 0)
            }
            source.sendSuccess({ Component.literal("已清除自由研发点") }, true)
            1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("执行失败: ${e.message}"))
            0
        }
    }

    private fun giveFreeRp(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        return try {
            val amount = getAmount(context)
            getPlayers(context).forEach { player ->
                val research = player.getData(MMAttachments.BLUEPRINT)
                research.setRp(player, research.freeResearchPoint + amount)
            }
            source.sendSuccess({ Component.literal("已增加自由研发点: $amount") }, true)
            1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("执行失败: ${e.message}"))
            0
        }
    }

    private fun setFreeRp(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        return try {
            val amount = getAmount(context)
            getPlayers(context).forEach { player ->
                player.getData(MMAttachments.BLUEPRINT).setRp(player, amount)
            }
            source.sendSuccess({ Component.literal("已设置自由研发点: $amount") }, true)
            1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("执行失败: ${e.message}"))
            0
        }
    }

    private fun makeBlueprint(research: BlueprintAttachment, researchId: ResourceLocation): ItemStack {
        return research.createBlueprintProduct(researchId)
    }

    private fun syncResearchAttachment(player: Player) {
        if (player is ServerPlayer) {
            PacketDistributor.sendToPlayer(player, ResearchAttachmentSyncPayload(player.getData(MMAttachments.BLUEPRINT)))
        }
    }
}
