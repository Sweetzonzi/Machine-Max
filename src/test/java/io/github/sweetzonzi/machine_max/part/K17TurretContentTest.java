package io.github.sweetzonzi.machine_max.part;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sweetzonzi.machine_max.external.CommentRemover;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * 四根导弹管各自有一个命中框，接在同名发射器子系统上。
     *
     * <p>命中框的键就是模型骨骼名，因此这里的期望值同时钉住三件事：模型里有这根骨骼（运行期
     * 建碰撞形状时按名字找）、骨骼属于该侧发射架子树、子系统与定位点在这个命中框上闭合。
     * 四个穿透区 id 必须互不相同，否则一发弹在同一次攻击里只会结算其中一个。</p>
     */
    @Test void eachAtgmTubeCarriesItsOwnHitBox() throws Exception {
        JsonObject subParts = json(K17.resolve("parts/k17_turret.json"))
                .getAsJsonObject("variants").getAsJsonObject("sub_parts");
        JsonObject geometry = json(K17.resolve("models/part/k17.geo.json"))
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        Map<String, JsonObject> bones = new LinkedHashMap<>();
        Set<String> locators = new HashSet<>();
        for (JsonElement element : geometry.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            bones.put(bone.get("name").getAsString(), bone);
            if (bone.has("locators")) locators.addAll(bone.getAsJsonObject("locators").keySet());
        }

        record Tube(String subPart, String subsystem, String bone, String parent,
                    String zoneId, String locator) {
        }
        List<Tube> tubes = List.of(
                new Tube("sub_part.machine_max.left_atgm", "subsystem.machine_max.left_atgm_tube_1",
                        "LeftAtgm1HitBox", "ATGM_Left", "left_atgm_tube_1", "left_atgm1_launch_point"),
                new Tube("sub_part.machine_max.left_atgm", "subsystem.machine_max.left_atgm_tube_2",
                        "LeftAtgm2HitBox", "ATGM_Left", "left_atgm_tube_2", "left_atgm2_launch_point"),
                new Tube("sub_part.machine_max.right_atgm", "subsystem.machine_max.right_atgm_tube_1",
                        "RightAtgm1HitBox", "ATGM_Right", "right_atgm_tube_1", "right_atgm1_launch_point"),
                new Tube("sub_part.machine_max.right_atgm", "subsystem.machine_max.right_atgm_tube_2",
                        "RightAtgm2HitBox", "ATGM_Right", "right_atgm_tube_2", "right_atgm2_launch_point"));

        Set<String> zoneIds = new HashSet<>();
        for (Tube tube : tubes) {
            JsonObject subPart = subParts.getAsJsonObject(tube.subPart());
            JsonObject subsystem = subPart.getAsJsonObject("subsystems").getAsJsonObject(tube.subsystem());
            assertEquals("machine_max:atgm_tube_launcher", subsystem.get("definition").getAsString(),
                    "每根管各自一个发射器：" + tube.subsystem());
            assertEquals(tube.locator(), subsystem.get("locator").getAsString(), "发射点必须落在该管上");
            assertTrue(locators.contains(tube.locator()), "定位点必须存在于模型里：" + tube.locator());

            JsonObject hitBox = subPart.getAsJsonObject("hit_boxes").getAsJsonObject(tube.bone());
            assertNotNull(hitBox, "命中框的键是模型骨骼名：" + tube.bone());
            assertEquals("box", hitBox.get("type").getAsString());
            assertEquals(tube.subsystem(), hitBox.get("subsystem").getAsString(), "命中框必须接到该管的发射器上");
            assertEquals(tube.zoneId(), hitBox.get("id").getAsString());
            assertNotEquals("part", tube.zoneId(), "发射管要自成一个穿透区，才能与外部装甲分别结算");
            assertTrue(zoneIds.add(tube.zoneId()), "四根管的穿透区 id 必须互不相同：" + tube.zoneId());
            assertTrue(hitBox.get("condition").getAsString()
                            .contains("local.subsystem_destroyed('" + tube.subsystem() + "')"),
                    "发射管被打掉后该命中框不得继续吸收伤害：" + tube.bone());

            JsonObject bone = bones.get(tube.bone());
            assertNotNull(bone, "模型里必须有这根骨骼：" + tube.bone());
            assertEquals(tube.parent(), bone.get("parent").getAsString(), "管体骨骼必须是该侧发射架的分支");
            assertEquals(1, bone.getAsJsonArray("cubes").size(),
                    "每根管只放一个方块，碰撞形状才是一个盒子：" + tube.bone());
        }
    }
}
