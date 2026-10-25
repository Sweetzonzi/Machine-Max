package io.github.sweetzonzi.machine_max.mech.trajectory;

import io.github.sweetzonzi.ballistics_framework.api.trajectory.BallisticConfig;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.DensityFunction;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.RealisticTrajectory;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.ThrustProfile;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.TrajectoryResult;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.TrajectorySample;
import io.github.sweetzonzi.machine_max.common.mech.trajectory.TrajectoryStep;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TrajectoryStep} 与 BallisticsFramework 正解的一致性回归。
 * <p>
 * 守的是“我们与 BF 没有分家”：两边是两套独立实现，一旦有人单方面改了阻力式、重力方向、
 * 推力方向或燃烧窗口的判定口径，这里的逐步比对就会失败。
 * <p>
 * <b>比对基准必须是一条具体路径。</b>
 * {@code RealisticTrajectory#forwardSolve(start, velocity, config, density, thrust)} 先看推力是否生效，
 * 再按 {@code gravity == 0} / {@code isNoDrag} 分派到五条路径之一。落点不同，逐步比对就会假失败：
 * <ul>
 *   <li>无推力、有重力、有阻力 → {@code solveFullIntegrationSI}（完整半隐式欧拉）；</li>
 *   <li>推力生效、有重力 → {@code solveThrustReducedSI}（竖直平面内的二维推进）；</li>
 *   <li>无阻力（{@code Cd·A} 为 0）→ {@code solveParabolicMotionSI}，重力项走解析抛物线；</li>
 *   <li>{@code gravity == 0} → {@code solveLinearMotionSI} / {@code solveStraightDecelerationSI}。</li>
 * </ul>
 * 本类只对前两条做比对，且参数组都带阻力、带重力，不会落进退化路径。
 * <p>
 * <b>容差的两个来源</b>（两者都与“模型是否一致”无关，只是浮点表示与算子次序的差异）：
 * <ol>
 *   <li>{@code TrajectoryStep} 在 {@code float} 上推进（SoA 数组就是 float），
 *       BF 的完整积分在 {@code Vec3}（double）上推进。N 步后相对差约 {@code N·2⁻²⁴}。</li>
 *   <li>BF 的推力路径把阻力与推力都写成<b>乘法</b>复合（{@code u -= u·k·dt} 后 {@code u += u·aT·dt/|v|}），
 *       本实现与既有模拟一致地写成<b>加法</b>求和。两者的差是交叉项
 *       {@code |v|·(k·dt)·(aT·dt/|v|) = k·aT·dt²}，量级 {@code O(dt²)}。</li>
 * </ol>
 * 第 1 条在 20 步、数百米的量级上大约 10⁻⁴ m，因此无推力路径的位置与速度都取 10⁻³。
 * 第 2 条按 {@code k·aT·dt²} 逐步累积：本类的强推力用例里
 * {@code k = ½·Cd·A·ρ·|v|/m ≈ 0.155}、{@code aT = 1428 m/s²}、{@code dt = 0.01 s}，
 * 单步速度差约 0.022 m/s，10 个推力步后约 0.22 m/s，位置差 {@code ≲ ½·0.22·0.1 = 0.011 m}。
 * 因此推力路径的位置取 0.1 m、速度取 1.0 m/s，各留 4~9 倍余量。
 * 这两个门槛仍远小于任何结构性错误（写错推力方向、窗口错位一个步、重力符号反）
 * 造成的偏差——那些至少是几十 m/s 或几米。
 */
class TrajectoryStepTest {

    /** 无推力路径的容差（m、m/s）：只覆盖 float 与 double 的表示差 */
    private static final double TOLERANCE_TRACKING = 1e-3;

    /** 推力路径的位置容差（m）：额外覆盖乘法复合与加法求和的 O(dt²) 差，见类注释 */
    private static final double TOLERANCE_THRUST_POS = 0.1;

    /** 推力路径的速度容差（m/s）：同一个交叉项逐步累积，见类注释 */
    private static final double TOLERANCE_THRUST_VEL = 1.0;

    /** 与 {@code ProjectileManager} 同一条归一化大气密度：海平面 y=62 处为 1.0 */
    private static final DensityFunction DENSITY =
            pos -> (float) Math.exp(-(pos.y - 62.0) / 8500.0);

    /** 一个比对用例的参数组 */
    private record Case(
            String name,
            float mass,
            float caliberMm,
            float dragCoefficient,
            float gravityFactor,
            float speed,
            ThrustProfile thrust
    ) {
        BallisticConfig config(float dt, int maxSteps) {
            float radius = caliberMm / 2000f;
            return new BallisticConfig(
                    dragCoefficient,
                    mass,
                    (float) (Math.PI * radius * radius),
                    gravityFactor * 9.81f,
                    dt,
                    maxSteps);
        }
    }

    /**
     * 155mm 榴弹、7.62mm 步枪弹与 120mm 穿甲弹各走 20 步（0.2 s）。
     * <p>
     * 20 步正好是预载视界的量级（{@code W / v ≈ L + Δ = 0.2 s}），
     * 比对因此覆盖了预载真正使用的航段；同时把 float 与 double 的累计差压在容差以内。
     */
    @Test
    @DisplayName("无推力：20 步逐步等于 solveFullIntegrationSI")
    void matchesFullIntegration() {
        float dt = 0.01f;
        int steps = 20;

        Case[] cases = {
                // 155mm HE：Cd 高、弹重大，阻力加速度约 30 m/s²
                new Case("155mm", 43.0f, 155f, 0.45f, 1.0f, 600f, ThrustProfile.NONE),
                // 7.62×54R：轻弹、速度高
                new Case("7.62mm", 0.0096f, 7.62f, 0.30f, 1.0f, 855f, ThrustProfile.NONE),
                // 120mm APFSDS：细长杆，本测试用等效的 30mm 迎风面
                new Case("APFSDS", 4.0f, 30f, 0.20f, 1.0f, 1650f, ThrustProfile.NONE),
        };

        for (Case c : cases) {
            BallisticConfig config = c.config(dt, steps);
            // 起射点取世界海平面上方，密度随高度衰减；初速带一点仰角，重力项不被零速掩盖
            Vec3 start = new Vec3(0.0, 80.0, 0.0);
            Vec3 velocity = new Vec3(c.speed() * 0.98, c.speed() * 0.2, 0.0);

            TrajectoryResult reference = RealisticTrajectory.forwardSolve(start, velocity, config, DENSITY);
            Assertions.assertEquals(steps + 1, reference.samples().size(), c.name() + " 采样点数");

            assertTracking(c.name(), start, velocity, null, config, reference, steps,
                    TOLERANCE_TRACKING, TOLERANCE_TRACKING);
        }
    }

    /**
     * 9M133 一类带助推的弹：阻力加速度近百 m/s²，推力 1428 m/s²（20000 N / 14 kg）。
     * <p>
     * 燃烧窗口取 {@code [0.055, 0.156)}——两端的 0.005 s 与 0.004 s 余量都远大于 float
     * 累积误差（~10⁻⁷ s），因此“第几步开始/停止施加推力”在两边必然一致；
     * 若窗口判定写成右闭、或按步后时间判定，偏差会直接体现为米级差异。
     */
    @Test
    @DisplayName("带推力：20 步等于 solveThrustReducedSI，且燃烧窗口的步位一致")
    void matchesThrustReduced() {
        float dt = 0.01f;
        int steps = 20;

        ThrustProfile thrust = new ThrustProfile(20000f, 0.055f, 0.156f);
        Case c = new Case("9M133", 14.0f, 152f, 0.40f, 1.0f, 600f, thrust);
        BallisticConfig config = c.config(dt, steps);

        Vec3 start = new Vec3(0.0, 80.0, 0.0);
        Vec3 velocity = new Vec3(c.speed(), 0.0, 0.0);

        TrajectoryResult reference = RealisticTrajectory.forwardSolve(start, velocity, config, DENSITY, thrust);
        Assertions.assertEquals(steps + 1, reference.samples().size(), "采样点数");

        assertTracking(c.name(), start, velocity, thrust, config, reference, steps,
                TOLERANCE_THRUST_POS, TOLERANCE_THRUST_VEL);
    }

    /**
     * 窗口整个落在视界之外时，轨迹必须与无推力逐位相同。
     * <p>
     * 这条钉的是“推力项的施加条件”：窗口外多施一步推力会带来 {@code aT·dt ≈ 14 m/s} 的速度差，
     * 逐位比对立刻失败。
     */
    @Test
    @DisplayName("燃烧窗口在视界之外时，与无推力逐位相同")
    void thrustOutsideHorizonIsBitIdentical() {
        float dt = 0.01f;
        int steps = 20;

        Case c = new Case("窗口外", 14.0f, 152f, 0.40f, 1.0f, 600f, ThrustProfile.NONE);
        BallisticConfig config = c.config(dt, steps);
        Vec3 start = new Vec3(0.0, 80.0, 0.0);
        Vec3 velocity = new Vec3(600.0, 0.0, 0.0);

        float[] withoutThrust = walk(start, velocity, null, config, steps, dt);
        float[] lateWindow = walk(start, velocity,
                new ThrustProfile(20000f, steps * dt, steps * dt + 0.5f), config, steps, dt);

        Assertions.assertArrayEquals(withoutThrust, lateWindow, "窗口起点在视界末步之后不应施加推力");
    }

    /**
     * 步长来自显式 {@code dt}，不来自 {@code BallisticConfig#timeStep}。
     * <p>
     * 服务端物理 100 Hz、客户端物理 60 Hz，任何“读当前物理步长”的实现都会在客户端算出 1/60；
     * 因此同参、同 {@code dt}、不同 {@code timeStep} 必须给出逐位相同的结果。
     */
    @Test
    @DisplayName("config.timeStep 不参与步进：换掉它结果逐位不变")
    void stepDoesNotReadConfigTimeStep() {
        float dt = 0.01f;
        int steps = 10;
        Vec3 start = new Vec3(12.5, 70.0, -3.25);
        Vec3 velocity = new Vec3(500.0, 120.0, 40.0);

        float[] withServerStep = walk(start, velocity, null, configFor(steps, 0.01f), steps, dt);
        float[] withClientStep = walk(start, velocity, null, configFor(steps, 1f / 60f), steps, dt);

        Assertions.assertArrayEquals(withServerStep, withClientStep, "timeStep 不得影响步进");
    }

    // ==================== 辅助 ====================

    private static BallisticConfig configFor(int maxSteps, float timeStep) {
        float radius = 155f / 2000f;
        return new BallisticConfig(0.45f, 43f, (float) (Math.PI * radius * radius), 9.81f, timeStep, maxSteps);
    }

    /** 用各种子以 {@code dt} 推进 {@code steps} 步，返回末状态 {@code [px, py, pz, vx, vy, vz]} */
    private static float[] walk(Vec3 start, Vec3 velocity, ThrustProfile thrust,
                                BallisticConfig config, int steps, float dt) {
        float px = (float) start.x, py = (float) start.y, pz = (float) start.z;
        float vx = (float) velocity.x, vy = (float) velocity.y, vz = (float) velocity.z;
        float[] out = new float[TrajectoryStep.OUT_LENGTH];
        ThrustProfile profile = (thrust == null) ? ThrustProfile.NONE : thrust;
        for (int k = 0; k < steps; k++) {
            TrajectoryStep.step(px, py, pz, vx, vy, vz, (float) (k * (double) dt),
                    config, profile, DENSITY, 0f, 0f, 0f, dt, out);
            px = out[TrajectoryStep.OUT_PX];
            py = out[TrajectoryStep.OUT_PY];
            pz = out[TrajectoryStep.OUT_PZ];
            vx = out[TrajectoryStep.OUT_VX];
            vy = out[TrajectoryStep.OUT_VY];
            vz = out[TrajectoryStep.OUT_VZ];
        }
        return new float[]{px, py, pz, vx, vy, vz};
    }

    /**
     * 逐步比对：{@code TrajectoryStep} 的第 k 步必须与参考实现的第 k 个采样点相同。
     * <p>
     * 第 k 步的步初燃烧时长是 {@code (k − 1)·dt}——参考实现的第 k 步覆盖
     * {@code [(k−1)dt, k·dt)}，窗口判定取的就是左端点。写成 {@code k·dt} 会让推力晚一步生效，
     * 强推力的用例会以米级差异失败。乘加在 double 上算再转 float，与参考实现的
     * {@code (step - 1) * (double) dt} 同值。
     */
    private static void assertTracking(String name, Vec3 start, Vec3 velocity, ThrustProfile thrust,
                                       BallisticConfig config, TrajectoryResult reference,
                                       int steps, double tolerancePos, double toleranceVel) {
        float dt = config.timeStep();
        float px = (float) start.x, py = (float) start.y, pz = (float) start.z;
        float vx = (float) velocity.x, vy = (float) velocity.y, vz = (float) velocity.z;
        float[] out = new float[TrajectoryStep.OUT_LENGTH];
        ThrustProfile profile = (thrust == null) ? ThrustProfile.NONE : thrust;

        for (int k = 1; k <= steps; k++) {
            TrajectoryStep.step(px, py, pz, vx, vy, vz, (float) ((k - 1) * (double) dt),
                    config, profile, DENSITY, 0f, 0f, 0f, dt, out);
            px = out[TrajectoryStep.OUT_PX];
            py = out[TrajectoryStep.OUT_PY];
            pz = out[TrajectoryStep.OUT_PZ];
            vx = out[TrajectoryStep.OUT_VX];
            vy = out[TrajectoryStep.OUT_VY];
            vz = out[TrajectoryStep.OUT_VZ];

            TrajectorySample expected = reference.samples().get(k);
            Vec3 expectedPos = expected.position();
            Vec3 expectedVel = expected.velocity();
            String at = name + " 第 " + k + " 步";

            Assertions.assertEquals(expectedPos.x, px, tolerancePos, at + " 位置 X");
            Assertions.assertEquals(expectedPos.y, py, tolerancePos, at + " 位置 Y");
            Assertions.assertEquals(expectedPos.z, pz, tolerancePos, at + " 位置 Z");
            Assertions.assertEquals(expectedVel.x, vx, toleranceVel, at + " 速度 X");
            Assertions.assertEquals(expectedVel.y, vy, toleranceVel, at + " 速度 Y");
            Assertions.assertEquals(expectedVel.z, vz, toleranceVel, at + " 速度 Z");
        }
    }
}
