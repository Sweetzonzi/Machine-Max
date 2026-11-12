package io.github.sweetzonzi.machine_max.common.mech;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.LauncherSubsystemStaticAttr.LauncherSoundAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.MotorSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.EngineSubsystemStaticAttr.EngineSoundAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.GearboxSubsystemStaticAttr.GearBoxSoundAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.GearboxSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CarControllerSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CarControllerSubsystemStaticAttr.HandBrakeSoundAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.MotorbikeControllerSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.TrackControllerSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.MaterialAttr;
import io.github.sweetzonzi.machine_max.testing.ContentFiles;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.BusBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 子系统和材料的内容包配置支持具名 FMOD 事件的解码、校验和往返。 */
class FmodSoundAttrTest {
    @BeforeAll
    static void registerBus() {
        MachineMax.REGISTER.register(BusBuilder.builder().build());
    }

    @Test
    void launcherFmodIsOptionalAndRoundTrips() {
        var codec = LauncherSoundAttr.CODEC;
        assertTrue(codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow().fmod().isEmpty());

        var sounds = codec.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "fmod": { "bank": "test:firing", "fire": "event:/launcher/fire" } }
                """)).getOrThrow();
        assertEquals(ResourceLocation.parse("test:firing"), sounds.fmod().orElseThrow().bank());
        assertEquals("event:/launcher/fire", sounds.fmod().orElseThrow().fire());
        var encoded = codec.encodeStart(JsonOps.INSTANCE, sounds).getOrThrow();
        assertEquals(sounds.fmod(), codec.parse(JsonOps.INSTANCE, encoded).getOrThrow().fmod());
    }

    @Test
    void tireMaterialFmodIsOptionalAndRoundTrips() {
        var codec = MaterialAttr.CODEC;
        var plain = codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        assertTrue(plain.sounds().fmod().isEmpty());

        var tire = codec.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "sounds": { "fmod": { "bank": "test:material_sounds" } } }
                """)).getOrThrow();
        assertEquals(ResourceLocation.parse("test:material_sounds"), tire.sounds().fmod().orElseThrow().bank());
        assertEquals(plain.sounds().onHitPen(), tire.sounds().onHitPen());
        var encoded = codec.encodeStart(JsonOps.INSTANCE, tire).getOrThrow();
        assertEquals(tire.sounds().fmod(), codec.parse(JsonOps.INSTANCE, encoded).getOrThrow().sounds().fmod());
    }

    @Test
    void motorFmodIsOptionalAndRoundTrips() {
        var codec = MotorSubsystemStaticAttr.CODEC.codec();
        var plain = codec.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "max_power": 1000 }
                """)).getOrThrow();
        assertTrue(plain.sounds.fmod().isEmpty());

        var motor = codec.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "max_power": 1000, "sounds": { "fmod": { "bank": "test:driving", "drive": "event:/motor" } } }
                """)).getOrThrow();
        assertEquals(ResourceLocation.parse("test:driving"), motor.sounds.fmod().orElseThrow().bank());
        assertEquals("event:/motor", motor.sounds.fmod().orElseThrow().drive());
        var encoded = codec.encodeStart(JsonOps.INSTANCE, motor).getOrThrow();
        assertEquals(motor.sounds.fmod(), codec.parse(JsonOps.INSTANCE, encoded).getOrThrow().sounds.fmod());
    }

    @Test
    void invalidStudioPathIsRejected() {
        var result = LauncherSoundAttr.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "fmod": { "bank": "test:firing", "fire": "event:/" } }
                """));
        assertTrue(result.error().isPresent());
    }

    @Test
    void engineEventGroupSupportsPartialAndFullDefinitions() {
        var codec = EngineSoundAttr.CODEC;
        var sounds = codec.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "fmod": { "bank": "test:engine", "drive": "event:/drive",
                            "start": "event:/start", "stop": "event:/stop" } }
                """)).getOrThrow();
        var group = sounds.fmod().orElseThrow();
        assertEquals("event:/drive", group.drive().orElseThrow());
        assertEquals("event:/start", group.start().orElseThrow());
        assertEquals("event:/stop", group.stop().orElseThrow());
        var encoded = codec.encodeStart(JsonOps.INSTANCE, sounds).getOrThrow();
        assertEquals(sounds.fmod(), codec.parse(JsonOps.INSTANCE, encoded).getOrThrow().fmod());

        var startOnly = codec.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "fmod": { "bank": "test:engine", "start": "event:/start" } }
                """)).getOrThrow().fmod().orElseThrow();
        assertTrue(startOnly.drive().isEmpty());
        assertTrue(startOnly.stop().isEmpty());
        assertTrue(codec.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "fmod": { "bank": "test:engine" } }
                """)).error().isPresent());
    }

    @Test
    void gearboxShiftIsOptionalAndPreservesClutchSounds() {
        var codec = GearBoxSoundAttr.CODEC;
        var plain = codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        assertTrue(plain.fmod().isEmpty());
        var sounds = codec.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "fmod": { "bank": "test:gearbox", "shift": "event:/shift" } }
                """)).getOrThrow();
        assertEquals(ResourceLocation.parse("test:gearbox"), sounds.fmod().orElseThrow().bank());
        assertEquals("event:/shift", sounds.fmod().orElseThrow().shift());
        assertEquals(plain.clutchIn(), sounds.clutchIn());
        assertEquals(plain.clutchOut(), sounds.clutchOut());
        var encoded = codec.encodeStart(JsonOps.INSTANCE, sounds).getOrThrow();
        assertEquals(sounds, codec.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void handbrakeOperationIsOptionalAndRoundTrips() {
        var codec = HandBrakeSoundAttr.CODEC;
        assertTrue(codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow().fmod().isEmpty());
        var sounds = codec.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "fmod": { "bank": "test:cabin", "handbrake": "event:/lever" } }
                """)).getOrThrow();
        assertEquals(ResourceLocation.parse("test:cabin"), sounds.fmod().orElseThrow().bank());
        assertEquals("event:/lever", sounds.fmod().orElseThrow().handbrake());
        var encoded = codec.encodeStart(JsonOps.INSTANCE, sounds).getOrThrow();
        assertEquals(sounds, codec.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void groundControllerTypesShareTheHandbrakeSoundContract() {
        var input = JsonParser.parseString("""
                { "sounds": { "fmod": { "bank": "test:cabin", "handbrake": "event:/lever" } } }
                """);
        var car = CarControllerSubsystemStaticAttr.CODEC.codec().parse(JsonOps.INSTANCE, input).getOrThrow();
        var bike = MotorbikeControllerSubsystemStaticAttr.CODEC.codec().parse(JsonOps.INSTANCE, input).getOrThrow();
        var track = TrackControllerSubsystemStaticAttr.CODEC.codec().parse(JsonOps.INSTANCE, input).getOrThrow();
        assertEquals(car.sounds.fmod(), bike.sounds.fmod());
        assertEquals(car.sounds.fmod(), track.sounds.fmod());
        var encodedTrack = TrackControllerSubsystemStaticAttr.CODEC.codec().encodeStart(JsonOps.INSTANCE, track).getOrThrow();
        assertEquals(track.sounds.fmod(), TrackControllerSubsystemStaticAttr.CODEC.codec()
                .parse(JsonOps.INSTANCE, encodedTrack).getOrThrow().sounds.fmod());
    }

    @Test
    void operationFmodGroupsRequireTheirNamedEvent() {
        assertTrue(GearBoxSoundAttr.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "fmod": { "bank": "test:gearbox" } }
                """)).error().isPresent());
        assertTrue(HandBrakeSoundAttr.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "fmod": { "bank": "test:cabin" } }
                """)).error().isPresent());
        assertTrue(HandBrakeSoundAttr.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "fmod": { "bank": "test:cabin", "handbrake": "event:/" } }
                """)).error().isPresent());
    }

    @Test
    void configuredOperationSoundsDecodeWithTheirSubsystemCodec() throws Exception {
        try (var paths = Files.walk(ContentFiles.PACKS)) {
            for (var path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".json"))
                    .filter(file -> file.toString().replace('\\', '/').contains("/subsystems/"))
                    .filter(file -> !file.toString().replace('\\', '/').contains("/docs/")).toList()) {
                var input = ContentFiles.json(path);
                if (!input.has("type")) continue;
                var codec = switch (input.get("type").getAsString()) {
                    case "machine_max:gearbox" -> GearboxSubsystemStaticAttr.CODEC.codec();
                    case "machine_max:car_controller" -> CarControllerSubsystemStaticAttr.CODEC.codec();
                    case "machine_max:motorbike_controller" -> MotorbikeControllerSubsystemStaticAttr.CODEC.codec();
                    case "machine_max:track_controller" -> TrackControllerSubsystemStaticAttr.CODEC.codec();
                    default -> null;
                };
                if (codec == null) continue;
                var result = codec.parse(JsonOps.INSTANCE, input);
                assertTrue(result.error().isEmpty(), () -> ContentFiles.describe(path) + ": " + result.error());
            }
        }
    }
}
