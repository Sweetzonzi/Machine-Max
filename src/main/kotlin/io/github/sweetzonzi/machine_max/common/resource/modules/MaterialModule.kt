package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.diagnostic.PackCodec
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.MaterialAttr
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.resources.ResourceLocation

class MaterialModule : SparkPackModule {

    override val id: String = "materials"
    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (!fromServer) return
        MMDynamicRes.MATERIALS.clear()
        SparkCore.LOGGER.info("开始注册材料类型…")
    }

    override fun read(
        namespace: String,
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean, fromServer: Boolean
    ) {
        if (!fromServer || !fileName.endsWith(".json")) return
        val id = ResourceLocation.fromNamespaceAndPath(namespace, fileName.removeSuffix(".json"))
        val material = PackCodec.read(MaterialAttr.CODEC, content)
        MMDynamicRes.MATERIALS[id] = material
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        MachineMax.LOGGER.info("已加载${MMDynamicRes.MATERIALS.size}种材料类型")
    }

}
