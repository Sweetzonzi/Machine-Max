package io.github.sweetzonzi.machinemax.common.resource.handler

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.autoregistry.AutoRegisterHandler
import cn.solarmoon.spark_core.resource.autoregistry.HandlerDiscoveryService
import cn.solarmoon.spark_core.resource.common.ResourceDiscoveryService
import cn.solarmoon.spark_core.resource.common.ResourceHandlerBase
import cn.solarmoon.spark_core.resource.graph.ResourceNode
import cn.solarmoon.spark_core.util.MultiModuleResourceExtractionUtil
import io.github.sweetzonzi.machinemax.MachineMax
import io.github.sweetzonzi.machinemax.common.registry.MMResources
import io.github.sweetzonzi.machinemax.common.vehicle.PartType
import net.minecraft.core.RegistrationInfo
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation

//@AutoRegisterHandler
class PartTypeHandler(
//    private val partRegistry: HotReloadRegistry<PartType>
) : ResourceHandlerBase() {

//    companion object {
//        init {
//            HandlerDiscoveryService.registerHandler {
//                PartTypeHandler(MMResources.PARTS)
//            }
//        }
//    }

    override fun getResourceType(): String = "parts"
    override fun getSupportedExtensions(): Set<String> = setOf("json")
//    override fun getRegistryIdentifier(): ResourceLocation? = partRegistry.key().location()
    override fun getRegistryIdentifier(): ResourceLocation? = null

    private var processedCount = 0

    // 资源被添加或修改时的处理逻辑
    override fun processResourceAdded(node: ResourceNode) {
//        val content = node.basePath.resolve(node.relativePath).readText()
//        // (你需要实现一个逻辑来分割顶点和片段着色器)
//        val (vertex, fragment) = parseShaderContent(content)
//        val shader = OShader(node.id, vertex, fragment)
//
//        // 存入静态存储
//        PartType.ORIGINS[node.id] = shader
//
//        // 注册到动态注册表（这将自动触发网络同步）
//        val resourceKey = ResourceKey.create(partRegistry.key(), node.id)
//        partRegistry.register(resourceKey, shader, RegistrationInfo.BUILT_IN)
//
//        addResourceToModule(node.getFullModuleId(), node.id)
    }

    // 资源被移除时的处理逻辑
    override fun processResourceRemoved(node: ResourceNode) {
//        PartType.ORIGINS.remove(node.id)
//        partRegistry.unregisterDynamic(node.id)
//        removeResourceFromModule(node.getFullModuleId(), node.id)
    }

    // 修改可以简化为重新添加
    override fun processResourceModified(node: ResourceNode) {
        processResourceAdded(node)
    }

    // 初始化逻辑（提取默认资源和扫描现有资源）
    override fun initialize(modMainClass: Class<*>): Boolean {
        return try {
            // 发现资源路径
            val resourcePaths = ResourceDiscoveryService.discoverResourcePaths(getResourceType())

            // 提取默认资源
            val extractionSuccess = extractDefaultResources(modMainClass)

            // 扫描并处理现有资源
            for (basePath in resourcePaths) {
                val resourceFiles = ResourceDiscoveryService.scanResourceFiles(basePath, getSupportedExtensions())

                for (resourceFile in resourceFiles) {
                    onResourceAdded(resourceFile)
                }
            }

            SparkCore.LOGGER.info("IKConstraintHandler 初始化完成，处理了 $processedCount 个IK约束")
            extractionSuccess

        } catch (e: Exception) {
            SparkCore.LOGGER.error("IKConstraintHandler 初始化失败", e)
            false
        }
    }

    private fun parseShaderContent(content: String): Pair<String, String> {
        // 示例解析逻辑
        val vert = content.substringBefore("#shader fragment")
        val frag = content.substringAfter("#shader fragment")
        return Pair(vert, frag)
    }

    private fun extractDefaultResources(modMainClass: Class<*>): Boolean {
        return MultiModuleResourceExtractionUtil.extractAllModuleResources(
            modMainClass,
            getResourceType()
        )
    }
}