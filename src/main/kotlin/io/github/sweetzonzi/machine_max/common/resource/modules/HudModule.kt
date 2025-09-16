package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import cn.solarmoon.spark_core.resource2.modules.SparkPackModule
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.visual.AnimatableParams
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLEnvironment
import java.nio.charset.StandardCharsets

class HudModule : SparkPackModule {

    override val id: String = "huds"
    override fun onStart(isClientSide: Boolean) {
        if (isClientSide) {
            MMDynamicRes.CUSTOM_HUD.clear()
        }
    }

    override fun read(
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean
    ) {
        if (isClientSide && fileName.endsWith(".json")) {
            val nameSpace: String = if (pathSegments.size > 1) {
                pathSegments[0]
            } else {
                MachineMax.MOD_ID
            }
            val path = fileName.substringBeforeLast(".")
            val id = ResourceLocation.fromNamespaceAndPath(nameSpace, path)
            val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
            val hud = AnimatableParams.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first
            MMDynamicRes.CUSTOM_HUD[id] = hud
        }
    }


    override fun onFinish(isClientSide: Boolean) {
        if (isClientSide) {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.CUSTOM_HUD.size}种HUD元素")
        }
    }

}