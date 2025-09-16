package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import cn.solarmoon.spark_core.resource2.modules.SparkPackModule
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.vehicle.PartType
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLEnvironment
import java.nio.charset.StandardCharsets

class PartModule : SparkPackModule {

    override val id: String = "parts"
    override fun onStart(isClientSide: Boolean) {
        if (isClientSide) {
            MMDynamicRes.PART_TYPES.clear()
        } else {
            MMDynamicRes.SERVER_PART_TYPES.clear()
        }
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
            val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
            val partType = PartType.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first
            MMDynamicRes.PART_TYPES[id] = partType
            val partType2 = PartType.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first
            MMDynamicRes.SERVER_PART_TYPES[id] = partType2
        }
    }


    override fun onFinish(isClientSide: Boolean) {
        if (isClientSide) {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.PART_TYPES.size}个部件")
        } else {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.SERVER_PART_TYPES.size}个部件")
        }
    }

}