package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.diagnostic.PackCodec
import cn.solarmoon.spark_core.pack.diagnostic.SparkPackIssues
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.resources.ResourceLocation

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
        if (!isClientSide || !fromServer || !fileName.endsWith(".json")) return
        val id = ResourceLocation.tryParse("$namespace:${fileName.removeSuffix(".json")}")
            ?: SparkPackIssues.error(MMDynamicRes.text(
                "error.machine_max.invalid_resource_location",
                "Invalid resource location. Only lowercase letters, numbers, hyphens and underscores are allowed."))
        // 色板不经 codec，只保留原始 JSON 供样式系统按需取值
        MMDynamicRes.COLORS[id] = PackCodec.parse(content)
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide) {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.COLORS.size}种色板方案")
        }
    }

}
