package io.github.sweetzonzi.machine_max.common.mech.trajectory;

import io.github.sweetzonzi.ballistics_framework.api.trajectory.BallisticConfig;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.DensityFunction;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.ThrustProfile;
import net.minecraft.world.phys.Vec3;

/**
 * 弹丸运动的唯一定义点：一次半隐式欧拉推进步。
 * <p>
 * 本步施加四项加速度：
 * <ul>
 *   <li><b>二次阻力</b> {@code a = -½·ρ·Cd·A·|v|²/m · v̂}，密度按步初位置查询；</li>
 *   <li><b>重力</b> 沿 {@code -Y}，大小为 {@link BallisticConfig#gravity()}；</li>
 *   <li><b>推力</b> 沿 {@code v̂}，大小 {@code force/mass}，只在燃烧窗口内施加；</li>
 *   <li><b>指令加速度</b> 由调用方给出（制导律输出），预载传 0。</li>
 * </ul>
 * 速度为半隐式更新（先算新速度），位置用<b>新</b>速度推进。
 * <p>
 * 服务端模拟（{@code ProjectileManager#updateProjectiles} 趟一）、客户端外推
 * （{@code ProjectileManager#clientExtrapolate}）与地形预载的视界行走都调用本方法，
 * 所以三者由构造同式，不存在“预测与模拟分家”的余地：改动这里的离散化就是改动服务端弹道本身。
 * <p>
 * <b>线程</b>：无状态，物理线程与主线程均可并发调用（唯一的分配来自
 * {@link DensityFunction} 的 {@code Vec3} 入参）。
 */
public final class TrajectoryStep {

    /** {@link #step} 输出数组的布局：{@code [px, py, pz, vx, vy, vz]}。 */
    public static final int OUT_PX = 0;
    public static final int OUT_PY = 1;
    public static final int OUT_PZ = 2;
    public static final int OUT_VX = 3;
    public static final int OUT_VY = 4;
    public static final int OUT_VZ = 5;

    /** 输出数组的最小长度。 */
    public static final int OUT_LENGTH = 6;

    private TrajectoryStep() {
    }

    /**
     * 推进一步。
     *
     * @param px       步初位置 X（m）
     * @param py       步初位置 Y（m）
     * @param pz       步初位置 Z（m）
     * @param vx       步初速度 X（m/s）
     * @param vy       步初速度 Y（m/s）
     * @param vz       步初速度 Z（m/s）
     * @param burnTime 自发射瞬间起算的已燃烧时长（s）。窗口判定取的是<b>步初</b>值，与
     *                 {@link ThrustProfile} 同一时基；本方法不推进它，调用方在推进后自行累加 {@code dt}
     * @param cfg      弹丸参数。{@code timeStep} 与 {@code maxSteps} 在这里不被读取——步长由
     *                 {@code dt} 显式给出，服务端物理 100 Hz 与客户端物理 60 Hz 的步长不同
     * @param thrust   推力剖面，{@link ThrustProfile#NONE} 表示无推力
     * @param density  空气密度函数，按步初位置查询；阻力系数为 0 或速度近零时不查询
     * @param gx       指令加速度 X（m/s²）
     * @param gy       指令加速度 Y（m/s²）
     * @param gz       指令加速度 Z（m/s²）
     * @param dt       步长（s）
     * @param out      输出缓冲，长度至少 {@value #OUT_LENGTH}，布局见 {@link #OUT_PX}
     */
    public static void step(
            float px, float py, float pz,
            float vx, float vy, float vz,
            float burnTime,
            BallisticConfig cfg, ThrustProfile thrust, DensityFunction density,
            float gx, float gy, float gz,
            float dt,
            float[] out
    ) {
        float speed = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);

        // ——— 二次阻力：F = ½·ρ·Cd·A·|v|²，方向 -v̂ ———
        float dragAccX = 0f, dragAccY = 0f, dragAccZ = 0f;
        if (cfg.dragCoefficient() > 1e-8f && speed > 1e-8f) {
            float rho = density.getDensity(new Vec3(px, py, pz));
            float dragAcc = 0.5f * rho * cfg.dragCoefficient() * cfg.crossSectionArea()
                    * speed * speed / cfg.mass();
            float invSpeed = 1f / speed;
            dragAccX = dragAcc * (-vx * invSpeed);
            dragAccY = dragAcc * (-vy * invSpeed);
            dragAccZ = dragAcc * (-vz * invSpeed);
        }

        // ——— 推力：沿 v̂、大小 force/mass；窗口左闭右开，只增速率、不改方向 ———
        float thrustAccX = 0f, thrustAccY = 0f, thrustAccZ = 0f;
        if (thrust.isActive() && speed > 1e-6f
                && burnTime >= thrust.startSeconds() && burnTime < thrust.endSeconds()) {
            float thrustAcc = thrust.force() / cfg.mass();
            float invSpeed = 1f / speed;
            thrustAccX = thrustAcc * vx * invSpeed;
            thrustAccY = thrustAcc * vy * invSpeed;
            thrustAccZ = thrustAcc * vz * invSpeed;
        }

        // ——— 半隐式欧拉：速度先更新，位置用新速度推进 ———
        float gravityAccY = -cfg.gravity();
        out[OUT_VX] = vx + (dragAccX + gx + thrustAccX) * dt;
        out[OUT_VY] = vy + (gravityAccY + dragAccY + gy + thrustAccY) * dt;
        out[OUT_VZ] = vz + (dragAccZ + gz + thrustAccZ) * dt;
        out[OUT_PX] = px + out[OUT_VX] * dt;
        out[OUT_PY] = py + out[OUT_VY] * dt;
        out[OUT_PZ] = pz + out[OUT_VZ] * dt;
    }
}
