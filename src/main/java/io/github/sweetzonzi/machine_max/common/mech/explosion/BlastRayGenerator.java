package io.github.sweetzonzi.machine_max.common.mech.explosion;

import com.jme3.math.Vector3f;

import java.util.Random;

/**
 * 爆炸射线的确定性方向集与射线数自适应（爆炸系统设计文档 §4.1、§4.7）。纯静态、无状态。
 *
 * <p><b>抖动只在这里发生一次</b>：{@link #generate} 之后方向不再改变，这是客户端能凭
 * "起爆点 + 种子"复现整条时间线的前提。</p>
 */
public final class BlastRayGenerator {

    /** 射线数下界（纯性能参数，代码常量，JSON 不可配置）。 */
    public static final int MIN_RAYS = 64;
    /** 射线数上界（纯性能参数，代码常量，JSON 不可配置）。 */
    public static final int MAX_RAYS = 4096;

    /** §4.7 的自适应系数：rayCount ≥ 10 · 4π · near_radius² ≈ 126 · near_radius²。 */
    private static final float RAY_DENSITY_COEFF = 126f;

    private BlastRayGenerator() {
    }

    /**
     * 依 {@code near_radius} 自适应射线数（§4.7）。这是该公式的唯一定义处：
     * 射线数不进 JSON，起爆时由 {@link ExplosionManager} 调一次，客户端按同一公式重算。
     *
     * @param nearRadius 参考距离（m）
     * @return 射线数，已钳制到 [{@link #MIN_RAYS}, {@link #MAX_RAYS}]
     */
    public static int adaptiveRayCount(float nearRadius) {
        int n = Math.round(RAY_DENSITY_COEFF * nearRadius * nearRadius);
        return Math.clamp(n, MIN_RAYS, MAX_RAYS);
    }

    /**
     * Fibonacci 球 + 种子抖动，一次性固定方向（§4.1）。
     *
     * @param origin   起爆点
     * @param seed     起爆种子（双端一致，决定抖动）
     * @param rayCount 射线数
     * @return 射线组，每个射线带自己的 DDA 游标与去重集
     */
    public static BlastRay[] generate(Vector3f origin, long seed, int rayCount) {
        BlastRay[] rays = new BlastRay[rayCount];
        Random rnd = new Random(seed);
        // 黄金角：Fibonacci 球的方位角增量
        float goldenAngle = (float) (Math.PI * (3.0 - Math.sqrt(5.0)));
        // 抖动幅度：约一个环间距，避免出现对称的"星形"破坏图案
        float yJitter = 1.0f / rayCount;
        float thetaJitter = (float) (2.0 * Math.PI / rayCount);

        for (int i = 0; i < rayCount; i++) {
            // 基准：均匀分布于球面的 Fibonacci 点
            float y = 1.0f - 2.0f * (i + 0.5f) / rayCount;
            float theta = goldenAngle * i;

            // 种子抖动（确定性）
            y += (rnd.nextFloat() - 0.5f) * yJitter;
            y = Math.clamp(y, -1.0f, 1.0f);
            theta += (rnd.nextFloat() - 0.5f) * thetaJitter;

            float r = (float) Math.sqrt(Math.max(0f, 1.0f - y * y));
            float x = (float) Math.cos(theta) * r;
            float z = (float) Math.sin(theta) * r;

            rays[i] = new BlastRay(origin, new Vector3f(x, y, z));
        }
        return rays;
    }
}
