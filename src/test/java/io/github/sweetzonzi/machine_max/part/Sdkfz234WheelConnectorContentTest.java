package io.github.sweetzonzi.machine_max.part;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sd.Kfz.234 轮座完整性内容契约。
 *
 * <p>这是一辆二战八轮装甲车，参考威胁是 50mm PzGr39（100），但战场上同样会吃
 * 20mm 机关炮（25）与 7.92mm 机枪（8）。轮座应被一发 50mm 击落，但不该被一发
 * 20mm 或 7.92mm 打掉。</p>
 */
class Sdkfz234WheelConnectorContentTest {
    private static final Path PACKS = Path.of("src/main/resources/spark_modules");
    private static final Path SDKFZ = PACKS.resolve("Machine-Max_Sdkfz234/machine_max");
    private static final Path CONNECTORS = SDKFZ.resolve("connectors/sdkfz234");

    /** 车体侧两个车轮挂点定义与车轮侧两个轮毂连接点。 */
    private static final List<String> WHEEL_MOUNTS = List.of(
            "sdkfz234_left_wheel", "sdkfz234_right_wheel",
            "sdkfz234_wheel_hub_left", "sdkfz234_wheel_hub_right");

    @Test
    void everyWheelMountSurvivesAutocannonAndRifleButNotOne50mmRound() throws Exception {
        float ap20 = projectileDamage("Machine-Max_Civilian", "20mm_ap");
        float ap792 = projectileDamage("Machine-Max_German_Basic_Kit", "7_92x57_ap");
        float ap50 = projectileDamage("Machine-Max_German_Basic_Kit", "50mm_pzgr39");

        for (String name : WHEEL_MOUNTS) {
            JsonObject connector = json(CONNECTORS.resolve(name + ".json"));
            float integrity = connector.get("integrity").getAsFloat();
            assertTrue(impact(connector, ap20) < integrity,
                    name + "：一发 20mm 机关炮就会脱轮");
            assertTrue(impact(connector, ap792) < integrity,
                    name + "：一发 7.92mm 机枪就会脱轮");
            assertTrue(impact(connector, ap50) >= integrity,
                    name + "：50mm PzGr39 也打不掉轮座，轮座比车轮更难对付");
        }
    }

    @Test
    void wheelPartUsesTheDedicatedHubDefinitions() throws Exception {
        JsonObject variants = json(SDKFZ.resolve("parts/sdkfz234_wheel.json")).getAsJsonObject("variants");
        assertEquals("machine_max:sdkfz234_wheel_hub_left",
                connectorDefinition(variants, "left", "connector.machine_max.left_wheel_hull"));
        assertEquals("machine_max:sdkfz234_wheel_hub_right",
                connectorDefinition(variants, "right", "connector.machine_max.right_wheel_hull"));
    }

    @Test
    void hullSideAndHubSideShareTheSameCalibration() throws Exception {
        JsonObject reference = json(CONNECTORS.resolve("sdkfz234_left_wheel.json"));
        for (String name : WHEEL_MOUNTS) {
            JsonObject connector = json(CONNECTORS.resolve(name + ".json"));
            assertEquals(reference.get("integrity").getAsFloat(),
                    connector.get("integrity").getAsFloat(), 1e-4f, name);
            assertEquals(reference.get("impact_absorption").getAsFloat(),
                    connector.get("impact_absorption").getAsFloat(), 1e-4f, name);
            assertEquals(reference.get("impact_reduction").getAsFloat(),
                    connector.get("impact_reduction").getAsFloat(), 1e-4f, name);
            assertEquals(reference.get("impact_multiplier").getAsFloat(),
                    connector.get("impact_multiplier").getAsFloat(), 1e-4f, name);
        }
    }

    private static float impact(JsonObject connector, float baseDamage) {
        float reduction = connector.get("impact_reduction").getAsFloat();
        float multiplier = connector.get("impact_multiplier").getAsFloat();
        return Math.max(0f, baseDamage - reduction) * multiplier;
    }

    private static float projectileDamage(String pack, String projectile) throws Exception {
        Path path = PACKS.resolve(pack).resolve("machine_max/projectiles/" + projectile + ".json");
        return json(path).getAsJsonObject("terminal").get("base_damage").getAsFloat();
    }

    private static String connectorDefinition(JsonObject variants, String variant, String connector) {
        return variants.getAsJsonObject(variant)
                .getAsJsonObject("sub_parts")
                .getAsJsonObject("sub_part.machine_max.main")
                .getAsJsonObject("connectors")
                .getAsJsonObject(connector)
                .get("definition").getAsString();
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
