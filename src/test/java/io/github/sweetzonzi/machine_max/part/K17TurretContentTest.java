package io.github.sweetzonzi.machine_max.part;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sweetzonzi.machine_max.external.CommentRemover;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** K17 炮塔的专属驱动型号、炮手观瞄转速与导弹供给内容契约。 */
class K17TurretContentTest {
    private static final Path K17 = Path.of("src/main/resources/spark_modules/Machine-Max_K17/machine_max");
    private static final Path SOVIET_KIT =
            Path.of("src/main/resources/spark_modules/Machine-Max_Soviet_Basic_Kit/machine_max");
    private static final Path SUBSYSTEMS = K17.resolve("subsystems/k17");

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    @Test void dedicatedDriversAndGunnerSightSpeedsAgree() throws Exception {
        JsonObject turret = json(K17.resolve("parts/k17_turret.json"));
        JsonObject subsystems = turret.getAsJsonObject("variants")
                .getAsJsonObject("sub_parts")
                .getAsJsonObject("sub_part.machine_max.main")
                .getAsJsonObject("subsystems");

        assertEquals("machine_max:k17_azimuth_driver",
                subsystems.getAsJsonObject("subsystem.machine_max.azimuth_driver")
                        .get("definition").getAsString());
        assertEquals("machine_max:k17_elevation_driver",
                subsystems.getAsJsonObject("subsystem.machine_max.elevation_driver")
                        .get("definition").getAsString());

        float yawSpeed = json(SUBSYSTEMS.resolve("k17_azimuth_driver.json"))
                .getAsJsonObject("yaw").get("max_speed").getAsFloat();
        float pitchSpeed = json(SUBSYSTEMS.resolve("k17_elevation_driver.json"))
                .getAsJsonObject("pitch").get("max_speed").getAsFloat();
        JsonObject gunner = json(SUBSYSTEMS.resolve("k17_gunner_sight.json"));
        assertEquals(yawSpeed, gunner.get("yaw_speed").getAsFloat(), 1e-4f);
        assertEquals(pitchSpeed, gunner.get("pitch_speed").getAsFloat(), 1e-4f);

        assertEquals("machine_max:k17_commander_sight",
                subsystems.getAsJsonObject("subsystem.machine_max.commander_optics").get("definition").getAsString());
        assertEquals("machine_max:k17_gunner_sight",
                subsystems.getAsJsonObject("subsystem.machine_max.gunner_optics").get("definition").getAsString());
        assertFalse(Files.exists(SUBSYSTEMS.resolve("k17_commander_sight_preview.json")));
        assertFalse(Files.exists(SUBSYSTEMS.resolve("k17_gunner_sight_preview.json")));
    }

    @Test void singleAtgmLoaderFeedsFourTubes() throws Exception {
        JsonObject subParts = json(K17.resolve("parts/k17_turret.json"))
                .getAsJsonObject("variants").getAsJsonObject("sub_parts");
        JsonObject mainSubsystems = subParts.getAsJsonObject("sub_part.machine_max.main").getAsJsonObject("subsystems");

        assertEquals("machine_max:atgm_regen_loader",
                mainSubsystems.getAsJsonObject("subsystem.machine_max.atgm_ammo_loader")
                        .get("definition").getAsString());
        for (String name : mainSubsystems.keySet()) {
            if (name.startsWith("subsystem.machine_max.atgm_ammo_loader"))
                assertEquals("subsystem.machine_max.atgm_ammo_loader", name);
        }

        int tubes = 0;
        for (String subPartName : subParts.keySet()) {
            JsonObject subsystems = subParts.getAsJsonObject(subPartName).getAsJsonObject("subsystems");
            if (subsystems == null) continue;
            for (String name : subsystems.keySet()) {
                if (name.contains("_atgm_tube_")) tubes++;
            }
        }
        assertEquals(4, tubes, "四根导弹管共用这一台装弹机");

        JsonObject definition = JsonParser.parseString(CommentRemover.removeComments(
                Files.readString(SOVIET_KIT.resolve("subsystems/weapons/atgm_regen_loader.json"))))
                .getAsJsonObject();
        JsonObject feed = definition.getAsJsonObject("feed");
        assertEquals(10f, feed.get("reload_time").getAsFloat(), 1e-6f);
        assertTrue(feed.get("reload_round_by_round").getAsBoolean());
        assertTrue(feed.get("can_supply_multiple").getAsBoolean());
        JsonObject magazine = definition.getAsJsonObject("magazine");
        assertEquals(4, magazine.get("capacity").getAsInt());
        assertEquals(2f, magazine.get("regen_per_minute").getAsFloat(), 1e-6f);
        assertTrue(magazine.get("regen_round_by_round").getAsBoolean());
    }
}
