package io.github.sweetzonzi.machine_max.common.mech.projectile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType.ExternalProperties;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType.ExternalProperties.ThrustSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code external.thrust} 的内容包契约：形态是<b>段数组</b>，不是单对象。
 * <p>
 * 这一层只保证「写进来的段都在」，不保证「每一段都被施加」——当前实现只施加首段，
 * 截断发生在 {@code BallisticProjectileType#getThrustProfile()}，不在 codec。把两者分开
 * 断言是有意的：数据形态是给内容作者的稳定契约，施加范围是实现进度。
 */
class ProjectileThrustCodecTest {

    /** 只带必填字段的最小外弹道块，便于在不复刻整份 JSON 的前提下测 thrust */
    private static final String MINIMAL = "\"mass\": 26.0, \"base_velocity\": 50";

    private static DataResult<ExternalProperties> parse(String thrustField) {
        String json = "{" + MINIMAL + (thrustField == null ? "" : ", " + thrustField) + "}";
        JsonElement element = JsonParser.parseString(json);
        return ExternalProperties.CODEC.parse(JsonOps.INSTANCE, element);
    }

    private static List<ThrustSegment> decode(String thrustField) {
        return parse(thrustField).getOrThrow().thrust();
    }

    @Test
    void omittedThrustDecodesToEmptyTable() {
        assertTrue(decode(null).isEmpty(), "缺省 thrust 必须是空表，而不是一个全零段");
    }

    @Test
    void emptyArrayDecodesToEmptyTable() {
        assertEquals(List.of(), decode("\"thrust\": []"));
    }

    @Test
    void segmentFieldsMapToForceStartAndDuration() {
        List<ThrustSegment> segments = decode(
                "\"thrust\": [{ \"force\": 2600.0, \"start\": 0.1, \"duration\": 2.5 }]");
        assertEquals(1, segments.size());
        ThrustSegment segment = segments.get(0);
        assertEquals(2600.0f, segment.force(), 0f);
        assertEquals(0.1f, segment.start(), 1e-6f);
        assertEquals(2.5f, segment.duration(), 0f);
        assertEquals(2.6f, segment.end(), 1e-6f);
        assertTrue(segment.isActive());
    }

    @Test
    void multipleSegmentsAreKeptInOrderByTheCodec() {
        List<ThrustSegment> segments = decode("""
                "thrust": [
                  { "force": 2600.0, "start": 0.1, "duration": 2.5 },
                  { "force": 1800.0, "start": 8.0, "duration": 1.2 }
                ]""");
        assertEquals(2, segments.size(), "codec 保留全部段；只有首段进入积分是下游的事");
        assertEquals(0.1f, segments.get(0).start(), 1e-6f);
        assertEquals(8.0f, segments.get(1).start(), 1e-6f);
    }

    /**
     * 旧写法（单对象）必须在解码期失败，而不是被当成「一段」静默接受。
     * 这条用例锁死 {@code docs/plan/进行中/武器系统-推力分段与变质量计划.md} §七
     * 的「对象形态不保留，喊得响而不是静默降级」。
     */
    @Test
    void legacyObjectFormIsRejectedInsteadOfSilentlyAccepted() {
        DataResult<ExternalProperties> result = parse(
                "\"thrust\": { \"force\": 2600.0, \"duration\": 2.5, \"ignition_delay\": 0.1 }");
        assertTrue(result.error().isPresent(), "对象形态应当解码失败");
    }

    @Test
    void segmentWithoutForceOrDurationIsInactive() {
        assertFalse(new ThrustSegment(0f, 0f, 2.5f).isActive(), "零推力不施加");
        assertFalse(new ThrustSegment(2600f, 0f, 0f).isActive(), "零燃时不施加");
        assertTrue(new ThrustSegment(2600f, 0f, 2.5f).isActive());
    }
}
