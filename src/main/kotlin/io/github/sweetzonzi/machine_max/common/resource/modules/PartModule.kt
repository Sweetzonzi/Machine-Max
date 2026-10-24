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

/**
 * 部件定义的内容包装载入口，按逻辑端暂存候选并在整批校验后发布。
 *
 * 只处理服务端权威来源的数据。单文件解码、模型与配置检查成功后才写入候选表，
 * 候选收集期间运行时查询表保持为空；种类的跨部件引用校验完成后发布有效子集并重建制造索引。
 * 这使文件检查失败和晚校验失败的定义都无法被实例工厂查询到。
 */
class PartModule : SparkPackModule {

    private val clientCandidates = linkedMapOf<ResourceLocation, AbstractPartType>()
    private val serverCandidates = linkedMapOf<ResourceLocation, AbstractPartType>()

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
     * 解码一个部件定义并执行文件内配置检查，成功后加入本端候选表。
     *
     * 注册名取命名空间和文件名，不包含目录段。同名文件先撤掉已有候选，防止后读取文件失败时
     * 悄然保留先前内容。异常留在当前文件读取链中，由内容包诊断归属到具体资源；此时尚不发布定义。
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
        val partType = PackCodec.read(PartKinds.CODEC, content)
        partType.registryKey = id // 注册部件
        // 装载期跨引用检查：模型与子部件区间此时已就绪，异常仍落在本文件帧内
        partType.validate()
        candidates(isClientSide)[id] = partType
    }

    /**
     * 在候选收集完成后执行种类与必需依赖校验，发布有效定义并重建派生索引。
     *
     * 无效定义及其传递依赖者均被剔除，再逐项上报诊断；即使框架只记录错误而不回滚，
     * 失败候选也没有进入运行时定义表。发布完成后释放候选映射，避免装载器持有本轮对象。
     */
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

    /**
     * 选取当前逻辑端的装载暂存表；该映射仅供本模块的串行装载回调使用。
     */
    private fun candidates(clientSide: Boolean) = if (clientSide) clientCandidates else serverCandidates

}
