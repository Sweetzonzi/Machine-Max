package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.nio.charset.StandardCharsets

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
     * 解析失败时将异常记录到 MMDynamicRes 的错误列表，不中断整体加载。
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
        if (fileName.endsWith(".json") && fromServer) {
            val path = fileName.removeSuffix(".json")
            val id = ResourceLocation.fromNamespaceAndPath(namespace, path)
            try {
                val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
                val projectileType = ProjectileType.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first
                // 额外读取 block_damage_factor（不在 CODEC 中以避免 group 参数超限）
                if (json.isJsonObject) {
                    val obj = json.asJsonObject
                    if (obj.has("block_damage_factor")) {
                        projectileType.blockDamageFactor = obj.get("block_damage_factor").asFloat
                    }
                }
                projectileType.setRegistryKey(id)
                if (isClientSide)
                    MMDynamicRes.PROJECTILE_TYPES[id] = projectileType
                else
                    MMDynamicRes.SERVER_PROJECTILE_TYPES[id] = projectileType
            } catch (e: Exception) {
                MMDynamicRes.exceptions.add(e)
                MMDynamicRes.errorFiles.add("[Projectile]" + id)
                MMDynamicRes.errorMessages.add(Component.literal(e.message ?: "Unknown error"))
            }
        }
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
