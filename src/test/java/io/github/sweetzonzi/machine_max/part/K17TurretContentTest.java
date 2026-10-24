package io.github.sweetzonzi.machine_max.part;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** K17 炮塔的专属驱动型号与炮手观瞄转速内容契约。 */
class K17TurretContentTest {
    private static final Path K17 = Path.of("src/main/resources/spark_modules/Machine-Max_K17/machine_max");
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
                subsystems.getAsJsonObject("commander_optics").get("definition").getAsString());
        assertEquals("machine_max:k17_gunner_sight",
                subsystems.getAsJsonObject("gunner_optics").get("definition").getAsString());
        assertFalse(Files.exists(SUBSYSTEMS.resolve("k17_commander_sight_preview.json")));
        assertFalse(Files.exists(SUBSYSTEMS.resolve("k17_gunner_sight_preview.json")));
    }
}
