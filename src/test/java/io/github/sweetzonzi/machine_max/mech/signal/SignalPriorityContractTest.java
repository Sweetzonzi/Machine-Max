package io.github.sweetzonzi.machine_max.mech.signal;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroup;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlMode;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimTargetState;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.MoveInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.RegularInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.RotationSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.Signal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ViewInputSignal;
import io.github.sweetzonzi.machine_max.util.data.KeyInputMapping;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 越级量的三条契约：它随信号走、按频道从控制组取、负值在解码期被拒。<br>
 * 读取点用 {@code Signal.priorityOf} 统一取值，非 Signal 的裸值按 0（按键绑定与 GUI 动作）；
 * 控制组的 {@code priorities} 是稀疏表，活动子组按频道键覆盖基础组，缺席按 0。
 */
class SignalPriorityContractTest {

    private static ControlGroup group(String name, Map<String, Integer> priorities) {
        return new ControlGroup(name, ControlMode.INHERIT, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                priorities, List.of());
    }

    @Test
    void seatsCopyTheSignalOnlyWhenTheNumberChanges() {
        ViewInputSignal view = new ViewInputSignal(new Vec3(1, 2, 3), Vec2.ZERO, AimTargetState.Mode.WORLD);
        assertEquals(0, view.priority, "默认不越级");
        assertSame(view, view.withPriority(0), "数值相同不产生副本");
        ViewInputSignal raised = view.withPriority(2);
        assertEquals(2, raised.priority);
        assertEquals(view.aimPoint, raised.aimPoint, "复制保留完整目标");

        MoveInputSignal move = new MoveInputSignal(new byte[]{1}, new byte[]{0});
        assertEquals(3, move.withPriority(3).priority);
        assertSame(move, move.withPriority(0));

        RegularInputSignal fire = new RegularInputSignal(KeyInputMapping.MAIN_FIRE, 0);
        assertEquals(1, fire.withPriority(1).priority);
        assertEquals(KeyInputMapping.MAIN_FIRE, fire.withPriority(1).getInputType());
    }

    @Test
    void executorAndInteractionLinesIgnorePriority() {
        RotationSignal rotation = new RotationSignal(0.1f, 0.2f, 0f);
        assertSame(rotation, rotation.withPriority(5), "执行器线不参与乘员输入排序");
        EmptySignal empty = EmptySignal.INSTANCE;
        assertSame(empty, empty.withPriority(5), "空信号不参与排序");
    }

    @Test
    void bareValuesCountAsZero() {
        assertEquals(0, Signal.priorityOf(1.0f), "按键绑定投递的裸值按 0 参与排序");
        assertEquals(0, Signal.priorityOf(EmptySignal.INSTANCE));
        assertEquals(2, Signal.priorityOf(new RegularInputSignal(KeyInputMapping.MAIN_FIRE, 0, 2)));
    }

    @Test
    void activeGroupOverridesTheBaseGroupPerChannel() {
        ControlGroup base = group("base", Map.of("crew_commander_aim", 2));
        ControlGroup override = group("override", Map.of("crew_commander_fire", 1));
        ControlGroupSet set = new ControlGroupSet(base, List.of(override), List.of(), 0);

        assertEquals(2, set.priorityOf("crew_commander_aim"), "子组没声明的频道继承基础组");
        assertEquals(1, set.priorityOf("crew_commander_fire"), "子组声明的频道按子组的值");
        assertEquals(0, set.priorityOf("crew_gunner_aim"), "都没声明的频道按 0");

        assertTrue(set.activate(-1), "切回基础组");
        assertEquals(0, set.priorityOf("crew_commander_fire"), "切出子组后回到 0");
        assertEquals(2, set.priorityOf("crew_commander_aim"));
    }

    @Test
    void jsonKeepsTheSparseTableAndRejectsNegativeValues() {
        ControlGroup parsed = ControlGroup.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"name":"base","priorities":{"crew_commander_aim":1}}
                """)).getOrThrow();
        assertEquals(Map.of("crew_commander_aim", 1), parsed.priorities);

        ControlGroup absent = ControlGroup.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"name":"base"}
                """)).getOrThrow();
        assertTrue(absent.priorities.isEmpty(), "整块可以不写");

        assertTrue(ControlGroup.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"name":"base","priorities":{"crew_commander_aim":-1}}
                """)).error().isPresent(), "负值在解码期就是错误");
    }

    @Test
    void streamCodecRoundTripsTheTable() {
        ControlGroup source = group("override", Map.of("crew_commander_aim_coax762", 1));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ControlGroup.STREAM_CODEC.encode(buffer, source);
            ControlGroup decoded = ControlGroup.STREAM_CODEC.decode(buffer);
            assertEquals(source.priorities, decoded.priorities);
            assertEquals(source.name, decoded.name);
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }
}
