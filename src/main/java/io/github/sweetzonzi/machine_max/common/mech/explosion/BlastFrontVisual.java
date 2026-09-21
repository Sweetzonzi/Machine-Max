package io.github.sweetzonzi.machine_max.common.mech.explosion;

import com.jme3.math.Vector3f;

/**
 * 单发起爆的客户端表现条目。每发起爆一个实例，
 * 由 {@link ExplosionManager} 在客户端的表现条目集合中持有与淘汰。
 *
 * <p>逻辑侧的 {@code frontRadius()} 由 {@link ExplosionInstance} 提供；本类只在客户端持有表现状态。
 * 年龄从 0 起算、每次推进自增，与服务端实例的 {@code tickCount} 同一定义，因此客户端无需知道
 * 服务端已经推进了几步。</p>
 */
public final class BlastFrontVisual {

    private final Vector3f origin;
    private final ExplosionParams params;
    private final long seed;
    private int age;
    /** 上一次推进时的波前半径，供渲染帧做插值（渲染帧率高于 20 时直接读年龄会跳格） */
    private float prevRadius;

    /**
     * 收到起爆网络包时建立本地表现（客户端）。
     *
     * @param origin 起爆点
     * @param params 参数集（与逻辑侧同一份）
     * @param seed   起爆种子（可复现整条时间线）
     */
    public BlastFrontVisual(Vector3f origin, ExplosionParams params, long seed) {
        this.origin = origin.clone();
        this.params = params;
        this.seed = seed;
        this.age = 0;
        this.prevRadius = 0f;
    }

    /** 客户端推进一次（与服务端同节拍时各类表现与逻辑同源）。 */
    public void tick() {
        prevRadius = frontRadius();
        age++;
    }

    /** 当前波前半径 = 年龄 × front_speed / 20，与逻辑侧同源。 */
    public float frontRadius() {
        return age * params.advancePerStep();
    }

    /**
     * 渲染用的插值半径：在上一 tick 的半径与当前半径之间按 partialTick 线性插值。
     *
     * <p>插值只用于表现，不写回年龄；逻辑侧仍按整数步推进。物理线程过载导致快照变稀时
     * 插值进度会落后半帧以内，由于半径是年龄的线性函数，下一帧即自愈。</p>
     *
     * @param partialTick 帧间插值量，取值 [0, 1]
     */
    public float frontRadius(float partialTick) {
        return prevRadius + (frontRadius() - prevRadius) * partialTick;
    }

    /** 寿命 = maxRadius / frontSpeed；到点后由 Manager 从集合中移除。 */
    public boolean isFinished() {
        return frontRadius() >= params.maxRadius();
    }

    public Vector3f origin() {
        return origin;
    }

    public ExplosionParams params() {
        return params;
    }

    public long seed() {
        return seed;
    }

    public int age() {
        return age;
    }
}
