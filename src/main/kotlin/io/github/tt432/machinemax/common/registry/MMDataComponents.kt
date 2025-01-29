package io.github.tt432.machinemax.common.registry

import io.github.tt432.machinemax.MachineMax
import io.github.tt432.machinemax.common.component.PartComponent
import io.github.tt432.machinemax.common.component.PartPortIteratorComponent
import io.github.tt432.machinemax.common.component.PartTypeComponent

object MMDataComponents {
    @JvmStatic
    fun register() {
    }

    /**
     * 保存在部件物品上的物品类型，用于从物品创建部件
     */
    @JvmStatic
    val PART_TYPE = MachineMax.REGISTER.dataComponent<PartTypeComponent>()
        .id("part_type")
        .build {
            it.persistent(PartTypeComponent.CODEC)
                .networkSynchronized(PartTypeComponent.STREAM_CODEC)
                .cacheEncoding()
        }

    /**
     * 保存在物品上的部件，用于读取保存部件安装槽，安装姿态等信息
     * Codec仅用于初始化，不在退出游戏时保存
     */
    @JvmStatic
    val PART = MachineMax.REGISTER.dataComponent<PartComponent>()
        .id("part")
        .build {
            it.persistent(PartComponent.CODEC)
//                .networkSynchronized(PartComponent.STREAM_CODEC)
        }

    @JvmStatic
    val PART_PORT_ITERATOR = MachineMax.REGISTER.dataComponent<PartPortIteratorComponent>()
        .id("part_port_iterator")
        .build {
            it.persistent(PartPortIteratorComponent.CODEC)
//                .networkSynchronized(PartPortIteratorComponent.STREAM_CODEC)
        }
}