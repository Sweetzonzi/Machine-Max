package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import com.google.gson.JsonParser
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLEnvironment
import java.nio.charset.StandardCharsets

class ColorModule : SparkPackModule {

    override val id: String = "colors"
    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide && fromServer) {
            MMDynamicRes.COLORS.clear()
            SparkCore.LOGGER.info("开始注册色板方案…")
        }
    }

    override fun read(
        namespace: String,
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean, fromServer: Boolean
    ) {
        if (isClientSide && fileName.endsWith(".json") && fromServer) {
            val path = fileName.removeSuffix(".json")
            val id = ResourceLocation.fromNamespaceAndPath(namespace, path)
            val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
            MMDynamicRes.COLORS[id] = json
        }
    }


    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide) {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.COLORS.size}种色板方案")
        }
    }

}