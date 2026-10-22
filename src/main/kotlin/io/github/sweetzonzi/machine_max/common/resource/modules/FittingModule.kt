package io.github.sweetzonzi.machine_max.common.resource.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.diagnostic.PackCodec
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.FittingType
import io.github.sweetzonzi.machine_max.external.MMDynamicRes
import net.minecraft.resources.ResourceLocation

/**
 * SparkPackModule — 加载 fittings/ 目录下的载具挂件型号 JSON 文件。
 *
 * 目录结构：{@code spark_modules/<pack>/<namespace>/fittings/<name>.json}
 *
 * 型号 id 是 {@code namespace:<name>}，与文件所在子目录无关。首版 {@code "type"}
 * 只有 {@code "track"}；未知取值、字段类型错误与材料缺失都由 {@code PackCodec}
 * 记为内容包错误，该型号不进入注册表，不阻断服务器启动。
 *
 * 该模块注册在 [MaterialModule] 之后：[FittingType] 的构造期会按 `material` 解析
 * 材质，材料表必须先就绪。
 */
class FittingModule : SparkPackModule {

    /** 模块 ID，对应资源包中子目录名 "fittings" */
    override val id: String = "fittings"

    /**
     * 开始加载前清理旧缓存。
     * 仅当 fromServer=true 时才执行（避免在客户端本地资源包加载时机被错误清空）。
     */
    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (!fromServer) return
        if (isClientSide) {
            MMDynamicRes.FITTING_TYPES.clear()
        } else {
            MMDynamicRes.SERVER_FITTING_TYPES.clear()
        }
        SparkCore.LOGGER.info("开始加载载具配件定义…")
    }

    /**
     * 读取并解析单个 JSON 文件。
     *
     * 解析失败时只记录内容包错误：不写注册表、不设注册键。
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
        val type = PackCodec.read(FittingType.CODEC, content)
        type.setRegistryKey(id)
        val registry = if (isClientSide) MMDynamicRes.FITTING_TYPES else MMDynamicRes.SERVER_FITTING_TYPES
        val replaced = registry.put(id, type)
        if (replaced != null) {
            MachineMax.LOGGER.warn("载具配件型号 {} 重复定义，后加载的定义覆盖前一个", id)
        }
    }

    /** 加载完成后打印统计信息 */
    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide) {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.FITTING_TYPES.size}个载具配件定义")
        } else {
            MachineMax.LOGGER.info("已加载${MMDynamicRes.SERVER_FITTING_TYPES.size}个载具配件定义")
        }
    }
}
