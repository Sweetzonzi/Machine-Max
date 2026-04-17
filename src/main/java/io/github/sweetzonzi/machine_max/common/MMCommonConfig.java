package io.github.sweetzonzi.machine_max.common;

import cn.solarmoon.spark_core.event.SparkContentPackAutoPackEvent;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sweetzonzi.machine_max.MachineMax;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class MMCommonConfig {
    public static final ModConfigSpec COMMON_SPEC;
    private static final ModConfigSpec.BooleanValue AUTO_OVERRIDE_OFFICIAL_PACK;
    private static final String OFFICIAL_PACK_ID = "machine_max:official";

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        AUTO_OVERRIDE_OFFICIAL_PACK = builder
                .comment("Whether to auto-pack and override machine_max:official content pack on startup/reload.")
                .define("auto_override_official_pack", true);

        COMMON_SPEC = builder.build();
    }

    public static boolean autoOverrideOfficialPack() {
        return AUTO_OVERRIDE_OFFICIAL_PACK.get();
    }

    @SubscribeEvent
    public static void onSparkContentPackAutoPack(SparkContentPackAutoPackEvent.Pre event) {
        if (!MachineMax.MOD_ID.equals(event.getModId())) return;
        if (!isOfficialPack(event.getMetaJson())) return;
        if (!MMCommonConfig.autoOverrideOfficialPack()) {
            event.setShouldPack(false);
        }
    }

    private static boolean isOfficialPack(JsonObject metaJson) {
        if (metaJson == null) return false;
        JsonElement idElement = metaJson.get("id");
        return idElement != null && idElement.isJsonPrimitive() && OFFICIAL_PACK_ID.equals(idElement.getAsString());
    }
}
