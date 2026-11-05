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

    /** 整表折算：两段都生效，段间空隙无推力，两个段都不写装药所以质量恒定 */
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
     * 装药进剖面：`propellant_mass` 原样成为段质量，质量在窗口内匀速下降，燃尽后回落到基础质量。
     * <p>
     * `external.mass` 是<b>弹体基础质量</b>，装药另计，所以出膛质量是两者之和——这条断言把口径固定住，
     * 免得日后有人把 `mass` 当成"含药的全重"去改换算。
     */
    @Test
    void propellantMassIsCarriedIntoTheProfileAndBurnsDown() {
        ExternalProperties props = parse("""
                "thrust": [{ "force": 2600.0, "start": 0.1, "duration": 2.5, "propellant_mass": 2.0 }]""")
                .getOrThrow();
        assertEquals(2.0f, props.thrust().get(0).propellantMass(), 0f, "字段解码");

        ThrustProfile profile = props.toThrustProfile();
        assertEquals(2.0f, profile.segments().get(0).massKg(), 0f, "段质量按段搬运");
        assertEquals(28f, profile.massAt(0.0, 26f), 0f, "点火前 = 基础质量 + 全部装药");
        assertEquals(27f, profile.massAt(1.35, 26f), 1e-4f, "烧到一半");
        assertEquals(26f, profile.massAt(2.7, 26f), 0f, "燃尽后回落到基础质量");
    }

    /** 不写 `propellant_mass` 时缺省 0：段质量 0，`massAt` 逐位返回基础质量 */
    @Test
    void omittedPropellantKeepsMassConstant() {
        ThrustProfile profile = profileOf("{ \"force\": 2600.0, \"start\": 0.0, \"duration\": 2.5 }");
        assertEquals(0f, profile.segments().get(0).massKg(), 0f);
        assertEquals(26f, profile.massAt(1.0, 26f), 0f);
    }

    /**
     * 装药合计必须小于 `mass`：等于、大于、以及"分段都合法但合计超标"都在解码期失败。
     * <p>
     * 这层跨字段校验只放解码方向，因此构造函数与测试夹具可以照常造出极端剖面；它挡的是内容包。
     */
    @Test
    void propellantTotalMustStayBelowProjectileMass() {
        assertTrue(parse("\"thrust\": [{ \"force\": 2600.0, \"start\": 0.0, \"duration\": 1.0, \"propellant_mass\": 26.0 }]")
                .error().isPresent(), "装药等于弹体质量必须失败");
        assertTrue(parse("\"thrust\": [{ \"force\": 2600.0, \"start\": 0.0, \"duration\": 1.0, \"propellant_mass\": 30.0 }]")
                .error().isPresent(), "装药重于弹体必须失败");
        assertTrue(parse("""
                "thrust": [
                  { "force": 2600.0, "start": 0.0, "duration": 1.0, "propellant_mass": 14.0 },
                  { "force": 2600.0, "start": 2.0, "duration": 1.0, "propellant_mass": 14.0 }
                ]""").error().isPresent(), "校验按整表求和，单段合法不代表合计合法");
        assertTrue(parse("\"thrust\": [{ \"force\": 2600.0, \"start\": 0.0, \"duration\": 1.0, \"propellant_mass\": 25.9 }]")
                .error().isEmpty(), "合计小于 mass 必须通过");
    }

    /**
     * 负装药在解码期拒绝。库侧会把它当成无效段丢掉（{@code ThrustProfile} 要求段质量非负），
     * 于是整段推力一起消失——作者只会看到"没推力"，而这恰好是最难查的那种。
     */
    @Test
    void negativePropellantIsRejected() {
        assertTrue(parse("\"thrust\": [{ \"force\": 2600.0, \"start\": 0.0, \"duration\": 1.0, \"propellant_mass\": -1.0 }]")
                .error().isPresent());
    }

    /**
     * 旧写法（单对象）必须在解码期失败，而不是被当成「一段」静默接受。
     * 这条用例锁死 {@code docs/plan/已实现/武器系统-推力分段与变质量计划.md} §七
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
