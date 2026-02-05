package io.github.sweetzonzi.machine_max.common.command

import cn.solarmoon.spark_core.command.BaseCommand
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.ResourceLocationArgument
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe

class ResearchCommand() : BaseCommand("research", 4) {

    override fun putExecution(context: CommandBuildContext) {
        builder.then(Commands.argument("target", EntityArgument.players())
            // 配方相关子命令
            .then(
                Commands.literal("recipe")
                    .then(Commands.literal("clear")
                        .executes { clearAllResearch(it) } // /mm research @s recipe clear
                    )
                    .then(Commands.literal("all")
                        .executes { allResearch(it) } // /mm research @s recipe all
                    )
                    .then(Commands.literal("remove")
                        .then(Commands.argument("recipe", ResourceLocationArgument.id())
                            .executes { clearSpecificResearch(it) } // /mm research @s recipe remove machine_max:recipe_id
                        )
                    )
                    .then(Commands.literal("add")
                        .then(Commands.argument("recipe", ResourceLocationArgument.id())
                            .then(Commands.argument("levels", IntegerArgumentType.integer())
                                .executes { addResearchLevel(it) } // /mm research @s recipe add machine_max:recipe_id 1
                            )
                        )
                    )
                    .then(Commands.literal("set")
                        .then(Commands.argument("recipe", ResourceLocationArgument.id())
                            .then(Commands.argument("levels", IntegerArgumentType.integer(0))
                                .executes { setResearchLevel(it) } // /mm research @s recipe set machine_max:recipe_id 1
                            )
                        )
                    )
            )
            // 研发点相关子命令
            .then(
                Commands.literal("rp")
                    .then(Commands.literal("clear")
                        .executes { clearFreeRp(it) } // /mm research @s rp clear
                    )
                    .then(Commands.literal("give")
                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                            .executes { giveFreeRp(it) } // /mm research @s rp give 100
                        )
                    )
                    .then(Commands.literal("set")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                            .executes { setFreeRp(it) } // /mm research @s rp set 100
                        )
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
        } catch (e: Exception) {
            listOf(source.playerOrException)
        }
    }

    // 辅助方法：获取配方ID
    private fun getRecipeId(context: CommandContext<CommandSourceStack>): net.minecraft.resources.ResourceLocation {
        return ResourceLocationArgument.getId(context, "recipe")
    }

    // 辅助方法：获取数量参数
    private fun getAmount(context: CommandContext<CommandSourceStack>): Int {
        return IntegerArgumentType.getInteger(context, "amount")
    }

