package io.github.tt432.machinemax.common.registry

import io.github.tt432.machinemax.MachineMax
import io.github.tt432.machinemax.common.component.PartAssemblyCacheComponent
import io.github.tt432.machinemax.common.component.PartAssemblyInfoComponent
import io.github.tt432.machinemax.common.vehicle.PartType

object MMDataComponents {
    @JvmStatic
    fun register() {
    }

    /**
     * 保存在部件物品上的物品类型，用于从物品创建部件
     */
    @JvmStatic
    val PART_TYPE = MachineMax.REGISTER.dataComponent<PartType>()
        .id("part_type")
        .build {
            it
                .persistent(PartType.CODEC)
                .networkSynchronized(PartType.STREAM_CODEC)
                .cacheEncoding()
        }

    @JvmStatic
    val PART_ASSEMBLY_INFO = MachineMax.REGISTER.dataComponent<PartAssemblyInfoComponent>()
        .id("part_assembly_info")
        .build {
            it
//                .persistent(PartAssemblyInfoComponent.CODEC)
                .networkSynchronized(PartAssemblyInfoComponent.STREAM_CODEC)
                .cacheEncoding()
        }

    /**
     * 用于暂存部件变体与部件对接口的迭代器
     * 仅存在于服务端，仅应在服务端被使用
     */
    @JvmStatic
    val PART_ASSEMBLY_CACHE = MachineMax.REGISTER.dataComponent<PartAssemblyCacheComponent>()
        .id("part_assembly_cache")
        .build {
            it
                .persistent(PartAssemblyCacheComponent.CODEC)
//                .networkSynchronized(PartAssemblyInfoComponent.STREAM_CODEC)
                .cacheEncoding()
        }
}