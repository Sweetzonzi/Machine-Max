package io.github.sweetzonzi.machine_max.mech.trajectory;

import io.github.sweetzonzi.ballistics_framework.api.trajectory.BallisticConfig;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.DensityFunction;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.ThrustProfile;
import io.github.sweetzonzi.machine_max.common.mech.trajectory.TerrainPreload;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 视界几何的回归：长度、采样步长、{@code hold} 的时基，以及行走本身的自洽性。
 * <p>
 * 这几个量是整份预算表的输入（{@code W = v·(L + Δ) + 16}、单轮新增区块数 {@code v·Δ/16}、
 * {@code hold} 的物理步换算），一旦谁被改错，验收指标只会静默变差，不会报错——所以钉在这里。
 */
class TerrainPreloadTest {

    /** 与生产同一条归一化大气密度 */
    private static final DensityFunction DENSITY =
            pos -> (float) Math.exp(-(pos.y - 62.0) / 8500.0);

    /** 120mm APFSDS 的等效参数：细长杆、速度最高，视界最长 */
    private static final BallisticConfig APFSDS = config(4f, 30f, 0.20f);

    private static BallisticConfig config(float mass, float caliberMm, float dragCoefficient) {
        float radius = caliberMm / 2000f;
        return new BallisticConfig(dragCoefficient, mass,
                (float) (Math.PI * radius * radius), 9.81f, 0.01f, 64);
    }

    @Test
    @DisplayName("视界长度等于 v·(L+Δ)+16，逐弹种对上预算表")
    void horizonMatchesBudgetTable() {
        // 预算表里的五档：9M133 燃尽 300、155mm 600、7.62 855、30mm 950、APFSDS 1650
        Assertions.assertEquals(76f, TerrainPreload.horizonMeters(300f), 0.01f);
        Assertions.assertEquals(136f, TerrainPreload.horizonMeters(600f), 0.01f);
        Assertions.assertEquals(187f, TerrainPreload.horizonMeters(855f), 0.01f);
        Assertions.assertEquals(206f, TerrainPreload.horizonMeters(950f), 0.01f);
        Assertions.assertEquals(346f, TerrainPreload.horizonMeters(1650f), 0.01f);
    }

    @Test
    @DisplayName("采样步长取 0.01 s 与 8 m 的较小者")
    void sampleStepIsTheSmallerOfTimeAndDistance() {
        // v > 800 m/s 时由 8 m 卡住
        Assertions.assertEquals(8f / 1650f, TerrainPreload.sampleSeconds(1650f), 1e-9f);
        // 低于 800 m/s 时由 0.01 s 卡住
        Assertions.assertEquals(TerrainPreload.SAMPLE_SECONDS, TerrainPreload.sampleSeconds(600f), 1e-9f);
        Assertions.assertEquals(TerrainPreload.SAMPLE_SECONDS, TerrainPreload.sampleSeconds(0f), 1e-9f);
    }

    @Test
    @DisplayName("hold 按物理步频折算：同一个提前量在 100 与 60 Hz 下步数不同")
    void holdStepsUsePhysicsStepRate() {
        // 0.2 s 的提前量 = 20 个物理步（tps 100）+ 10 步余量
        Assertions.assertEquals(30, TerrainPreload.holdSteps(0.2f, 100));
        // 同一提前量在客户端物理步频下只有 12 + 6 步：分母确实是 tps，而不是视界采样步长
        Assertions.assertEquals(18, TerrainPreload.holdSteps(0.2f, 60));
        // 负提前量按 0 处理，只剩余量
        Assertions.assertEquals(10, TerrainPreload.holdSteps(-1f, 100));
    }

    @Test
    @DisplayName("行走逐段连续，且末段的提前量不小于 L——去掉 v·Δ 会在这里失败")
    void walkIsContinuousAndReachesLead() {
        // 起点取海平面上方，水平初速 1650 m/s；外扩按弹体半径（无制导）
        List<float[]> segments = walk(1650f, 0f, 0f, 0.015f);

        Assertions.assertFalse(segments.isEmpty());
        float travelled = 0f;
        float[] previous = null;
        for (float[] seg : segments) {
            if (previous != null) {
                Assertions.assertEquals(previous[3], seg[0], 1e-3f, "线段起点必须接上一段终点 X");
                Assertions.assertEquals(previous[4], seg[1], 1e-3f, "线段起点必须接上一段终点 Y");
                Assertions.assertEquals(previous[5], seg[2], 1e-3f, "线段起点必须接上一段终点 Z");
            }
            float dx = seg[3] - seg[0];
            float dy = seg[4] - seg[1];
            float dz = seg[5] - seg[2];
            travelled += (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            previous = seg;
        }

        Assertions.assertTrue(travelled >= TerrainPreload.horizonMeters(1650f),
                "累计走过 " + travelled + " m，视界 " + TerrainPreload.horizonMeters(1650f));
        Assertions.assertTrue(previous[8] > TerrainPreload.LEAD_SECONDS + 0.5f * TerrainPreload.REPLAN_SECONDS,
                "末段提前量 " + previous[8] + " s 必须覆盖 L + 半个 Δ");
    }

    @Test
    @DisplayName("Y 窗口按外扩半径放宽，含两端")
    void segmentWindowIncludesRadius() {
        // 水平飞行：Y 窗口应当是 [y - r, y + r] 向下/向上取整
        List<float[]> segments = walk(600f, 0f, 0f, 2.5f);
        float[] first = segments.get(0);
        Assertions.assertEquals(Math.floor(80.0 - 2.5), first[6], 1e-6f);
        Assertions.assertEquals(Math.ceil(80.0 + 2.5), first[7], 1e-6f);
    }

    /**
     * 走一遍视界并收集线段：{@code [ax, ay, az, bx, by, bz, yLo, yHi, lead]}。
     *
     * @param speed  水平初速（m/s）
     * @param vy     垂直初速（m/s）
     * @param vz     横向初速（m/s）
     * @param radius 外扩半径
     */
    private static List<float[]> walk(float speed, float vy, float vz, float radius) {
        List<float[]> out = new ArrayList<>();
        TerrainPreload.walk(APFSDS, ThrustProfile.NONE, DENSITY,
                0f, 80f, 0f, speed, vy, vz, 0f, radius,
                (ax, ay, az, bx, by, bz, yLo, yHi, lead) ->
                        out.add(new float[]{ax, ay, az, bx, by, bz, yLo, yHi, lead}));
        return out;
    }
}
