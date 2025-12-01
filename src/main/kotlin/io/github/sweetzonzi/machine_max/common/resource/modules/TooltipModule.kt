package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.resources.ResourceLocation
import net.minecraft.network.chat.Component
import java.nio.charset.StandardCharsets

class TooltipModule : SparkPackModule {

    override val id: String = "tooltips"

    override fun onStart(isClientSide: Boolean) {
        if (isClientSide) {
            MMDynamicRes.TOOLTIPS.clear()
            SparkCore.LOGGER.info("开始注册文本内容…")
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
                MMDynamicRes.TOOLTIPS[id] = string
            } catch (e: Exception){
                MMDynamicRes.exceptions.add(e)
                MMDynamicRes.errorFiles.add("[Tooltip]" + id)
                MMDynamicRes.errorMessages.add(Component.literal(e.message))
            }
        }
    }


    override fun onFinish(isClientSide: Boolean) {
        if (isClientSide) {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.TOOLTIPS.size}种文本内容")
        }
    }

}