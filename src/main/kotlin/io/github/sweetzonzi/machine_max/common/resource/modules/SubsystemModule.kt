package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.AbstractSubsystemStaticAttr
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.resources.ResourceLocation
import net.minecraft.network.chat.Component
import java.nio.charset.StandardCharsets

/**
 * 子系统模块的读取注册
 */
class SubsystemModule : SparkPackModule {
    override val id: String = "subsystems"
    override fun onStart(isClientSide: Boolean) {
        MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.clear()
    }

    override fun read(
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean
    ) {
        if (fileName.endsWith(".json")) {
            val nameSpace: String = if (pathSegments.size > 1) {
                pathSegments[0]
            } else {
                MachineMax.MOD_ID
            }
            val path = fileName.substringBeforeLast(".")
            val id = ResourceLocation.fromNamespaceAndPath(nameSpace, path)
            try{
                val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
                val staticSubsystemAttr = AbstractSubsystemStaticAttr.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first
                MMDynamicRes.STATIC_SUBSYSTEM_ATTRS[id] = staticSubsystemAttr
            } catch (e: Exception) {
                MMDynamicRes.exceptions.add(e)
                MMDynamicRes.errorFiles.add("[Subsystem]" + id)
                MMDynamicRes.errorMessages.add(Component.literal(e.message))
            }
        }
    }


    override fun onFinish(isClientSide: Boolean) {
        MachineMax.LOGGER.info("已加载${MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.size}种子系统型号")
    }
}