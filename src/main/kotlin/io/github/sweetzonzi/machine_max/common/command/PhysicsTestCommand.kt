package io.github.sweetzonzi.machine_max.common.command

import cn.solarmoon.spark_core.command.BaseCommand
import com.mojang.brigadier.context.CommandContext
import io.github.sweetzonzi.machine_max.common.mech.physics_test.PhysicsTestBus
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

class PhysicsTestCommand : BaseCommand("physics_test", 4) {
    override fun putExecution(context: CommandBuildContext) {
        builder.executes { listTests(it) }
            .then(Commands.literal("run")
                .then(Commands.argument("path", ResourceLocationArgument.id())
                    .suggests { ctx, builder ->
                        val paths = PhysicsTestBus.TEST_SET
                        paths.sortedWith(ResourceLocation::compareTo)
                        for (path in paths) {
                            builder.suggest(path.toString())
                        }
                        builder.buildFuture()
                    }
                    .executes { ctx -> runPhysicsTest(ctx, ResourceLocationArgument.getId(ctx, "path")) }
                )
            )
//            .then(Commands.literal("jp")
//                .then(Commands.literal("add").executes { addJoinPosition(it) })
//                .then(Commands.literal("clear").executes { clearJoinPositions(it) })
//                .then(Commands.literal("remove")
//                    .then(Commands.literal("first").executes { removeFirstJoinPosition(it) })
//                    .then(Commands.literal("last").executes { removeLastJoinPosition(it) })
//                )
//            )
    }

    private fun listTests(ctx: CommandContext<CommandSourceStack>): Int {
        val paths = PhysicsTestBus.TEST_SET.toMutableList()
        paths.sortWith(ResourceLocation::compareTo)
        ctx.source.sendSuccess({ Component.literal("已注册测试用例") }, false)
        for (path in paths) {
            ctx.source.sendSuccess({ Component.literal("  $path") }, false)
        }
        return 1
    }

    private fun runPhysicsTest(ctx: CommandContext<CommandSourceStack>, testCase: ResourceLocation): Int {
        val source = ctx.source
        val instance = PhysicsTestBus.TEST_MAP.get(testCase)
            ?.getDeclaredConstructor(ServerPlayer::class.java)
            ?.newInstance(ctx.source.player)
        if (instance == null) {
            source.sendFailure(Component.literal("未找到测试用例: $testCase"))
            return 1
        }
        val player = source.player
        if (player == null) {
            source.sendFailure(Component.literal("该命令需要玩家执行"))
            return 1
        }
        instance.run()
        if (instance.isDisabled()) return 0
        PhysicsTestBus.INSTANCE_LIST.add(instance)
        PhysicsTestBus.LAST_RUN = instance
        return 1
    }

//    private fun addJoinPosition(ctx: CommandContext<CommandSourceStack>): Int {
//        val player = ctx.source.player!!
//        val level = player.level() as Level
//        addJoinPosition(player)
//        ctx.source.sendSuccess({ Component.literal("入点添加成功") }, false)
//        return 1
//    }

//    companion object {
//        @JvmStatic
//        fun addJoinPosition(player: ServerPlayer): Pair<Vec3?, Transform?>? {
//            val rotation = Quaternionf()
//                .rotationY(-Math.toRadians(player.yRot.toDouble()).toFloat())
//            val transform = Transform(
//                player.level().clip(
//                    ClipContext(
//                        player.eyePosition,
//                        player.eyePosition.add(
//                            player.getViewVector(1.0f)
//                                .scale(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))
//                        ),
//                        ClipContext.Block.COLLIDER,
//                        ClipContext.Fluid.NONE,
//                        player
//                    )
//                ).location.toBVector3f(),
//                rotation.toBQuaternion()
//            )
//            return Pair.of(player.lookAngle, transform)
//        }
//    }


//    private fun clearJoinPositions(ctx: CommandContext<CommandSourceStack>): Int {
//        val size = BaseJoinPositionPhysicsTest.JOIN_POSITIONS.size
//        BaseJoinPositionPhysicsTest.JOIN_POSITIONS.clear()
//        ctx.source.sendSuccess({ Component.literal("删除所有的入点, 一共${size}个") }, false)
//        return 1
//    }
//
//    private fun removeFirstJoinPosition(ctx: CommandContext<CommandSourceStack>): Int {
//        BaseJoinPositionPhysicsTest.JOIN_POSITIONS.removeFirst()
//        ctx.source.sendSuccess({ Component.literal("删除最早的入点") }, false)
//        return 1
//    }
//
//    private fun removeLastJoinPosition(ctx: CommandContext<CommandSourceStack>): Int {
//        BaseJoinPositionPhysicsTest.JOIN_POSITIONS.removeLast()
//        ctx.source.sendSuccess({ Component.literal("删除新建的入点") }, false)
//        return 1
//    }
}
