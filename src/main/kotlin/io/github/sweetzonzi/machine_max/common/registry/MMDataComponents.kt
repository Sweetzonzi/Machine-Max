package io.github.sweetzonzi.machine_max.common.registry

import cn.solarmoon.spark_core.animation.ItemAnimatable
import cn.solarmoon.spark_core.entry_builder.dataComponentBuilder
import com.mojang.serialization.Codec
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.component.PartAssemblyCacheComponent
import io.github.sweetzonzi.machine_max.common.component.PartAssemblyInfoComponent
import io.github.sweetzonzi.machine_max.common.vehicle.data.VehicleData
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemDisplayContext

object MMDataComponents {
    @JvmStatic
    fun register() {
    }

    /**
     * 保存在部件物品上的配方类型
     */
    @JvmStatic
    val RECIPE_TYPE = MachineMax.REGISTER.dataComponentType<ResourceLocation> {
        id = "recipe_type"
        factory = dataComponentBuilder {
            persistent(ResourceLocation.CODEC)
            networkSynchronized(ResourceLocation.STREAM_CODEC)
            cacheEncoding()
        }
    }

    /**
     * 保存在部件物品上的部件类型，用于从物品创建部件
     */
    @JvmStatic
    val PART_TYPE = MachineMax.REGISTER.dataComponentType<ResourceLocation> {
        id = "part_type"
        factory = dataComponentBuilder {
            persistent(ResourceLocation.CODEC)
            networkSynchronized(ResourceLocation.STREAM_CODEC)
            cacheEncoding()
        }
    }

    /**
     * 保存在蓝图物品上的蓝图资源路径，用于重建载具
     */
    @JvmStatic
    val VEHICLE_BLUEPRINT_PATH = MachineMax.REGISTER.dataComponentType<ResourceLocation> {
        id = "vehicle_blueprint_path"
        factory = dataComponentBuilder {
            persistent(ResourceLocation.CODEC)
            networkSynchronized(ResourceLocation.STREAM_CODEC)
            cacheEncoding()
        }
    }

    /**
     * 保存在蓝图物品上的蓝图资源路径，用于重建载具
     */
    @JvmStatic
    val VEHICLE_DATA = MachineMax.REGISTER.dataComponentType<VehicleData> {
        id = "vehicle_data"
        factory = dataComponentBuilder {
            persistent(VehicleData.CODEC)
            networkSynchronized(VehicleData.STREAM_CODEC)
            cacheEncoding()
        }
    }

    @JvmStatic
    val CUSTOM_ITEM_MODEL = MachineMax.REGISTER.dataComponentType<HashMap<ItemDisplayContext, ItemAnimatable>> {
        id = "custom_item_model"
        factory = dataComponentBuilder {
            persistent(Codec.unit(java.util.HashMap.newHashMap(6)))
            cacheEncoding()
        }
    }

    @JvmStatic
    val PART_ASSEMBLY_INFO = MachineMax.REGISTER.dataComponentType<PartAssemblyInfoComponent> {
        id = "part_assembly_info"
        factory = dataComponentBuilder {
            networkSynchronized(PartAssemblyInfoComponent.STREAM_CODEC)
            cacheEncoding()
        }
    }

    /**
     * 用于暂存部件变体与部件对接口的迭代器
     * 仅存在于服务端，仅应在服务端被使用
     */
    @JvmStatic
    val PART_ASSEMBLY_CACHE = MachineMax.REGISTER.dataComponentType<PartAssemblyCacheComponent> {
        id = "part_assembly_cache"
        factory = dataComponentBuilder {
            persistent(PartAssemblyCacheComponent.CODEC)
            cacheEncoding()
        }
    }
}