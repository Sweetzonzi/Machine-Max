package io.github.sweetzonzi.machinemax.common.registry

import cn.solarmoon.spark_core.animation.ItemAnimatable
import com.mojang.serialization.Codec
import io.github.sweetzonzi.machinemax.MachineMax
import io.github.sweetzonzi.machinemax.common.component.PartAssemblyCacheComponent
import io.github.sweetzonzi.machinemax.common.component.PartAssemblyInfoComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemDisplayContext

object MMDataComponents {
    @JvmStatic
    fun register() {
    }

    /**
     * 保存在部件物品上的物品类型，用于从物品创建部件
     */
    @JvmStatic
    val PART_TYPE = MachineMax.REGISTER.dataComponent<ResourceLocation>()
        .id("part_type")
        .build {
            it
                .persistent(ResourceLocation.CODEC)
                .networkSynchronized(ResourceLocation.STREAM_CODEC)
                .cacheEncoding()
        }

    /**
     * 保存在部件物品上的物品类型，用于从物品创建部件
     */
    @JvmStatic
    val PART_NAME = MachineMax.REGISTER.dataComponent<String>()
        .id("part_name")
        .build {
            it
                .persistent(Codec.STRING)
                .cacheEncoding()
        }

    @JvmStatic
    val CUSTOM_ITEM_MODEL = MachineMax.REGISTER.dataComponent<HashMap<ItemDisplayContext, ItemAnimatable>>()
        .id("custom_item_model")
        .build {
            it
                .persistent(Codec.unit(java.util.HashMap.newHashMap(6)))
                .cacheEncoding()
        }

    @JvmStatic
    val PART_ASSEMBLY_INFO = MachineMax.REGISTER.dataComponent<PartAssemblyInfoComponent>()
        .id("part_assembly_info")
        .build {
            it
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
                .cacheEncoding()
        }
}