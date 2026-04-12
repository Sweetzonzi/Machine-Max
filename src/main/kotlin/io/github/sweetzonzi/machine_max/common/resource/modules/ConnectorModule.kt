package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.vehicle.PartType
import io.github.sweetzonzi.machine_max.common.vehicle.attr.MaterialAttr
import io.github.sweetzonzi.machine_max.common.vehicle.attr.connector.ConnectorStaticAttr
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.nio.charset.StandardCharsets

class ConnectorModule : SparkPackModule {

    override val id: String = "connectors"
    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (!fromServer) return
        MMDynamicRes.CONNECTORS.clear()
        SparkCore.LOGGER.info("开始注册连接点类型…")
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
            try{
                val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
                val connector = ConnectorStaticAttr.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first
                MMDynamicRes.CONNECTORS[id] = connector
            } catch (e: Exception) {
                MMDynamicRes.exceptions.add(e)
                MMDynamicRes.errorFiles.add("[Connectors]$id")
                MMDynamicRes.errorMessages.add(Component.literal(e.message))
            }
        }
    }


    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        MachineMax.LOGGER.info("已加载${MMDynamicRes.CONNECTORS.size}种连接点类型")
    }

}