    private fun clearAllResearch(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        try {
            val players = getPlayers(context)
            var successCount = 0

            for (player in players) {
                val research = player.getData(MMAttachments.BLUEPRINT)
                // 清除所有研发进度
                research.researchedRecipes.clear()
                // 重置当前研究配方
                research.setResearching(player, FabricatingRecipe.EMPTY)
                // 清除待领取的蓝图物品
                research.products.clear()
                research.markDirty(player)
                successCount++
            }

            val message = if (players.size > 1)
                "已清除 ${players.size} 名玩家的所有研发进度"
            else
                "已清除玩家的所有研发进度"
            source.sendSuccess({ Component.literal(message) }, true)
            return 1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("执行失败: ${e.message}"))
            return 0
        }
    }

    private fun allResearch(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        try {
            val players = getPlayers(context)
            var successCount = 0

            for (player in players) {
                val research = player.getData(MMAttachments.BLUEPRINT)
                // 获取所有可研发配方
                val allRecipes = research.getAllResearchable()

                // 解锁所有配方
                for ((recipeId, _) in allRecipes) {
                    if (research.researchedRecipes[recipeId] == 0f || research.researchedRecipes[recipeId] == null) {
                        research.researchedRecipes[recipeId] = 1f
                    }
                }
                research.markDirty(player)
                successCount++
            }

            val message = if (players.size > 1)
                "已为 ${players.size} 名玩家解锁所有研发项"
            else
                "已为玩家解锁所有研发项"
            source.sendSuccess({ Component.literal(message) }, true)
            return 1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("执行失败: ${e.message}"))
            return 0
        }
    }

    private fun clearSpecificResearch(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        try {
            val players = getPlayers(context)
            val recipeId = getRecipeId(context)
            var successCount = 0

            for (player in players) {
                val research = player.getData(MMAttachments.BLUEPRINT)
                // 移除指定配方的研发进度
                research.researchedRecipes.remove(recipeId)
                // 如果正在研究这个配方，清除研究目标
                if (research.researchingRecipe == recipeId) {
                    research.setResearching(player, FabricatingRecipe.EMPTY)
                }
                // 移除待领取的蓝图物品
                research.products.remove(recipeId)
                research.markDirty(player)
                successCount++
            }

            val message = if (players.size > 1)
                "已清除 ${players.size} 名玩家的配方 ${recipeId} 的研发进度"
            else
                "已清除配方 ${recipeId} 的研发进度"
            source.sendSuccess({ Component.literal(message) }, true)
            return 1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("执行失败: ${e.message}"))
            return 0
        }
    }

    private fun addResearchLevel(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        try {
            val players = getPlayers(context)
            val recipeId = getRecipeId(context)
            val levels = getAmount(context)
            var successCount = 0

            for (player in players) {
                val research = player.getData(MMAttachments.BLUEPRINT)
                // 获取当前研究等级
                val currentLevel = research.getResearchLevel(recipeId)
                val newLevel = currentLevel + levels

                // 更新研究进度
                research.researchedRecipes[recipeId] = newLevel.toFloat()
                research.markDirty(player)
                successCount++
            }

            val message = if (players.size > 1)
                "已将 ${players.size} 名玩家的配方 $recipeId 增加 $levels 级"
            else
                "已将配方 $recipeId 增加 $levels 级"
            source.sendSuccess({ Component.literal(message) }, true)
            return 1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("执行失败: ${e.message}"))
            return 0
        }
    }

    private fun setResearchLevel(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        try {
            val players = getPlayers(context)
            val recipeId = getRecipeId(context)
            val targetLevel = getAmount(context)
            var successCount = 0

            for (player in players) {
                val research = player.getData(MMAttachments.BLUEPRINT)
                // 直接设置研究等级
                research.researchedRecipes[recipeId] = targetLevel.toFloat()
                if (targetLevel < 1) {
                    // 如果设置等级为0，移除蓝图物品
                    research.products.remove(recipeId)
                }
                research.markDirty(player)
                successCount++
            }

            val message = if (players.size > 1)
                "已将 ${players.size} 名玩家的配方 $recipeId 设置为 $targetLevel 级"
            else
                "已将配方 $recipeId 设置为 $targetLevel 级"
            source.sendSuccess({ Component.literal(message) }, true)
            return 1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("执行失败: ${e.message}"))
            return 0
        }
    }

    private fun clearFreeRp(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        try {
            val players = getPlayers(context)
            var successCount = 0

            for (player in players) {
                val research = player.getData(MMAttachments.BLUEPRINT)
                // 清除自由研发点
                research.setFreeResearchPoint(player, 0)
                successCount++
            }

            val message = if (players.size > 1)
                "已清除 ${players.size} 名玩家的自由研发点"
            else
                "已清除自由研发点"
            source.sendSuccess({ Component.literal(message) }, true)
            return 1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("执行失败: ${e.message}"))
            return 0
        }
    }

    private fun giveFreeRp(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        try {
            val players = getPlayers(context)
            val amount = getAmount(context)
            var successCount = 0

            for (player in players) {
                val research = player.getData(MMAttachments.BLUEPRINT)
                // 增加自由研发点
                research.setFreeResearchPoint(player, research.freeResearchPoint + amount)
                successCount++
            }

            val message = if (players.size > 1)
                "已给予 ${players.size} 名玩家各 $amount 点自由研发点"
            else
                "已给予 $amount 点自由研发点"
            source.sendSuccess({ Component.literal(message) }, true)
            return 1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("执行失败: ${e.message}"))
            return 0
        }
    }

    private fun setFreeRp(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        try {
            val players = getPlayers(context)
            val amount = getAmount(context)
            var successCount = 0

            for (player in players) {
                val research = player.getData(MMAttachments.BLUEPRINT)
                // 设置自由研发点
                research.setFreeResearchPoint(player, amount)
                successCount++
            }

            val message = if (players.size > 1)
                "已将 ${players.size} 名玩家的自由研发点设置为 $amount"
            else
                "已将自由研发点设置为 $amount"
            source.sendSuccess({ Component.literal(message) }, true)
            return 1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("执行失败: ${e.message}"))
            return 0
        }
    }
}