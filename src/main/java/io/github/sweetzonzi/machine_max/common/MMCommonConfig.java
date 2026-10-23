package io.github.sweetzonzi.machine_max.common;

import cn.solarmoon.spark_core.event.SparkContentPackAutoPackEvent;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sweetzonzi.machine_max.MachineMax;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Set;

/**
 * {@code auto_override_official_pack} 的开关：关掉后，磁盘上已有的内容包副本不再被 jar 内版本覆盖，
 * 玩家改过的包因此能保留下来。
 *
 * <p>开关只覆盖叶子包（民用包与车包）。{@code machine_max:builtin} 与两个 {@code *_basic_kit}
 * 始终跟随 jar：它们是依赖链的下层，其它包硬依赖它们的定义；留在旧版本只会让缺定义、型号对不上的
 * 报错更难查，而玩家通常也不会去改货架件与共用武器。
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class MMCommonConfig {
    public static final ModConfigSpec COMMON_SPEC;
    private static final ModConfigSpec.BooleanValue AUTO_OVERRIDE_FIRST_PARTY_PACKS;

    /** 可被玩家改动、因此允许关掉覆盖的第一方内容包。 */
    private static final Set<String> OVERRIDABLE_PACK_IDS = Set.of(
            "machine_max:civilian",
            "machine_max:2s38",
            "machine_max:k17",
            "machine_max:mtlb",
            "machine_max:sdkfz234"
    );

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        AUTO_OVERRIDE_FIRST_PARTY_PACKS = builder
                .comment("Whether to auto-pack and override first-party content packs (builtin and basic kits always follow the jar) on startup/reload.")
                .define("auto_override_official_pack", true);

        COMMON_SPEC = builder.build();
    }

    public static boolean autoOverrideFirstPartyPacks() {
        return AUTO_OVERRIDE_FIRST_PARTY_PACKS.get();
    }

    @SubscribeEvent
    public static void onSparkContentPackAutoPack(SparkContentPackAutoPackEvent.Pre event) {
        if (!MachineMax.MOD_ID.equals(event.getModId())) return;
        if (!isOverridableFirstPartyPack(event.getMetaJson())) return;
        if (!MMCommonConfig.autoOverrideFirstPartyPacks()) {
            event.setShouldPack(false);
        }
    }

    private static boolean isOverridableFirstPartyPack(JsonObject metaJson) {
        if (metaJson == null) return false;
        JsonElement idElement = metaJson.get("id");
        return idElement != null && idElement.isJsonPrimitive()
                && OVERRIDABLE_PACK_IDS.contains(idElement.getAsString());
    }
}
