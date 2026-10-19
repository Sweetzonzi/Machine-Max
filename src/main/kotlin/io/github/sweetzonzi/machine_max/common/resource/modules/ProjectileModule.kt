package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.diagnostic.PackCodec
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.resources.ResourceLocation

/**
 * SparkPackModule — 加载 projectiles/ 目录下的投射物定义 JSON 文件。
 * <p>
 * 目录结构：{@code spark_modules/<pack>/<namespace>/projectiles/<name>.json}
 * <p>
 * 每个 JSON 文件对应一个 {@link ProjectileType}，加载后存入
 * {@link MMDynamicRes#PROJECTILE_TYPES}（客户端）或
 * {@link MMDynamicRes#SERVER_PROJECTILE_TYPES}（服务端）。
 * 与 PartModule 等现有模块的模式一致。
 */
class ProjectileModule : SparkPackModule {

    /** 模块 ID，对应资源包中子目录名 "projectiles" */
    override val id: String = "projectiles"

    /**
     * 开始加载前清理旧缓存。
     * 仅当 fromServer=true 时才执行（避免在客户端本地资源包加载时机被错误清空）。
     */
    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (!fromServer) return
        if (isClientSide) {
            MMDynamicRes.PROJECTILE_TYPES.clear()
        } else {
            MMDynamicRes.SERVER_PROJECTILE_TYPES.clear()
        }
        SparkCore.LOGGER.info("开始加载投射物定义…")
    }

    /**
     * 读取并解析单个 JSON 文件。
     * 使用 ProjectileType.CODEC 反序列化，设置 registryKey 后存入全局缓存。
     */
    override fun read(
        namespace: String,
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean,
        fromServer: Boolean
    ) {
        if (!fromServer || !fileName.endsWith(".json")) return
        val id = ResourceLocation.fromNamespaceAndPath(namespace, fileName.removeSuffix(".json"))
        val projectileType = PackCodec.read(ProjectileType.CODEC, content)
        projectileType.setRegistryKey(id)
        if (isClientSide)
            MMDynamicRes.PROJECTILE_TYPES[id] = projectileType
        else
            MMDynamicRes.SERVER_PROJECTILE_TYPES[id] = projectileType
    }

    /** 加载完成后打印统计信息 */
    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide) {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.PROJECTILE_TYPES.size}个投射物定义")
        } else {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.SERVER_PROJECTILE_TYPES.size}个投射物定义")
        }
    }
}
