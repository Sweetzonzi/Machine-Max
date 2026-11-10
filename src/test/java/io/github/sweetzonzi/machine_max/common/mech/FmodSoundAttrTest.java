package io.github.sweetzonzi.machine_max.common.mech;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.LauncherSubsystemStaticAttr.LauncherSoundAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.MotorSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.MaterialAttr;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.BusBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 发射器、材料与电机的内容包配置支持 FMOD 引用的解码和往返。 */
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
                { "fmod": { "bank": "test:firing", "event": "event:/launcher/fire" } }
                """)).getOrThrow();
        assertEquals(new FmodSoundAttr(ResourceLocation.parse("test:firing"), "event:/launcher/fire"),
                sounds.fmod().orElseThrow());
        var encoded = codec.encodeStart(JsonOps.INSTANCE, sounds).getOrThrow();
        assertEquals(sounds.fmod(), codec.parse(JsonOps.INSTANCE, encoded).getOrThrow().fmod());
    }

    @Test
    void tireMaterialFmodIsOptionalAndRoundTrips() {
        var codec = MaterialAttr.CODEC;
        var plain = codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        assertTrue(plain.sounds().fmod().isEmpty());

        var tire = codec.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "sounds": { "fmod": { "bank": "test:driving", "event": "event:/tire" } } }
                """)).getOrThrow();
        assertEquals(new FmodSoundAttr(ResourceLocation.parse("test:driving"), "event:/tire"),
                tire.sounds().fmod().orElseThrow());
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
                { "max_power": 1000, "sounds": { "fmod": { "bank": "test:driving", "event": "event:/motor" } } }
                """)).getOrThrow();
        assertEquals(new FmodSoundAttr(ResourceLocation.parse("test:driving"), "event:/motor"),
                motor.sounds.fmod().orElseThrow());
        var encoded = codec.encodeStart(JsonOps.INSTANCE, motor).getOrThrow();
        assertEquals(motor.sounds.fmod(), codec.parse(JsonOps.INSTANCE, encoded).getOrThrow().sounds.fmod());
    }

    @Test
    void invalidStudioPathIsRejected() {
        var result = LauncherSoundAttr.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "fmod": { "bank": "test:firing", "event": "event:/" } }
                """));
        assertTrue(result.error().isPresent());
    }
}
