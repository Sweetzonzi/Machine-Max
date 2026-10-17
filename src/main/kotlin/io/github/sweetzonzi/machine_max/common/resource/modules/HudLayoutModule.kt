package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.visual.HudLayout
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.network.chat.Component
import java.nio.charset.StandardCharsets

/**
 * HUD 布局加载模块。
 *
 * 读取内容包 `hud_layouts/` 下的 JSON（文件内容是有序元素数组），
 * 存入 [MMDynamicRes.HUD_LAYOUTS]。键为 `<命名空间>:<相对路径>`；
 * 布局与样式分属不同目录，因此两者的键可以同名而互不冲突。
 */
class HudLayoutModule : SparkPackModule {

    override val id: String = "hud_layouts"

    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide && fromServer) {
            MMDynamicRes.HUD_LAYOUTS.clear()
            SparkCore.LOGGER.info("开始注册HUD布局…")
        }
    }

    override fun read(
        namespace: String,
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean, fromServer: Boolean
    ) {
        if (!isClientSide || !fromServer || !fileName.endsWith(".json")) return
        val id = HudModule.contentKey(namespace, pathSegments, fileName) ?: run {
            MMDynamicRes.errorFiles.add("[HUD Layout] $namespace/${(pathSegments + fileName).joinToString("/")}")
            MMDynamicRes.errorMessages.add(Component.literal("非法的 HUD 布局路径"))
            return
        }
        try {
            val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
            val layout = HudLayout.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first
            MMDynamicRes.HUD_LAYOUTS[id] = layout
        } catch (e: Exception) {
            MMDynamicRes.exceptions.add(e)
            MMDynamicRes.errorFiles.add("[HUD Layout]$id")
            MMDynamicRes.errorMessages.add(Component.literal(e.message ?: e.toString()))
        }
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide) {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.HUD_LAYOUTS.size}份HUD布局")
        }
    }
}
