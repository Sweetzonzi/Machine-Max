package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.diagnostic.PackCodec
import cn.solarmoon.spark_core.pack.diagnostic.PackLoadContext
import cn.solarmoon.spark_core.pack.diagnostic.PackSeverity
import cn.solarmoon.spark_core.pack.diagnostic.SparkPackIssues
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.mech.part.AbstractPartType
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions
import io.github.sweetzonzi.machine_max.common.mech.part.PartKinds
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.resources.ResourceLocation

/**
 * 部件定义的内容包装载入口，按逻辑端暂存候选并在整批校验后发布。
 *
 * 只处理服务端权威来源的数据。单文件解码通过后写入候选表，候选收集期间运行时查询表保持为空；
 * 全部文件读取完成后统一校验，发布有效子集并重建制造索引。解码期拒绝的内容和晚校验失败的
 * 定义都不会被实例工厂查询到，诊断按定义所属文件上报。
 */
class PartModule : SparkPackModule {

    private val clientCandidates = linkedMapOf<ResourceLocation, AbstractPartType>()
    private val serverCandidates = linkedMapOf<ResourceLocation, AbstractPartType>()
    /** 候选注册名到内容包内文件位置的旁路表，用于把晚校验失败归到具体文件。 */
    private val clientLocations = linkedMapOf<ResourceLocation, String>()
    private val serverLocations = linkedMapOf<ResourceLocation, String>()

    override val id: String = "parts"
    /**
     * 开始权威装载批次，清空本端候选与发布表，并使制造、研究索引失效。
     * 非服务端来源的回调直接忽略；同一逻辑端的装载回调应由框架串行调用。
     */
    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (!fromServer) return
        candidates(isClientSide).clear()
        PartDefinitions.begin(isClientSide)
        MMDynamicRes.invalidatePartIndexes(isClientSide)
        SparkCore.LOGGER.info("开始注册部件…")
    }

    /**
     * 解码一个部件定义并加入本端候选表。
     *
     * 注册名取命名空间和文件名，不包含目录段。同名文件先撤掉已有候选，防止后读取文件失败时
     * 悄然保留先前内容。解码期检查（空变体、堆叠数、模型与贴图存在性等）抛出的异常留在当前
     * 文件读取链中，由内容包诊断归属到具体资源，失败定义因此不会进入候选表。
     */
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
        locations(isClientSide).remove(id)
        val partType = PackCodec.read(PartKinds.CODEC, content)
        partType.registryKey = id // 注册部件
        candidates(isClientSide)[id] = partType
        locations(isClientSide)[id] =
            "$namespace:$this.id/" + (pathSegments + fileName).joinToString("/")
    }

    /**
     * 在候选收集完成后校验整批定义，发布有效子集并重建派生索引。
     *
     * 校验发生在全部包的文件读取结束之后，因此引用了其他定义的检查能看到完整候选集。
     * 无效定义及其引用者均被剔除，再按各自所属文件逐项上报诊断；即使框架只记录错误而不回滚，
     * 失败候选也没有进入运行时定义表。发布完成后释放候选与本轮位置表，避免装载器持有本轮对象。
     */
    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (!fromServer) return
        val errors = PartDefinitions.validateAndPublish(isClientSide, candidates(isClientSide))
        candidates(isClientSide).clear()
        MMDynamicRes.onPartsPublished(isClientSide)
        errors.forEach { (partId, message) ->
            SparkPackIssues.report(
                id, locations(isClientSide)[partId] ?: "*", PackSeverity.ERROR, message, null,
                if (isClientSide) PackLoadContext.CLIENT_REMOTE else PackLoadContext.SERVER_LOCAL
            )
        }
        locations(isClientSide).clear()
        if (isClientSide) {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.PART_TYPES.size}个部件")
        } else {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.SERVER_PART_TYPES.size}个部件")
        }
    }

    /**
     * 选取当前逻辑端的装载暂存表；该映射仅供本模块的串行装载回调使用。
     */
    private fun candidates(clientSide: Boolean) = if (clientSide) clientCandidates else serverCandidates

    /**
     * 选取当前逻辑端的候选位置表；键与候选表一致，用于给晚校验失败定位文件。
     */
    private fun locations(clientSide: Boolean) = if (clientSide) clientLocations else serverLocations

}
