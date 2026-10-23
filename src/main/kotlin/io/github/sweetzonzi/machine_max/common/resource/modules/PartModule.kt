package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.diagnostic.PackCodec
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.mech.part.AbstractPartType
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions
import io.github.sweetzonzi.machine_max.common.mech.part.PartKinds
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.resources.ResourceLocation

class PartModule : SparkPackModule {

    private val clientCandidates = linkedMapOf<ResourceLocation, AbstractPartType>()
    private val serverCandidates = linkedMapOf<ResourceLocation, AbstractPartType>()

    override val id: String = "parts"
    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (!fromServer) return
        candidates(isClientSide).clear()
        PartDefinitions.begin(isClientSide)
        MMDynamicRes.invalidatePartIndexes(isClientSide)
        SparkCore.LOGGER.info("开始注册部件…")
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
        candidates(isClientSide).remove(id)
        val partType = PackCodec.read(PartKinds.CODEC, content)
        partType.registryKey = id // 注册部件
        candidates(isClientSide)[id] = partType
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (!fromServer) return
        val errors = PartDefinitions.validateAndPublish(isClientSide, candidates(isClientSide))
        candidates(isClientSide).clear()
        MMDynamicRes.onPartsPublished(isClientSide)
        errors.forEach {
            cn.solarmoon.spark_core.pack.diagnostic.SparkPackIssues.report(
                id, "*", cn.solarmoon.spark_core.pack.diagnostic.PackSeverity.ERROR,
                net.minecraft.network.chat.Component.literal(it), null,
                if (isClientSide) cn.solarmoon.spark_core.pack.diagnostic.PackLoadContext.CLIENT_REMOTE
                else cn.solarmoon.spark_core.pack.diagnostic.PackLoadContext.SERVER_LOCAL
            )
        }
        if (isClientSide) {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.PART_TYPES.size}个部件")
        } else {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.SERVER_PART_TYPES.size}个部件")
        }
    }

    private fun candidates(clientSide: Boolean) = if (clientSide) clientCandidates else serverCandidates

}
