package io.github.sweetzonzi.machine_max.common.registry

import cn.solarmoon.spark_core.animation.ItemAnimatable
import cn.solarmoon.spark_core.entry_builder.dataComponentBuilder
import com.mojang.serialization.Codec
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.vehicle.data.AssemblyData
import io.github.sweetzonzi.machine_max.common.vehicle.data.BlueprintData
import io.github.sweetzonzi.machine_max.common.vehicle.data.VehicleData
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemDisplayContext

object MMDataComponents {
    @JvmStatic
    fun register() {
    }

    /**
     * 保存在部件物品上的配方路径
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
     * 保存在蓝图物品上的蓝图路径，用于重建载具
     */
    @JvmStatic
    val BLUEPRINT_DATA = MachineMax.REGISTER.dataComponentType<BlueprintData> {
        id = "blueprint_data"
        factory = dataComponentBuilder {
            persistent(BlueprintData.CODEC)
            networkSynchronized(BlueprintData.STREAM_CODEC)
            cacheEncoding()
        }
    }

    /**
     * 保存在蓝图物品上的预装配结构体模板资源路径，用于重建载具
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


    /**
     * 保存在装配体物品上的装配体资源路径，用于重建载具
     */
    @JvmStatic
    val ASSEMBLY_PATH = MachineMax.REGISTER.dataComponentType<ResourceLocation> {
        id = "assembly_path"
        factory = dataComponentBuilder {
            persistent(ResourceLocation.CODEC)
            networkSynchronized(ResourceLocation.STREAM_CODEC)
            cacheEncoding()
        }
    }

    /**
     * 保存在蓝图物品上的蓝图或结构体资源路径，用于重建载具
     */
    @JvmStatic
    val ASSEMBLY_DATA = MachineMax.REGISTER.dataComponentType<AssemblyData> {
        id = "assembly_data"
        factory = dataComponentBuilder {
            persistent(AssemblyData.CODEC)
            networkSynchronized(AssemblyData.STREAM_CODEC)
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

}