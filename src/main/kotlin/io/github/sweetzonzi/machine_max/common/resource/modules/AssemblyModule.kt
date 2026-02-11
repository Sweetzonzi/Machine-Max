package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.vehicle.data.AssemblyData
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.nio.charset.StandardCharsets

class AssemblyModule : SparkPackModule {

    override val id: String = "assemblies"
    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (!fromServer) return
        MMDynamicRes.ASSEMBLIES.clear()
        SparkCore.LOGGER.info("开始注册装配体…")
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
                val assembly = AssemblyData.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first
                MMDynamicRes.ASSEMBLIES[id] = assembly
            } catch (e: Exception) {
                MMDynamicRes.exceptions.add(e)
                MMDynamicRes.errorFiles.add("[Assembly]" + id)
                MMDynamicRes.errorMessages.add(Component.literal(e.message))
            }
        }
    }


    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        MachineMax.LOGGER.info("已加载${MMDynamicRes.ASSEMBLIES.size}种装配体")
    }

}