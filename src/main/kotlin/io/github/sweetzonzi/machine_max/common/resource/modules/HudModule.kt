package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.diagnostic.PackCodec
import cn.solarmoon.spark_core.pack.diagnostic.SparkPackIssues
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.visual.HudStyle
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.resources.ResourceLocation

/**
 * HUD 样式加载模块。
 *
 * 读取内容包 `huds/` 下的 JSON，用 [HudStyle.CODEC] 按 `type` 分派载体，
 * 存入 [MMDynamicRes.HUD_STYLES]。键为 `<命名空间>:<相对路径>`，
 * 子目录参与拼键，因此 `huds/foo/bar.json` 的键是 `<命名空间>:foo/bar`。
 */
class HudModule : SparkPackModule {

    override val id: String = "huds"

    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide && fromServer) {
            MMDynamicRes.HUD_STYLES.clear()
            SparkCore.LOGGER.info("开始注册HUD样式…")
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
        val id = contentKey(namespace, pathSegments, fileName)
            ?: SparkPackIssues.error(MMDynamicRes.text(
                "error.machine_max.hud_style.invalid_path", "Invalid HUD style path"))
        val style = PackCodec.read(HudStyle.CODEC, content)
        MMDynamicRes.HUD_STYLES[id] = style
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide) {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.HUD_STYLES.size}种HUD样式")
        }
    }

    internal companion object {
        /** 把包内路径拼成 `<命名空间>:<相对路径>` 形式的资源键。 */
        fun contentKey(namespace: String, pathSegments: List<String>, fileName: String): ResourceLocation? {
            val name = (pathSegments + fileName.removeSuffix(".json"))
                .filter { it.isNotBlank() && it != "." && it != ".." }
                .joinToString("/")
            if (name.isBlank()) return null
            return ResourceLocation.tryParse("$namespace:$name")
        }
    }
}
