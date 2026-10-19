package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.diagnostic.PackCodec
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.AssemblyData
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.resources.ResourceLocation

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
        if (!fromServer || !fileName.endsWith(".json")) return
        val id = ResourceLocation.fromNamespaceAndPath(namespace, fileName.removeSuffix(".json"))
        val assembly = PackCodec.read(AssemblyData.CODEC, content)
        MMDynamicRes.ASSEMBLIES[id] = assembly
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        MachineMax.LOGGER.info("已加载${MMDynamicRes.ASSEMBLIES.size}种装配体")
    }

}
