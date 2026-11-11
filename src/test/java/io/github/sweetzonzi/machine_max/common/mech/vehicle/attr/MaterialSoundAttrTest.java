package io.github.sweetzonzi.machine_max.common.mech.vehicle.attr;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 音效覆写须保留未指定的命中音与 FMOD bank。 */
class MaterialSoundAttrTest {
    private MaterialAttr base() {
        return MaterialAttr.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "sounds": {
                    "hit_pen": { "sound_id": "test:penetrate", "range": 30 },
                    "hit_un_pen": { "sound_id": "test:ricochet", "range": 40 },
                    "fmod": { "bank": "test:material_sounds" }
                } }
                """)).getOrThrow();
    }

    private MaterialAttr merge(MaterialAttr base, String json) {
        var overwrite = HitBoxAttr.OverwriteAttr.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        return HitBoxAttr.mergeWithOverwrite(base, overwrite);
    }

    @Test
    void overridingBankPreservesHitSounds() {
        MaterialAttr base = base();
        MaterialAttr merged = merge(base, """
                { "sounds": { "fmod": { "bank": "test:other_sounds" } } }
                """);
        var fmod = merged.sounds().fmod().orElseThrow();
        assertEquals(ResourceLocation.parse("test:other_sounds"), fmod.bank());
        assertEquals(base.sounds().onHitPen(), merged.sounds().onHitPen());
        assertEquals(base.sounds().onHitUnPen(), merged.sounds().onHitUnPen());
    }

    @Test
    void overridingOneHitSoundPreservesOtherHitAndFmod() {
        MaterialAttr base = base();
        MaterialAttr merged = merge(base, """
                { "sounds": { "hit_pen": { "sound_id": "test:other_hit", "range": 50 } } }
                """);
        assertEquals(ResourceLocation.parse("test:other_hit"), merged.sounds().onHitPen().getLocation());
        assertEquals(base.sounds().onHitUnPen(), merged.sounds().onHitUnPen());
        assertEquals(base.sounds().fmod(), merged.sounds().fmod());
        assertEquals(base.sounds(), merge(base, "{ \"sounds\": {} }").sounds());
    }

    @Test
    void fmodBankCanBeAddedWithoutEvents() {
        var merged = merge(MaterialAttr.DEFAULT, """
                { "sounds": { "fmod": { "bank": "test:material_sounds" } } }
                """);
        assertEquals(ResourceLocation.parse("test:material_sounds"), merged.sounds().fmod().orElseThrow().bank());
        assertTrue(HitBoxAttr.OverwriteAttr.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "sounds": { "fmod": {} } }
                """)).error().isPresent());
    }
}
