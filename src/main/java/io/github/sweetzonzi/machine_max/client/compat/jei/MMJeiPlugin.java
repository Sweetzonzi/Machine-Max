package io.github.sweetzonzi.machine_max.client.compat.jei;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.registry.MMItems;
import io.github.sweetzonzi.machine_max.common.vehicle.data.AssemblyData;
import io.github.sweetzonzi.machine_max.common.vehicle.data.BlueprintData;
import io.github.sweetzonzi.machine_max.common.vehicle.data.VehicleData;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

@JeiPlugin
public class MMJeiPlugin implements IModPlugin {
    // JEI 插件唯一 ID，仅在安装 JEI 时由 JEI 侧扫描并加载。
    private static final ResourceLocation PLUGIN_UID =
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "jei_plugin");

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // 为“同物品不同 DataComponent”的场景注册子类型解释器，避免 JEI 把所有变体视为同一物品。
        registration.registerSubtypeInterpreter(
                MMItems.getPART_ITEM().get(),
                interpreter(MMJeiPlugin::buildPartSubtypeKey)
        );
        registration.registerSubtypeInterpreter(
                MMItems.getFABRICATING_BLUEPRINT().get(),
                interpreter(MMJeiPlugin::buildFabricatingBlueprintSubtypeKey)
        );
        registration.registerSubtypeInterpreter(
                MMItems.getASSEMBLY_ITEM().get(),
                interpreter(MMJeiPlugin::buildAssemblySubtypeKey)
        );
        registration.registerSubtypeInterpreter(
                MMItems.getVEHICLE_BLUEPRINT().get(),
                interpreter(MMJeiPlugin::buildVehicleBlueprintSubtypeKey)
        );
    }

    private static Object buildPartSubtypeKey(ItemStack stack, UidContext context) {
        StringBuilder key = new StringBuilder(64);
        ResourceLocation partType = stack.get(MMDataComponents.getPART_TYPE());
        ResourceLocation recipeType = stack.get(MMDataComponents.getRECIPE_TYPE());

        // 配方匹配优先按 part_type 聚合，缺失时回退 recipe_type。
        appendLocation(key, "part_type", partType);
        if (context == UidContext.Ingredient || partType == null) {
            appendLocation(key, "recipe_type", recipeType);
        }
        return finishKey(key);
    }

    private static Object buildFabricatingBlueprintSubtypeKey(ItemStack stack, UidContext context) {
        StringBuilder key = new StringBuilder(96);
        ResourceLocation partType = stack.get(MMDataComponents.getPART_TYPE());
        ResourceLocation recipeType = stack.get(MMDataComponents.getRECIPE_TYPE());

        // 蓝图与部件相同：优先按 part_type 匹配，避免一个蓝图命中所有部件配方。
        appendLocation(key, "part_type", partType);
        if (context == UidContext.Ingredient || partType == null) {
            appendLocation(key, "recipe_type", recipeType);
        }
        // 仅在物品列表上下文细分研发等级，防止配方上下文过度碎片化。
        if (context == UidContext.Ingredient) {
            appendInt(key, "research_level", stack.get(MMDataComponents.getRESEARCH_LEVEL()));
        }
        return finishKey(key);
    }

    private static Object buildAssemblySubtypeKey(ItemStack stack, UidContext context) {
        StringBuilder key = new StringBuilder(96);
        appendLocation(key, "assembly_path", stack.get(MMDataComponents.getASSEMBLY_PATH()));

        // assembly_data 主要使用 template 作为身份锚点，避免把整结构体全部纳入 key。
        AssemblyData assemblyData = stack.get(MMDataComponents.getASSEMBLY_DATA());
        if (assemblyData != null) {
            appendLocation(key, "assembly_template", assemblyData.getTemplate());
        }

        appendVehicleDataIfNeeded(key, stack, context);
        return finishKey(key);
    }

    private static Object buildVehicleBlueprintSubtypeKey(ItemStack stack, UidContext context) {
        StringBuilder key = new StringBuilder(96);
        appendLocation(key, "blueprint_path", stack.get(MMDataComponents.getVEHICLE_BLUEPRINT_PATH()));

        // blueprint_data 同理只取 template，保证区分度和稳定性。
        BlueprintData blueprintData = stack.get(MMDataComponents.getBLUEPRINT_DATA());
        if (blueprintData != null) {
            appendLocation(key, "blueprint_template", blueprintData.getTemplate());
        }

        appendVehicleDataIfNeeded(key, stack, context);
        return finishKey(key);
    }

    private static void appendVehicleDataIfNeeded(StringBuilder key, ItemStack stack, UidContext context) {
        // VehicleData 常包含大量动态信息，仅在物品列表中以 UUID 做最小区分。
        if (context != UidContext.Ingredient) {
            return;
        }
        VehicleData vehicleData = stack.get(MMDataComponents.getVEHICLE_DATA());
        if (vehicleData != null) {
            appendString(key, "vehicle_uuid", vehicleData.getUuid());
        }
    }

    private static void appendLocation(StringBuilder key, String name, ResourceLocation value) {
        if (value != null) {
            appendString(key, name, value.toString());
        }
    }

    private static void appendInt(StringBuilder key, String name, Integer value) {
        if (value != null) {
            appendString(key, name, Integer.toString(value));
        }
    }

    private static void appendString(StringBuilder key, String name, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (!key.isEmpty()) {
            key.append('|');
        }
        key.append(name).append('=').append(value);
    }

    private static Object finishKey(StringBuilder key) {
        return key.isEmpty() ? null : key.toString();
    }

    private interface SubtypeKeyBuilder {
        Object build(ItemStack stack, UidContext context);
    }

    private static ISubtypeInterpreter<ItemStack> interpreter(SubtypeKeyBuilder builder) {
        return new ISubtypeInterpreter<>() {
            @Override
            public Object getSubtypeData(ItemStack ingredient, UidContext context) {
                return builder.build(ingredient, context);
            }

            @Override
            @Deprecated(since = "19.9.0")
            public @NotNull String getLegacyStringSubtypeInfo(ItemStack ingredient, UidContext context) {
                Object data = builder.build(ingredient, context);
                return data == null ? "" : data.toString();
            }
        };
    }
}
