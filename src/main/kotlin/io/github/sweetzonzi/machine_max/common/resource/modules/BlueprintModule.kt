package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.vehicle.data.BlueprintData
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.resources.ResourceLocation
import net.minecraft.network.chat.Component
import java.nio.charset.StandardCharsets

class BlueprintModule : SparkPackModule {

    override val id: String = "blueprints"
    override fun onStart(isClientSide: Boolean) {
        MMDynamicRes.BLUEPRINTS.clear()
        SparkCore.LOGGER.info("开始注册载具蓝图…")
    }

    override fun read(
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean
    ) {
        if (fileName.endsWith(".json")) {
            val nameSpace: String = if (pathSegments.size > 1) {
                pathSegments[0]
            } else {
                MachineMax.MOD_ID
            }
            val path = fileName.substringBeforeLast(".")
            val id = ResourceLocation.fromNamespaceAndPath(nameSpace, path)
            try {
                val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
                val blueprint = BlueprintData.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first
                MMDynamicRes.BLUEPRINTS[id] = blueprint
            } catch (e: Exception) {
                MMDynamicRes.exceptions.add(e)
                MMDynamicRes.errorFiles.add("[Blueprint]" + id)
                MMDynamicRes.errorMessages.add(Component.literal(e.message))
            }
        }
    }


    override fun onFinish(isClientSide: Boolean) {
        MachineMax.LOGGER.info("已加载${MMDynamicRes.BLUEPRINTS.size}种载具蓝图")
    }

}