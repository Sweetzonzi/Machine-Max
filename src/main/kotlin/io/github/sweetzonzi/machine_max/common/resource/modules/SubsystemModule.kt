package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.diagnostic.PackCodec
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemKinds
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractSubsystemStaticAttr
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.resources.ResourceLocation

/**
 * 子系统模块的读取注册
 */
class SubsystemModule : SparkPackModule {
    override val id: String = "subsystems"
    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        // 子系统类型的登记窗口到此为止：模组初始化阶段已登记完内置与下游类型，
        // 本模块的读取回调在此之前不会执行，封表因此不会拦下正常登记。
        SubsystemKinds.seal()
        if (!fromServer) return
        if (isClientSide) {
            MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.clear()
        } else {
            MMDynamicRes.SERVER_STATIC_SUBSYSTEM_ATTRS.clear()
        }
        SparkCore.LOGGER.info("开始注册子系统…")
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
        val staticSubsystemAttr = PackCodec.read(AbstractSubsystemStaticAttr.CODEC, content)
        if (isClientSide)
            MMDynamicRes.STATIC_SUBSYSTEM_ATTRS[id] = staticSubsystemAttr
        else
            MMDynamicRes.SERVER_STATIC_SUBSYSTEM_ATTRS[id] = staticSubsystemAttr
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide)
            MachineMax.LOGGER.info("已加载${MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.size}型子系统")
        else
            MachineMax.LOGGER.info("已加载${MMDynamicRes.SERVER_STATIC_SUBSYSTEM_ATTRS.size}型子系统")
    }
}
