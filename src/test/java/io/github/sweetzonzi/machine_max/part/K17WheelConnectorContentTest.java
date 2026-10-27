package io.github.sweetzonzi.machine_max.part;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * K17 轮座完整性内容契约。
 *
 * <p>K17 的参考威胁是 30mm 机关炮（AP 45 / HE 55）。轮座必须扛得住单发 30mm——
 * 直击与车体侧近失都不能一发脱轮——但仍应被 50mm 级弹药一发击落，
 * 否则轮座会比车轮本体更难对付，破坏威胁层级。</p>
 */
class K17WheelConnectorContentTest {
    private static final Path PACKS = Path.of("src/main/resources/spark_modules");
    private static final Path K17 = PACKS.resolve("Machine-Max_K17/machine_max");
    private static final Path CONNECTORS = K17.resolve("connectors/k17");

    /** 车体侧四个车轮挂点与车轮侧两个轮毂连接点。 */
    private static final List<String> WHEEL_MOUNTS = List.of(
            "k17_left_front_wheel", "k17_right_front_wheel",
            "k17_left_back_wheel", "k17_right_back_wheel",
            "k17_wheel_hub_left", "k17_wheel_hub_right");

    @Test
    void everyWheelMountSurvivesOne30mmRoundButNotOne50mmRound() throws Exception {
        float ap30 = projectileDamage("Machine-Max_Soviet_Basic_Kit", "30mm_ap");
        float he30 = projectileDamage("Machine-Max_Soviet_Basic_Kit", "30mm_he");
        float ap50 = projectileDamage("Machine-Max_German_Basic_Kit", "50mm_pzgr39");

        for (String name : WHEEL_MOUNTS) {
            JsonObject connector = json(CONNECTORS.resolve(name + ".json"));
            float integrity = connector.get("integrity").getAsFloat();
            assertTrue(impact(connector, ap30) < integrity,
                    name + "：一发 30mm AP 就会脱轮");
            assertTrue(impact(connector, he30) < integrity,
                    name + "：一发 30mm HE 就会脱轮");
            assertTrue(impact(connector, ap50) >= integrity,
                    name + "：50mm AP 也打不掉轮座，轮座比车轮更难对付");
        }
    }

    @Test
    void wheelPartUsesTheDedicatedHubDefinitions() throws Exception {
        JsonObject variants = json(K17.resolve("parts/k17_wheel.json")).getAsJsonObject("variants");
        assertEquals("machine_max:k17_wheel_hub_left",
                connectorDefinition(variants, "left", "connector.machine_max.left_wheel_hull"));
        assertEquals("machine_max:k17_wheel_hub_right",
                connectorDefinition(variants, "right", "connector.machine_max.right_wheel_hull"));
    }

    @Test
    void hullSideAndHubSideShareTheSameCalibration() throws Exception {
        JsonObject reference = json(CONNECTORS.resolve("k17_left_front_wheel.json"));
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
