package io.github.sweetzonzi.machinemax.common.resource.modules

import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import cn.solarmoon.spark_core.resource2.modules.SparkPackModule
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import io.github.sweetzonzi.machinemax.MachineMax
import io.github.sweetzonzi.machinemax.common.vehicle.data.VehicleData
import io.github.sweetzonzi.machinemax.external.MMDynamicRes
import net.minecraft.resources.ResourceLocation
import java.nio.charset.StandardCharsets

class BlueprintModule : SparkPackModule {

    override val id: String = "blueprints"
    override fun onStart() {
        MMDynamicRes.BLUEPRINTS.clear()
    }

    override fun read(
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage
    ) {
        if (fileName.endsWith(".json")) {
            val nameSpace: String = if (pathSegments.size > 1) {
                pathSegments[0]
            } else {
                MachineMax.MOD_ID
            }
            val path = fileName.substringBeforeLast(".")
            val id = ResourceLocation.fromNamespaceAndPath(nameSpace, path)
            val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
            val blueprint = VehicleData.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first
            MMDynamicRes.BLUEPRINTS[id] = blueprint
        }
    }


    override fun onFinish() {
        MachineMax.LOGGER.info("已加载${MMDynamicRes.BLUEPRINTS.size}个载具蓝图")
    }

}