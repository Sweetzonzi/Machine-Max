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

class ProjectileModule : SparkPackModule {

    override val id: String = "projectiles"

    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (!fromServer) return
        if (isClientSide) {
            MMDynamicRes.PROJECTILE_TYPES.clear()
        } else {
            MMDynamicRes.SERVER_PROJECTILE_TYPES.clear()
        }
        SparkCore.LOGGER.info("开始加载投射物定义…")
    }

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
                projectileType.setRegistryKey(id)
                if (isClientSide)
                    MMDynamicRes.PROJECTILE_TYPES[id] = projectileType
                else
                    MMDynamicRes.SERVER_PROJECTILE_TYPES[id] = projectileType
            } catch (e: Exception) {
                MMDynamicRes.exceptions.add(e)
                MMDynamicRes.errorFiles.add("[Projectile]" + id)
                MMDynamicRes.errorMessages.add(Component.literal(e.message))
            }
        }
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide) {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.PROJECTILE_TYPES.size}个投射物定义")
        } else {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.SERVER_PROJECTILE_TYPES.size}个投射物定义")
        }
    }
}
