package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.nio.charset.StandardCharsets

class TooltipModule : SparkPackModule {

    override val id: String = "tooltips"

    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide && fromServer) {
            MMDynamicRes.TOOLTIPS.clear()
            SparkCore.LOGGER.info("开始注册文本内容…")
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
        if (isClientSide && fromServer) {
            try{
                val id = ResourceLocation.fromNamespaceAndPath(namespace, fileName)
                val string = String(content, StandardCharsets.UTF_8)
                MMDynamicRes.TOOLTIPS[id] = string
            } catch (e: Exception){
                MMDynamicRes.exceptions.add(e)
                MMDynamicRes.errorFiles.add("[Tooltip]" + id)
                MMDynamicRes.errorMessages.add(Component.literal(e.message))
            }
        }
    }


    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide) {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.TOOLTIPS.size}种文本内容")
        }
    }

}