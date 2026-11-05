package io.github.sweetzonzi.machine_max.common.mech.vehicle.collision;

import io.github.sweetzonzi.machine_max.probe.ClimbLiftRig.LoadRig;
import io.github.sweetzonzi.machine_max.probe.ClimbLiftRig.SupportRig;
import io.github.sweetzonzi.machine_max.probe.NativeLibraryLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static io.github.sweetzonzi.machine_max.probe.ClimbLiftRig.DT;
import static io.github.sweetzonzi.machine_max.probe.ClimbLiftRig.OVERLAP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 台阶抬升的求解器口径：承载接触的深度如何变成竖直速度，以及
 * {@code CollisionHandler} 把承载深度钳到一个垫高（{@code CLIMB_LIFT_PENETRATION}）之后
 * 车体速度是否仍被位置修正顶飞。
 *
 * <p>同一条试具上的其它场景（法线改写、接触点位置、外力对照、悬挂静置）产出的是打印表格，
 * 已归入 {@code probe.ClimbLiftContactProbe}。</p>
 *
 * <p>只在 Windows 上跑：断言依赖本机重建的 bulletjme 在近接触上的行为。</p>
 */
@EnabledOnOs(OS.WINDOWS)
class ClimbLiftContactTest {

    @BeforeAll
    static void loadNative() {
        NativeLibraryLoader.load();
    }

    /**
     * 侵入深度 → 竖直速度。球正下方接触，法线本就竖直，没有任何改写：量的是求解器自己的映射。
     *
     * <p>深度浅于 split impulse 阈值（4 cm）时，一步后的竖直速度就是 {@code erp × depth / dt}
     * （1:1，与深度成正比）；越过阈值后这条接触的穿透修正改走分裂冲量，竖直速度恰好为 0，
     * 只把位置每步抬高 {@code erp × depth}。</p>
     */
    @Test
    void supportDepthDecidesTheVerticalVelocity() {
        for (float depth : new float[]{0.002f, 0.005f, 0.01f, 0.02f, 0.03f, 0.039f, 0.041f, 0.05f, 0.08f, 0.15f}) {
            try (SupportRig rig = new SupportRig(depth)) {
                float first = rig.advance(1);
                float bottom = rig.bottom();
                float expected = 0.2f * depth / DT;
                if (depth < 0.04f) {
                    assertEquals(expected, first, 1e-3f,
                            "阈值以内：一步后的竖直速度等于 erp*depth/dt（depth=" + depth + "）");
                } else {
                    assertEquals(0f, first, 1e-6f,
                            "越过阈值：分裂冲量不改速度，竖直速度必须是 0（depth=" + depth + "）");
                    assertEquals(-depth * 0.8f, bottom, 1e-3f,
                            "越过阈值：位置每步被抬高 erp*depth（depth=" + depth + "）");
                }
            }
        }
    }

    /**
     * 承载接触的深度上限：前推 500 N 把轮子推进方块的同一次过程里，复刻
     * {@code CollisionHandler} 的「承载深度不超过一个垫高」那一条。
     *
     * <p>契约：钳位以后单步抬高回到「一个垫的量级」（不超过 2 cm），车体竖直速度不再被
     * 位置修正顶到 1 m/s 以上。</p>
     */
    @Test
    void deepSupportContactDepthIsCappedByTheLiftPad() {
        try (LoadRig rig = new LoadRig(3, true, -0.05f, 0f)) {
            rig.settle(600);
            rig.engage(OVERLAP);
            rig.press = 500f;
            float previousBottom = rig.bottom();
            float peakBody = Float.NEGATIVE_INFINITY;
            float maxStepLift = 0f;
            for (int step = 1; step <= 160; step++) {
                rig.advance(1);
                float bottom = rig.bottom();
                peakBody = Math.max(peakBody, rig.body.getLinearVelocity(null).y);
                maxStepLift = Math.max(maxStepLift, bottom - previousBottom);
                previousBottom = bottom;
            }
            assertTrue(maxStepLift < 0.02f,
                    "承载深度钳到一个垫高后，单步抬高不应再是位置修正量级（实测 " + maxStepLift + "）");
            assertTrue(peakBody < 1.0f,
                    "承载深度钳到一个垫高后，车体竖直速度不应被顶到 1 m/s 以上（实测 " + peakBody + "）");
        }
    }
}
