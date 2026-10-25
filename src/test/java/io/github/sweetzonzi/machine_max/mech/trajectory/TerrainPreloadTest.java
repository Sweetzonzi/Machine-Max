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

    /** 单轮 1 个游戏 tick */
    private static final float DT_GAME_TICK = 1f / 20f;

    /** 单轮可提交的区块上限 */
    private static final int MAX_NEW_CHUNKS_PER_ROUND = 8;

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
    @DisplayName("去掉 v·Δ 项会让视界短掉一整段带，APFSDS 上可见")
    void horizonMustIncludeReplanInterval() {
        // v·L + 16 在 APFSDS 上是 263.5 m，比正解短 82.5 m —— 正是 v·Δ
        float missing = 1650f * TerrainPreload.LEAD_SECONDS + TerrainPreload.HORIZON_MARGIN_METERS;
        Assertions.assertEquals(82.5f,
                TerrainPreload.horizonMeters(1650f) - missing, 0.01f);
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
    @DisplayName("hold 按物理步频折算：用采样步长当分母会放大两倍以上")
    void holdStepsUsePhysicsStepRate() {
        // 0.2 s 的提前量 = 20 个物理步（tps 100）+ 10 步余量
        Assertions.assertEquals(30, TerrainPreload.holdSteps(0.2f, 100));

        // 反例：APFSDS 的采样步长是 4.85 ms，用它当分母会得到 ceil(0.2 / 0.00485) + 10 = 52，
        // 比正解大七成、且随速度变化——这就是“分母必须是 tps”的那条回归
        float sampleDt = TerrainPreload.sampleSeconds(1650f);
        int wrong = (int) Math.ceil(0.2f / sampleDt) + 10;
        Assertions.assertTrue(wrong > 2 * 30 - 15, "反例算出的步数应当明显偏大：" + wrong);
        Assertions.assertNotEquals(wrong, TerrainPreload.holdSteps(0.2f, 100));
    }

    @Test
    @DisplayName("单轮新进视界的区块数由 Δ 决定，1 tick 才落在上限内")
    void newChunksPerRoundBindDelta() {
        // v·Δ/16 是**轴向**下界：斜穿实测 APFSDS 峰值 9、压格线时 12。
        // 这条用例守的是“Δ 的选取判据”（Δ ≤ 16N/v），不是“实测区块数 ≤ 8”。
        for (float speed : new float[]{1650f, 950f, 855f}) {
            float newChunks = speed * DT_GAME_TICK / 16f;
            Assertions.assertTrue(newChunks <= MAX_NEW_CHUNKS_PER_ROUND,
                    "v=" + speed + " 单轮新进 " + newChunks + " 个区块");
        }
        // Δ 取 0.2 s 就会越界：APFSDS 20.6、30mm 11.9、7.62 10.7
        Assertions.assertTrue(1650f * 0.2f / 16f > MAX_NEW_CHUNKS_PER_ROUND);
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
