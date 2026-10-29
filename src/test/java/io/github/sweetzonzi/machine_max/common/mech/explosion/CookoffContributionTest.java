package io.github.sweetzonzi.machine_max.common.mech.explosion;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CookoffContribution} 的内容包契约：三个字段都可省略且缺省为零，
 * 负数与 NaN／Infinity 在解码期和直接构造时都被拒绝。
 */
class CookoffContributionTest {

    private static DataResult<CookoffContribution> parse(String json) {
        JsonElement element = JsonParser.parseString(json);
        return CookoffContribution.CODEC.parse(JsonOps.INSTANCE, element);
    }

    private static CookoffContribution decode(String json) {
        return parse(json).getOrThrow();
    }

    @Test
    void emptyObjectDefaultsAllThreeFieldsToZero() {
        assertEquals(CookoffContribution.ZERO, decode("{}"));
    }

    @Test
    void omittedFieldsDefaultToZeroAndExplicitFieldsAreKept() {
        assertEquals(new CookoffContribution(0.0, 2.5, 0.0), decode("{\"damage\": 2.5}"));
        assertEquals(new CookoffContribution(1.0, 0.0, 0.0), decode("{\"penetration\": 1.0}"));
        assertEquals(new CookoffContribution(0.0, 0.0, 3.0), decode("{\"impulse\": 3.0}"));
        assertEquals(new CookoffContribution(12.5, 0.75, 4.0), decode("""
                { "penetration": 12.5, "damage": 0.75, "impulse": 4.0 }
                """));
    }

    @Test
    void negativeFieldsAreRejected() {
        assertTrue(parse("{\"penetration\": -0.5}").error().isPresent());
        assertTrue(parse("{\"damage\": -1.0}").error().isPresent());
        assertTrue(parse("{\"impulse\": -0.0001}").error().isPresent());
    }

    @Test
    void nonFiniteFieldsAreRejected() {
        // Codec.DOUBLE 接受 NaN／±Infinity 的字符串写法；validate 在解码期把它们按内容错误拒绝
        for (String field : new String[]{"penetration", "damage", "impulse"}) {
            assertTrue(parse("{\"" + field + "\": \"NaN\"}").error().isPresent(), field);
            assertTrue(parse("{\"" + field + "\": \"Infinity\"}").error().isPresent(), field);
            assertTrue(parse("{\"" + field + "\": \"-Infinity\"}").error().isPresent(), field);
        }
    }

    @Test
    void constructorEnforcesFiniteNonNegative() {
        assertThrows(IllegalArgumentException.class, () -> new CookoffContribution(-1.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new CookoffContribution(0.0, -0.5, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new CookoffContribution(0.0, 0.0, -0.5));
        assertThrows(IllegalArgumentException.class, () -> new CookoffContribution(Double.NaN, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new CookoffContribution(0.0, Double.POSITIVE_INFINITY, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new CookoffContribution(0.0, 0.0, Double.NEGATIVE_INFINITY));
    }

    @Test
    void zeroIsOnlyAllThreeChannelsZero() {
        assertTrue(CookoffContribution.ZERO.isZero());
        assertTrue(new CookoffContribution(0.0, 0.0, 0.0).isZero());
        assertFalse(new CookoffContribution(0.0001, 0.0, 0.0).isZero());
        assertFalse(new CookoffContribution(0.0, 0.0001, 0.0).isZero());
        assertFalse(new CookoffContribution(0.0, 0.0, 0.0001).isZero());
    }

    @Test
    void encodeDecodeRoundTrip() {
        CookoffContribution contribution = new CookoffContribution(30.0, 0.25, 9.0);
        JsonElement json = CookoffContribution.CODEC
                .encodeStart(JsonOps.INSTANCE, contribution).getOrThrow();
        assertEquals(contribution, CookoffContribution.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        // 全零贡献编码后再解码仍是零贡献
        JsonElement zeroJson = CookoffContribution.CODEC
                .encodeStart(JsonOps.INSTANCE, CookoffContribution.ZERO).getOrThrow();
        assertEquals(CookoffContribution.ZERO, CookoffContribution.CODEC.parse(JsonOps.INSTANCE, zeroJson).getOrThrow());
    }
}
