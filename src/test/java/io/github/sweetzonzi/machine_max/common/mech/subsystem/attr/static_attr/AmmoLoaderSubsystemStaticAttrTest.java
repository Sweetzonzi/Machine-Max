package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AmmoLoaderSubsystemStaticAttr} 的时长字段边界：{@code reload_time} 与 {@code human_reload_time}
 * 都是有限非负数，0 表示本型号没有对应的段。
 * <p>
 * 机械段写 0 描述的是"弹链或弹匣直接接在枪上，机构没有动作"，与"没写这个字段"不是一回事——后者才是
 * 未配置，由必填字段本身拒绝。因此 0 必须解得出对象，负值必须解不出。
 */
class AmmoLoaderSubsystemStaticAttrTest {

    private static DataResult<AmmoLoaderSubsystemStaticAttr> parse(String json) {
        return AmmoLoaderSubsystemStaticAttr.CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static void assertAccepted(DataResult<AmmoLoaderSubsystemStaticAttr> result) {
        assertTrue(result.result().isPresent(),
                () -> result.error().map(DataResult.Error::message).orElse(""));
    }

    @Test
    void zeroMechanicalStageIsALegalConfiguration() {
        DataResult<AmmoLoaderSubsystemStaticAttr> result =
                parse("{\"basic_durability\": 20, \"reload_time\": 0}");
        assertAccepted(result);
        assertEquals(0f, result.result().orElseThrow().getReloadTime());
    }

    @Test
    void aMachineWithNeitherStageIsStillALegalConfiguration() {
        assertAccepted(parse("{\"reload_time\": 0, \"human_reload_time\": 0}"));
    }

    @Test
    void negativeDurationsAreRejected() {
        assertTrue(parse("{\"reload_time\": -0.01}").error().isPresent(),
                "机械段时长不得为负");
        assertTrue(parse("{\"reload_time\": 1, \"human_reload_time\": -0.01}").error().isPresent(),
                "人工段时长不得为负");
    }

    @Test
    void aMissingMechanicalStageIsStillAConfigurationError() {
        assertTrue(parse("{\"basic_durability\": 20}").error().isPresent(),
                "省略 reload_time 与写 0 是两件事，前者不合法");
    }
}
