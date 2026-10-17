package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.pack.SparkPackLoaderApplier
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.ReadMode
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import com.sighs.apricityui.parser.HTML
import io.github.sweetzonzi.machine_max.MachineMax
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType

/**
 * 通用 AUI 文档资源模块。
 *
 * <p>内容包布局为 {@code spark_modules/<pack>/<namespace>/documents/<path>}；
 * 模块像 {@code TextureModule} 一样，在客户端把文件直接写进 Spark 的虚拟资源包，
 * 路径为 {@code apricityui:apricity/<namespace>/<path>}。AUI 的普通资源管理器
 * 与自动重载会负责发现 HTML、CSS、JS、图片、字体和音频；`onFinish` 再调用
 * 一次 `HTML.scan()` 作为保险。</p>
 *
 * <p>文档是客户端资源，模块使用 {@link ReadMode#LOCAL_ONLY}，不参与服务端到客户端
 * 的包同步；客户端本地的 {@code spark_modules} 就是唯一来源。</p>
 */
class DocumentModule : SparkPackModule {

    override val id: String = "documents"

    override val mode: ReadMode = ReadMode.LOCAL_ONLY

    private var loaded = 0

    override fun onInitialize(isClientSide: Boolean) {
        if (isClientSide) {
            seedNamespace()
        }
    }

    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide) {
            loaded = 0
        }
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
        if (!isClientSide) return

        val key = documentKey(namespace, pathSegments, fileName) ?: return
        val location = ResourceLocation.tryParse("apricityui:apricity/$key")
        if (location == null) {
            MachineMax.LOGGER.warn("[AUI Document] invalid AUI path: {}", key)
            return
        }

        SparkPackLoaderApplier.CLIENT_PACK.put(PackType.CLIENT_RESOURCES, location, content)
        loaded++
    }

    /**
     * 客户端读完后主动扫描一次作为保险；AUI 的自动重载仍负责常规发现。
     *
     * <p>{@code HTML} 属于 AUI 的 common 包，不是 Minecraft 客户端类；这里只在
     * 客户端分支调用。启动阶段 AUI 还会再扫描一次，重复调用不会破坏模板表。</p>
     */
    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (!isClientSide || loaded <= 0) return
        try {
            HTML.scan()
        } catch (exception: RuntimeException) {
            MachineMax.LOGGER.warn("[AUI Document] AUI template scan failed", exception)
        }
    }

    /**
     * 在资源包注册前写入 `apricityui` 命名空间种子。
     *
     * <p>种子让 {@code CLIENT_PACK} 在资源管理器构造时已经包含该命名空间；之后通过
     * `/reload` 新增的文档也能被资源管理器列出。</p>
     */
    private fun seedNamespace() {
        SparkPackLoaderApplier.CLIENT_PACK.put(
            PackType.CLIENT_RESOURCES,
            ResourceLocation.fromNamespaceAndPath("apricityui", "apricity/.keep"),
            ByteArray(0)
        )
    }

    /**
     * 把包内路径规范化成 AUI 页面键。
     *
     * <p>拒绝空路径、绝对路径、反斜杠、`.`/`..` 段和空段；命名空间为空也拒绝。
     * 文件后缀不在这里过滤，CSS、JS、图片、字体和音频都走同一张表。</p>
     */
    private fun documentKey(namespace: String, pathSegments: List<String>, fileName: String): String? {
        if (namespace.isBlank() || namespace.any { it == '/' || it == '\\' || it == ':' }) return null
        if (fileName.isBlank() || fileName == "." || fileName == "..") return null

        val raw = (pathSegments + fileName)
            .joinToString("/")
            .trim()
        if (raw.isEmpty() || raw.startsWith('/')) return null
        if (raw.contains('\\') || raw.contains('\u0000')) return null

        val segments = raw.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return null

        return "$namespace/$raw"
    }
}
