package io.github.sweetzonzi.machine_max.common.command

import cn.solarmoon.spark_core.command.BaseCommand
import com.jme3.math.Quaternion
import com.jme3.math.Vector3f
import com.mojang.brigadier.context.CommandContext
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager
import io.github.sweetzonzi.machine_max.common.mech.part.PartRestoreMode
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehiclePlacementService
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.AngleArgument
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Locale

class VehicleCommand(): BaseCommand("vehicle", 4) {
    override fun putExecution(context: CommandBuildContext) {
        builder.then(Commands.literal("clear").executes { clear(it) })
        builder.then(Commands.literal("list_templates").executes { listTemplates(it) })
        builder.then(Commands.literal("summon")
            .then(Commands.argument("template", ResourceLocationArgument.id())
                .suggests { _, suggestions ->
                    SharedSuggestionProvider.suggestResource(
                        MMDynamicRes.TEMPLATES.keys.sortedWith(ResourceLocation::compareTo), suggestions)
                }
                .then(Commands.argument("pos", Vec3Argument.vec3(false))
                    .executes { summon(it, explicitYaw = false, force = false) }
                    // 字面量排在参数之前：非数字的 `force` 由它接住，数字仍归角度参数，同一层互不干扰。
                    .then(Commands.literal("force")
                        .executes { summon(it, explicitYaw = false, force = true) })
                    .then(Commands.argument("yaw", AngleArgument.angle())
                        .executes { summon(it, explicitYaw = true, force = false) }
                        .then(Commands.literal("force")
                            .executes { summon(it, explicitYaw = true, force = true) })))))
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

    /**
     * 按模板 id 在接地锚点召唤一台完整状态的载具（服务端主线程）。
     *
     * 几何、落点校验与入世全部交给 [VehiclePlacementService]，与物品放置共用同一条路径；
     * 失败时只回报被拒的那一条原因，而不是一句笼统的「空间不足」。
     *
     * @param explicitYaw 为 true 时取 `yaw` 参数，否则取执行者当前的 yaw（控制台为 0）
     * @param force 为 true 时跳过落点校验强制放置，并在成功前回报被跳过的那一条
     * @return 成功登记返回 1，任一失败返回 0
     */
    private fun summon(context: CommandContext<CommandSourceStack>, explicitYaw: Boolean, force: Boolean): Int {
        val source = context.source
        val level = source.level
        val templateId = ResourceLocationArgument.getId(context, "template")
        val template = MMDynamicRes.TEMPLATES[templateId]
        if (template == null) {
            source.sendFailure(MMLanguageRegistry.COMMAND_VEHICLE_SUMMON_UNKNOWN_TEMPLATE.component(templateId))
            return 0
        }
        val anchor = Vec3Argument.getVec3(context, "pos")
        val yaw = if (explicitYaw) AngleArgument.getAngle(context, "yaw") else source.rotation.y
        val rotation = placementRotation(yaw)

        // 模板坏掉时参考根找不到、碰撞形状合成失败，都在这里退化成一条可读的失败消息。
        val placement: VehiclePlacementService
        val bounds: AABB
        try {
            placement = VehiclePlacementService(level, template)
            bounds = placement.boundsAtOrigin(rotation).move(anchor)
        } catch (e: Exception) {
            source.sendFailure(MMLanguageRegistry.COMMAND_VEHICLE_SUMMON_FAILED.component(describe(e)))
            return 0
        }
        // 必须在入世之前判：载具自己进世界后，占位检查会对它自己的物理体成立。
        val ignored = if (force) placement.findRejection(bounds) else null

        val vehicle = try {
            placement.instantiateAndAdd(PartRestoreMode.FULL_STATE, anchor, rotation, force)
        } catch (e: Exception) {
            source.sendFailure(MMLanguageRegistry.COMMAND_VEHICLE_SUMMON_FAILED.component(describe(e)))
            return 0
        }
        if (vehicle == null) {
            source.sendFailure(MMLanguageRegistry.COMMAND_VEHICLE_SUMMON_REJECTED.component(
                rejectionText(placement.findRejection(bounds)), formatBounds(bounds)))
            return 0
        }
        if (ignored != null) {
            source.sendSuccess({ MMLanguageRegistry.COMMAND_VEHICLE_SUMMON_FORCE_IGNORED.component(rejectionText(ignored)) }, false)
        }
        source.sendSuccess({
            MMLanguageRegistry.COMMAND_VEHICLE_SUMMON_SUCCESS.component(
                templateId.toString(), vehicle.uuid.toString(), template.parts.size, formatVec(anchor))
        }, true)
        return 1
    }

    /** 拒绝成因的可读名字；边界与建造高度共用一条文案，处置方式相同。 */
    private fun rejectionText(reason: VehiclePlacementService.PlacementRejection?): MutableComponent = when (reason) {
        null -> MMLanguageRegistry.COMMAND_VEHICLE_SUMMON_REASON_UNKNOWN.component()
        VehiclePlacementService.PlacementRejection.OUT_OF_BORDER,
        VehiclePlacementService.PlacementRejection.OUTSIDE_BUILD_HEIGHT ->
            MMLanguageRegistry.COMMAND_VEHICLE_SUMMON_REASON_OUT_OF_BOUNDS.component()
        VehiclePlacementService.PlacementRejection.CHUNKS_UNLOADED ->
            MMLanguageRegistry.COMMAND_VEHICLE_SUMMON_REASON_CHUNKS_UNLOADED.component()
        VehiclePlacementService.PlacementRejection.BLOCK_COLLISION ->
            MMLanguageRegistry.COMMAND_VEHICLE_SUMMON_REASON_BLOCK_COLLISION.component()
        VehiclePlacementService.PlacementRejection.OCCUPIED ->
            MMLanguageRegistry.COMMAND_VEHICLE_SUMMON_REASON_OCCUPIED.component()
    }

    /** 命令的 yaw 按 Minecraft 约定（0 指向 +Z、俯视顺时针为正），四元数按右手法则（俯视逆时针为正），故取反后构造。 */
    private fun placementRotation(yaw: Float): Quaternion =
        Quaternion().fromAngleNormalAxis(Math.toRadians(-yaw.toDouble()).toFloat(), Vector3f.UNIT_Y)

    private fun describe(e: Exception): String =
        e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName

    private fun formatVec(pos: Vec3): String =
        String.format(Locale.ROOT, "%.1f, %.1f, %.1f", pos.x, pos.y, pos.z)

    private fun formatBounds(bounds: AABB): String = String.format(
        Locale.ROOT, "%.1f, %.1f, %.1f → %.1f, %.1f, %.1f",
        bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ)
}
