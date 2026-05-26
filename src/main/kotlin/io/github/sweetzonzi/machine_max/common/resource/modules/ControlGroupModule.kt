package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.nio.charset.StandardCharsets

/**
 * 控制组预设加载模块。
 * 读取 control_groups/ 目录下的 JSON 文件，用 ControlGroupSet.CODEC 反序列化，
 * 存入 MMDynamicRes.CONTROL_GROUP_PRESETS 供 AbstractControllableSubsystem 按 RL 引用。
 */
class ControlGroupModule : SparkPackModule {

    override val id: String = "control_groups"

    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (!fromServer) return
        MMDynamicRes.CONTROL_GROUP_PRESETS.clear()
        SparkCore.LOGGER.info("开始注册控制组预设…")
    }

    override fun read(
        namespace: String,
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean, fromServer: Boolean
    ) {
        if (fileName.endsWith(".json") && fromServer) {
            val path = fileName.removeSuffix(".json")
            val id = ResourceLocation.fromNamespaceAndPath(namespace, path)
            try {
                val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
                val preset = ControlGroupSet.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first
                MMDynamicRes.CONTROL_GROUP_PRESETS[id] = preset
            } catch (e: Exception) {
                MMDynamicRes.exceptions.add(e)
                MMDynamicRes.errorFiles.add("[ControlGroup]$id")
                MMDynamicRes.errorMessages.add(Component.literal(e.message))
            }
        }
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        MachineMax.LOGGER.info("已加载${MMDynamicRes.CONTROL_GROUP_PRESETS.size}个控制组预设")
    }

}
