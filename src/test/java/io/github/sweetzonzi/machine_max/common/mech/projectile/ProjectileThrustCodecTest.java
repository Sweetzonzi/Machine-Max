package io.github.sweetzonzi.machine_max.common.mech.projectile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.ThrustProfile;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType.ExternalProperties;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType.ExternalProperties.ThrustSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code external.thrust} 的内容包契约：形态是<b>段数组</b>，不是单对象。
 * <p>
 * 解码只保证「写进来的段都在」；段表怎么变成积分用的剖面由
 * {@link ExternalProperties#toThrustProfile()} 决定。两类断言分开写是有意的：数据形态是给
 * 内容作者的稳定契约，折算规则归弹道库（排序、丢弃无效段、拒绝重叠都在
 * {@link ThrustProfile} 的构造器里），MM 侧不复制一份。
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

        // 窗口终点不在内容包里：它由库侧按 start + duration 推导
        ThrustProfile profile = parse(
                "\"thrust\": [{ \"force\": 2600.0, \"start\": 0.1, \"duration\": 2.5 }]")
                .getOrThrow().toThrustProfile();
        assertEquals(0.1f, profile.startSeconds(), 1e-6f);
        // 左端闭，但查询要用同一个 float：内容包里的 0.1 解码成 0.1f，而 double 字面量 0.1
        // 比 0.1f 略小，按 double 传会落在窗口之前
        assertEquals(2600f, profile.forceAt(0.1f), 0f, "窗口左端闭");
        assertEquals(2600f, profile.forceAt(1.0), 0f, "窗口内");
        assertEquals(0f, profile.forceAt(2.61), 0f, "熄火之后");
    }

    @Test
    void multipleSegmentsAreKeptInOrderByTheCodec() {
        List<ThrustSegment> segments = decode("""
                "thrust": [
                  { "force": 2600.0, "start": 0.1, "duration": 2.5 },
                  { "force": 1800.0, "start": 8.0, "duration": 1.2 }
                ]""");
        assertEquals(2, segments.size(), "codec 保留全部段，折算与积分在下游");
        assertEquals(0.1f, segments.get(0).start(), 1e-6f);
        assertEquals(8.0f, segments.get(1).start(), 1e-6f);
    }

    /** 整表折算：两段都生效，段间空隙无推力，质量恒定（内容包没有装药字段） */
    @Test
    void conversionAppliesEverySegmentAndLeavesGapsCoasting() {
        ThrustProfile profile = parse("""
                "thrust": [
                  { "force": 2600.0, "start": 0.1, "duration": 2.5 },
                  { "force": 1800.0, "start": 8.0, "duration": 1.2 }
                ]""").getOrThrow().toThrustProfile();

        assertEquals(2, profile.segments().size());
        assertEquals(2600f, profile.forceAt(1.0), 0f, "第一段");
        assertEquals(0f, profile.forceAt(4.0), 0f, "段间空隙无推力");
        assertEquals(1800f, profile.forceAt(8.0), 0f, "第二段");
        assertEquals(0f, profile.forceAt(9.5), 0f, "末段之后");
        assertEquals(26f, profile.massAt(9.0, 26f), 0f, "段质量为 0，质量恒定");
    }

    /** 重叠是内容包笔误：折算期由库侧抛错，由调用方转成点名弹种的日志 */
    @Test
    void overlappingSegmentsAreRejectedAtConversion() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
                "thrust": [
                  { "force": 2600.0, "start": 0.0, "duration": 2.0 },
                  { "force": 1800.0, "start": 1.0, "duration": 1.0 }
                ]""").getOrThrow().toThrustProfile());
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

    /** 零推力 / 零燃时的段不进剖面：库侧构造器直接丢弃，不需要 MM 再判一次 */
    @Test
    void invalidSegmentsAreDroppedByTheConversion() {
        assertFalse(profileOf("{ \"force\": 0.0, \"start\": 0.0, \"duration\": 2.5 }").isActive(),
                "零推力段被丢弃");
        assertFalse(profileOf("{ \"force\": 2600.0, \"start\": 0.0, \"duration\": 0.0 }").isActive(),
                "零燃时段被丢弃");
        assertTrue(profileOf("{ \"force\": 2600.0, \"start\": 0.0, \"duration\": 2.5 }").isActive());
    }

    private static ThrustProfile profileOf(String segmentJson) {
        return parse("\"thrust\": [" + segmentJson + "]").getOrThrow().toThrustProfile();
    }
}
