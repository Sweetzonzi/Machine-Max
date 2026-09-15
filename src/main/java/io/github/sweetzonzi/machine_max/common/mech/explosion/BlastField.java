package io.github.sweetzonzi.machine_max.common.mech.explosion;

/**
 * 场强与终端的<b>唯一换算出口</b>（爆炸系统设计文档 §14.1 实现纪律 1）。无状态，全部静态方法。
 *
 * <p><b>推进循环不允许乘任何含距离的因子</b>——几何衰减只在这里出现。把 {@code I} 的出口收在
 * 一个类里是本设计最重要的一条结构性约束：终端只<b>读取</b> {@code I}，不修改它。
 * 因此所有需要强度的调用都必须经过 {@link #rayIntensity(float, float, ExplosionParams)}，
 * 且它不接收"目标"参数，只接收该射线自己的 {@code (E_r, d_r)}。</p>
 */
public final class BlastField {

    private static final float FOUR_PI = (float) (4.0 * Math.PI);

    private BlastField() {
    }

    /**
     * 参考场强 Φ₀ = 1 / (4π · near_radius²)（§6.1）。
     *
     * <p>它是该发爆炸<b>总能量</b>（本模型固定为 1）在参考距离处的场强，量纲为 m⁻²。</p>
     */
    public static float referenceFlux(ExplosionParams p) {
        float nr = p.nearRadius();
        return 1f / (FOUR_PI * nr * nr);
    }

    /**
     * 单条射线在其命中点处的归一化强度 I_r = E_r · (near_radius / d_eff)²（§6.1），
     * 含 §6.4 的近场平台钳制 {@code d_eff = max(d_r, near_radius)}。
     *
     * @param energy   该射线的剩余能量比例 E_r
     * @param distance 该射线飞行到命中点的距离 d_r（m）
     */
    public static float rayIntensity(float energy, float distance, ExplosionParams p) {
        float dEff = Math.max(distance, p.nearRadius());
        float ratio = p.nearRadius() / dEff;
        return energy * ratio * ratio;
    }

    /**
     * 单条射线的足迹 A_r = ΔΩ · d_r²（§7.1.1），仅冲量使用。
     *
     * <p>ΔΩ = 4π / rayCount，与 {@link BlastRayGenerator#adaptiveRayCount(float)} 同源。</p>
     */
    public static float footprint(float distance, ExplosionParams p) {
        int rayCount = BlastRayGenerator.adaptiveRayCount(p.nearRadius());
        float deltaOmega = FOUR_PI / rayCount;
        return deltaOmega * distance * distance;
    }

    /**
     * 沉积能量 → 基准伤害 D = base_damage · ΔE / Φ₀（§7.2）。
     *
     * <p>目标侧的材料修正（{@code blockDamageFactor} / {@code modifyDamage}）不在这里做，
     * 由 {@code ExplosionInstance.settleXxx} 分派给对应目标。</p>
     *
     * @param deltaE 被击穿射线的能量之和（一般实体按"到达"计）
     */
    public static float damage(float deltaE, ExplosionParams p) {
        return p.baseDamage() * deltaE / referenceFlux(p);
    }

    /**
     * 逐射线冲量项之和 → 冲量 J = base_impulse · Σ A_r·√I_r（§10.1）。
     *
     * @param sumFootprintSqrtI 全部命中射线的 {@code A_r·√I_r} 之和
     */
    public static float impulse(float sumFootprintSqrtI, ExplosionParams p) {
        return p.baseImpulse() * sumFootprintSqrtI;
    }
}
