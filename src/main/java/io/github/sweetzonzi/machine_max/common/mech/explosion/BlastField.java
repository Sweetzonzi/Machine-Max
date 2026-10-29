package io.github.sweetzonzi.machine_max.common.mech.explosion;

/**
 * 场强与终端的<b>唯一换算出口</b>。无状态，全部静态方法。
 *
 * <p><b>推进循环不允许乘任何含距离的因子</b>——几何衰减只在这里出现。把 {@code I} 的出口收在
 * 一个类里是本设计最重要的一条结构性约束：终端只<b>读取</b> {@code I}，不修改它。
 * 因此所有需要强度的调用都必须经过 {@link #rayIntensity(float, float, ExplosionParams)}，
 * 且它不接收"目标"参数，只接收该射线自己的 {@code (E_r, d_r)}。</p>
 *
 * <p>本类共有三个出口，分别对应三个终端：</p>
 * <ul>
 *   <li>{@link #rayIntensity} —— 强度（穿深、冲量的 {@code √I}）；近场平台在此钳制；</li>
 *   <li>{@link #damage} —— 伤害；近场钳制在此按目标参考面积收口；</li>
 *   <li>{@link #footprint} —— 冲量用的逐射线足迹 {@code ΔΩ·d²}。</li>
 * </ul>
 */
public final class BlastField {

    private static final float FOUR_PI = (float) (4.0 * Math.PI);

    private BlastField() {
    }

    /**
     * 参考场强 Φ₀ = 1 / (4π · near_radius²)。
     *
     * <p>它是该发爆炸<b>总能量</b>（本模型固定为 1）在参考距离处的场强，量纲为 m⁻²。</p>
     */
    public static float referenceFlux(ExplosionParams p) {
        float nr = p.nearRadius();
        return 1f / (FOUR_PI * nr * nr);
    }

    /**
     * 单条射线在其命中点处的归一化强度 I_r = E_r · (near_radius / d_eff)²，
     * 含近场平台钳制 {@code d_eff = max(d_r, near_radius)}。
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
     * 单条射线的足迹 A_r = ΔΩ · d_r²，仅冲量使用。
     *
     * <p>ΔΩ = 4π / rayCount，与 {@link BlastRayGenerator#adaptiveRayCount(float)} 同源。</p>
     */
    public static float footprint(float distance, ExplosionParams p) {
        int rayCount = BlastRayGenerator.adaptiveRayCount(p.nearRadius());
        float deltaOmega = FOUR_PI / rayCount;
        return deltaOmega * distance * distance;
    }

    /**
     * 沉积能量 → 基准伤害 D = base_damage · min(ΔE / Φ₀, A_ref)。
     *
     * <p><b>为什么伤害也要钳。</b>{@link #rayIntensity} 的平台只钳住强度（穿深、冲量的 {@code √I}），
     * 而伤害读的是 ΔE —— 命中射线的能量份额之和。靶截获的射线数随立体角按 {@code 1/d²} 增长，
     * 因此 {@code ΔE/Φ₀ ∝ near_radius²/d²}，<b>平台对 ΔE 不起作用</b>：不加钳时包住爆点的靶取得
     * {@code ΔE→1}，即 {@code D = base_damage · 4π·near_radius²}（{@code near_radius=5} 时为 314 倍）。</p>
     *
     * <p><b>钳在哪里。</b>{@code ΔE/Φ₀} 就是"该目标截获的迎流面积"（m²，见设计文档 §7.2 的
     * {@code ΔE/Φ₀ = I·A}）。它的物理上界不可能超过目标自身的迎流面积 {@code A_ref}，
     * 而近场的发散正是这条不变量被 {@code 1/d²} 采样打破的结果。因此在<b>目标尺度</b>上取
     * {@code min(ΔE/Φ₀, A_ref)} 等价于"把 I 钳到 1"：内半径以内伤害恒为 {@code base_damage × A_ref}，
     * 与 {@code d = near_radius} 处的标定点连续相接；{@code d ≥ near_radius} 时
     * {@code ΔE/Φ₀ ≤ A_ref} 恒成立，<b>钳制不生效，远场结果与原实现逐位相同</b>。</p>
     *
     * <p><b>为什么不是逐射线加权。</b>把 {@code E_r·λ_r} 乘 {@code (d_r/near_radius)²} 也能在
     * 远场退化为 1、在近场压平，但它隐含"靶的截获面积按 {@code 1/d²} 无限增长"这一仅对<b>点靶</b>成立的
     * 假设。当靶的尺度与距离相当时（贴着爆点的方块、被爆点包住的实体），射线数已几何饱和，
     * 逐射线加权会过度补偿——方块在 d→0 处反而零伤害，形成"中心不破、外壳被削"的凹坑。
     * 端钳用目标自身的参考面积收口，不依赖该假设，故对三种目标一致成立。</p>
     *
     * @param deltaE        被击穿射线（一般实体按"到达"计）的<b>原始</b>能量之和
     * @param referenceAreaM2 目标参考迎流面积 A_ref（m²）：方块取 1 m² 标定面，实体取其 AABB
     *                        迎流面积，零件取其在装车坐标系下的包围盒最大截面。非有限或负值按 0 处理
     */
    public static float damage(float deltaE, ExplosionParams p, float referenceAreaM2) {
        float ratio = deltaE / referenceFlux(p);
        // NaN 与负的沉积能量一律归零，避免把非有限值送进伤害链
        if (!(ratio > 0f)) return 0f;
        float area = Float.isFinite(referenceAreaM2) ? Math.max(referenceAreaM2, 0f) : 0f;
        return p.baseDamage() * Math.min(ratio, area);
    }

    /**
     * 逐射线冲量项之和 → 冲量 J = base_impulse · Σ A_r·√I_r。
     *
     * @param sumFootprintSqrtI 全部命中射线的 {@code A_r·√I_r} 之和
     */
    public static float impulse(float sumFootprintSqrtI, ExplosionParams p) {
        return p.baseImpulse() * sumFootprintSqrtI;
    }
}
