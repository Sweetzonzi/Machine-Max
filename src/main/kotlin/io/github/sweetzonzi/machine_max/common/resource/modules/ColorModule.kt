package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import cn.solarmoon.spark_core.resource2.modules.SparkPackModule
import com.google.gson.JsonParser
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLEnvironment
import java.nio.charset.StandardCharsets

class ColorModule : SparkPackModule {

    override val id: String = "colors"
    override fun onStart() {
        if (FMLEnvironment.dist.isClient) {
            MMDynamicRes.COLORS.clear()
        }
    }

    override fun read(
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage
    ) {
        if (FMLEnvironment.dist.isClient && fileName.endsWith(".json")) {
            val nameSpace: String = if (pathSegments.size > 1) {
                pathSegments[0]
            } else {
                MachineMax.MOD_ID
            }
            val path = fileName.substringBeforeLast(".")
            val id = ResourceLocation.fromNamespaceAndPath(nameSpace, path)
            val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
            MMDynamicRes.COLORS[id] = json
        }
    }


    override fun onFinish() {
        if (FMLEnvironment.dist.isClient) {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.COLORS.size}种色板方案")
        }
    }

}