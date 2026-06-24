package io.github.sweetzonzi.machine_max.common.registry;

import cn.solarmoon.spark_core.animation.IAnimatable;
import com.mojang.serialization.Codec;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.AssemblyData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.function.Supplier;

/**
 * 物品数据组件注册（传统 DeferredRegister 方式）
 */
public class MMDataComponents {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MachineMax.MOD_ID);

    /** 保存在部件物品上的配方路径 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> RECIPE_TYPE = COMPONENTS.register(
            "recipe_type",
            () -> DataComponentType.<ResourceLocation>builder()
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC)
                    .cacheEncoding()
                    .build()
    );

    /** 蓝图设计者 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> DESIGNER = COMPONENTS.register(
            "designer",
            () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .cacheEncoding()
                    .build()
    );

    /** 保存在部件物品上的部件类型，用于从物品创建部件 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> PART_TYPE = COMPONENTS.register(
            "part_type",
            () -> DataComponentType.<ResourceLocation>builder()
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC)
                    .cacheEncoding()
                    .build()
    );

    /** 保存在测试物品上的投射物类型路径 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> PROJECTILE_TYPE = COMPONENTS.register(
            "projectile_type",
            () -> DataComponentType.<ResourceLocation>builder()
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC)
                    .cacheEncoding()
                    .build()
    );

    /** 保存在蓝图物品上的蓝图资源路径，用于重建载具 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> VEHICLE_BLUEPRINT_PATH = COMPONENTS.register(
            "vehicle_blueprint_path",
            () -> DataComponentType.<ResourceLocation>builder()
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC)
                    .cacheEncoding()
                    .build()
    );

    /** 保存在蓝图物品上的蓝图数据，用于重建载具 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlueprintData>> BLUEPRINT_DATA = COMPONENTS.register(
            "blueprint_data",
            () -> DataComponentType.<BlueprintData>builder()
                    .persistent(BlueprintData.CODEC)
                    .networkSynchronized(BlueprintData.STREAM_CODEC)
                    .cacheEncoding()
                    .build()
    );

    /** 保存在蓝图物品上的预装配结构体模板，用于重建载具 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<VehicleData>> VEHICLE_DATA = COMPONENTS.register(
            "vehicle_data",
            () -> DataComponentType.<VehicleData>builder()
                    .persistent(VehicleData.CODEC)
                    .networkSynchronized(VehicleData.STREAM_CODEC)
                    .cacheEncoding()
                    .build()
    );

    /** 保存在装配体物品上的装配体资源路径，用于重建载具 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> ASSEMBLY_PATH = COMPONENTS.register(
            "assembly_path",
            () -> DataComponentType.<ResourceLocation>builder()
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC)
                    .cacheEncoding()
                    .build()
    );

    /** 保存在物品上的装配体数据，用于重建载具 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AssemblyData>> ASSEMBLY_DATA = COMPONENTS.register(
            "assembly_data",
            () -> DataComponentType.<AssemblyData>builder()
                    .persistent(AssemblyData.CODEC)
                    .networkSynchronized(AssemblyData.STREAM_CODEC)
                    .cacheEncoding()
                    .build()
    );

    /** 自定义物品模型（用于喷漆） */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<HashMap<ItemDisplayContext, IAnimatable<?>>>> CUSTOM_ITEM_MODEL = COMPONENTS.register(
            "custom_item_model",
            () -> DataComponentType.builder()
                    .persistent((Codec) Codec.unit(new HashMap<>(6)))
                    .cacheEncoding()
                    .build()
    );

    // ──────── 静态 getter（兼容旧 Kotlin @JvmStatic 调用方）────────

    public static DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> getRECIPE_TYPE() { return RECIPE_TYPE; }
    public static DeferredHolder<DataComponentType<?>, DataComponentType<String>> getDESIGNER() { return DESIGNER; }
    public static DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> getPART_TYPE() { return PART_TYPE; }
    public static DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> getPROJECTILE_TYPE() { return PROJECTILE_TYPE; }
    public static DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> getVEHICLE_BLUEPRINT_PATH() { return VEHICLE_BLUEPRINT_PATH; }
    public static DeferredHolder<DataComponentType<?>, DataComponentType<BlueprintData>> getBLUEPRINT_DATA() { return BLUEPRINT_DATA; }
    public static DeferredHolder<DataComponentType<?>, DataComponentType<VehicleData>> getVEHICLE_DATA() { return VEHICLE_DATA; }
    public static DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> getASSEMBLY_PATH() { return ASSEMBLY_PATH; }
    public static DeferredHolder<DataComponentType<?>, DataComponentType<AssemblyData>> getASSEMBLY_DATA() { return ASSEMBLY_DATA; }
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static DeferredHolder<DataComponentType<?>, DataComponentType<HashMap<ItemDisplayContext, IAnimatable<?>>>> getCUSTOM_ITEM_MODEL() { return CUSTOM_ITEM_MODEL; }

    /**
     * 将数据组件注册到事件总线
     */
    public static void register(net.neoforged.bus.api.IEventBus bus) {
        COMPONENTS.register(bus);
    }
}
