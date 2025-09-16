package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import cn.solarmoon.spark_core.resource2.modules.SparkPackModule
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLEnvironment
import java.nio.charset.StandardCharsets

class BlueprintInfoModule : SparkPackModule {

    override val id: String = "contents"

    override fun onStart(isClientSide: Boolean) {
        if (isClientSide) {
            MMDynamicRes.BLUEPRINT_INFO.clear()
        }
    }

    override fun read(
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean
    ) {
        if (isClientSide) {
            val nameSpace: String = if (pathSegments.size > 1) {
                pathSegments[0]
            } else {
                MachineMax.MOD_ID
            }
            try{
                val id = ResourceLocation.fromNamespaceAndPath(nameSpace, fileName)
                val string = String(content, StandardCharsets.UTF_8)
                MMDynamicRes.BLUEPRINT_INFO[id] = string
            } catch (e: Exception){
                MachineMax.LOGGER.error("无法解析文本内容文件: $fileName")
            }
        }
    }


    override fun onFinish(isClientSide: Boolean) {
        if (isClientSide) {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.BLUEPRINT_INFO.size}种文本内容")
        }
    }

}