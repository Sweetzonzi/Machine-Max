package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.vehicle.PartType
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.nio.charset.StandardCharsets

class PartModule : SparkPackModule {

    override val id: String = "parts"
    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (!fromServer) return
        if (isClientSide) {
            MMDynamicRes.PART_TYPES.clear()
        } else {
            MMDynamicRes.SERVER_PART_TYPES.clear()
        }
        SparkCore.LOGGER.info("开始注册部件…")
    }

    override fun read(
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean, fromServer: Boolean
    ) {
        if (fileName.endsWith(".json") && fromServer) {
            val nameSpace: String = if (pathSegments.isNotEmpty()) {
                pathSegments[0]
            } else {
                MachineMax.MOD_ID
            }
            val path = fileName.substringBeforeLast(".")
            val id = ResourceLocation.fromNamespaceAndPath(nameSpace, path)
            try{
                val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
                val partType = PartType.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first
                partType.registryKey = id // 注册部件
                if (isClientSide)
                    MMDynamicRes.PART_TYPES[id] = partType
                else
                    MMDynamicRes.SERVER_PART_TYPES[id] = partType
            } catch (e: Exception) {
                MMDynamicRes.exceptions.add(e)
                MMDynamicRes.errorFiles.add("[Part]" + id)
                MMDynamicRes.errorMessages.add(Component.literal(e.message))
            }
        }
    }


    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide) {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.PART_TYPES.size}个部件")
        } else {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.SERVER_PART_TYPES.size}个部件")
        }
    }

}