package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.diagnostic.PackCodec
import cn.solarmoon.spark_core.pack.diagnostic.SparkPackIssues
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.visual.HudLayout
import io.github.sweetzonzi.machine_max.external.MMDynamicRes

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
        val id = HudModule.contentKey(namespace, pathSegments, fileName)
            ?: SparkPackIssues.error(MMDynamicRes.text(
                "error.machine_max.hud_layout.invalid_path", "Invalid HUD layout path"))
        val layout = PackCodec.read(HudLayout.CODEC, content)
        MMDynamicRes.HUD_LAYOUTS[id] = layout
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide) {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.HUD_LAYOUTS.size}份HUD布局")
        }
    }
}
