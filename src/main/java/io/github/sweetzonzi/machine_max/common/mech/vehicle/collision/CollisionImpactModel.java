package io.github.sweetzonzi.machine_max.common.mech.vehicle.collision;

import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;

/**
 * 撞碎与压溃的纯算术部分：接近速度、方向有效质量、预测冲量、耐久门槛、双方材料分配与伤害换算。
 *
 * <p>本类不读取配置、不访问物理世界、不登记候选，也没有状态：服务端配置值与材质值
 * 都由调用方作为参数传入，因此物理线程与离线测试走的是同一段算术。</p>
 *
 * <p>口径：法线 {@code n} 由对方指向零件；{@code J} 是一个物理子步内的法向冲量（N·s），
 * 不是 {@code J/dt}；{@code u} 是沿 {@code n} 的接近速度（m/s）。</p>
 *
 * <p>所有函数对 {@code NaN}、无穷与负值取退化而不抛异常：无法给出可信读数的输入一律按
 * “不产生作用量”处理，由调用方自行记录诊断计数。</p>
 */
public final class CollisionImpactModel {

    /** 数值守卫的统一下限：小于该值的和式按零处理。 */
    public static final float NUMERIC_EPSILON = 1.0e-6f;

    private CollisionImpactModel() {
    }

    /**
     * 把输入夹成有限的非负值；{@code NaN}、无穷与负数都变成 0。
     */
    public static float nonNegative(float value) {
        return Float.isFinite(value) && value > 0f ? value : 0f;
    }

    /**
     * 接近速度：{@code u = max(0, -dot(v_rel, n))}。
     *
     * <p>法线由对方指向零件，因此只有沿法线反向的运动算作接近。分离、静止与非法值都返回 0。
     * 调用方须传入单位法线，并把接触点相对速度（含 {@code ω × r} 项）作为 {@code relativeVelocity}。</p>
     *
     * @param relativeVelocity 零件表面速度减去对方表面速度（世界坐标，m/s）
     * @param normalOnPart     由对方指向零件的单位法线
     * @return 接近速度，单位 m/s，非负
     */
    public static float approachSpeed(Vector3f relativeVelocity, Vector3f normalOnPart) {
        if (relativeVelocity == null || normalOnPart == null) return 0f;
        float dot = relativeVelocity.dot(normalOnPart);
        return Float.isFinite(dot) ? -Math.min(dot, 0f) : 0f;
    }

    /**
     * 沿法线的方向有效质量：{@code 1/m_n = 1/m + (r × n)ᵀ · I⁻¹ · (r × n)}。
     *
     * <p>只描述接触刚体自身及其转动惯量；不并入整车质量、接地点个数或任何连接件。
     * 质量非正、杠杆或惯量矩阵无法给出有限读数时返回 0，调用方据此跳过该点。</p>
     *
     * @param mass                刚体质量（kg）
     * @param rFromCenterOfMass   刚体质心指向接触点的向量（世界坐标，m）
     * @param normal              由对方指向零件的单位法线
     * @param inverseInertiaWorld 刚体的世界系逆惯量张量
     * @return 方向有效质量（kg），非负
     */
    public static float normalEffectiveMass(float mass, Vector3f rFromCenterOfMass,
                                            Vector3f normal, Matrix3f inverseInertiaWorld) {
        if (!Float.isFinite(mass) || mass <= 0f) return 0f;
        if (rFromCenterOfMass == null || normal == null || inverseInertiaWorld == null) return 0f;
        Vector3f lever = rFromCenterOfMass.cross(normal);
        Vector3f angularMomentum = inverseInertiaWorld.mult(lever, new Vector3f());
        if (!isFinite(lever) || !isFinite(angularMomentum)) return 0f;
        float inverseMass = 1f / mass + angularMomentum.dot(lever);
        if (!Float.isFinite(inverseMass) || inverseMass <= 0f) return 0f;
        return 1f / inverseMass;
    }

    /**
     * 单点预测冲量：{@code J_hat = (1 + e) · m_n · u}。
     *
     * <p>这是“孤立接触若完全阻挡本体所需作用量”的预测，不是求解器已经施加的冲量。
     * 恢复系数夹在 {@code [0, 1]}；非法输入返回 0。</p>
     *
     * @param normalEffectiveMass 方向有效质量（kg）
     * @param approachSpeed       接近速度（m/s）
     * @param restitution         本次接触的合成恢复系数
     * @return 预测法向冲量（N·s），非负
     */
    public static float predictedImpulse(float normalEffectiveMass, float approachSpeed, float restitution) {
        float massTerm = nonNegative(normalEffectiveMass);
        float speedTerm = nonNegative(approachSpeed);
        float bounce = Float.isFinite(restitution) ? Math.clamp(restitution, 0f, 1f) : 0f;
        float impulse = (1f + bounce) * massTerm * speedTerm;
        return Float.isFinite(impulse) ? impulse : 0f;
    }

    /**
     * 普通撞击的资格速度门：{@code gate = max(approachEpsilon, materialMinSpeed)}。
     *
     * <p>{@code approachEpsilon} 来自服务端配置，{@code materialMinSpeed} 来自接触材质
     * （{@code physics.impact_min_speed}）。负值与非法值按 0 参与比较。</p>
     *
     * @param approachEpsilon     服务端配置的接近速度下限（m/s）
     * @param materialMinSpeed    材质声明的撞击门槛（m/s）
     * @return 本次接触的资格速度门（m/s），非负
     */
    public static float impactGate(float approachEpsilon, float materialMinSpeed) {
        return Math.max(nonNegative(approachEpsilon), nonNegative(materialMinSpeed));
    }

    /**
     * 普通撞击资格判定：{@code u >= gate}。
     */
    public static boolean reachesGate(float approachSpeed, float gate) {
        return nonNegative(approachSpeed) >= nonNegative(gate);
    }

    /**
     * 即时破坏分支的速度条件：{@code u > approachEpsilon}。
     *
     * <p>该分支不受材质门限制，只用配置的浮点噪声下限作为唯一门槛；相等不算越过。</p>
     */
    public static boolean exceedsEpsilon(float approachSpeed, float approachEpsilon) {
        return nonNegative(approachSpeed) > nonNegative(approachEpsilon);
    }

    /**
     * 带背撑的方块耐久：{@code D* = D_target + 0.5 · D_back}。
     *
     * <p>没有有效支撑时调用方传 0。不可破坏的背撑不能把负硬度代进耐久函数，
     * 调用方须先把它折算成 {@code D_target} 再调用。</p>
     *
     * @param targetDurability 目标方块自身的耐久
     * @param backDurability   紧邻后方的支撑方块耐久
     * @return 用于比较的有效耐久，非负
     */
    public static float supportedDurability(float targetDurability, float backDurability) {
        return nonNegative(targetDurability) + 0.5f * nonNegative(backDurability);
    }

    /**
     * 方块耐久门槛：{@code J_break = C_J · D*}。
     *
     * <p>未定义系数或没有有效耐久时返回正无穷，保证“读不到数字”不会变成“随便就碎”。</p>
     *
     * @param durabilityPerImpulse C_J（N·s / 耐久点）
     * @param supportedDurability  带背撑的有效耐久
     * @return 破块所需的法向冲量（N·s）
     */
    public static float breakImpulse(float durabilityPerImpulse, float supportedDurability) {
        float coefficient = nonNegative(durabilityPerImpulse);
        float durability = nonNegative(supportedDurability);
        if (coefficient <= NUMERIC_EPSILON || durability <= NUMERIC_EPSILON) return Float.POSITIVE_INFINITY;
        float impulse = coefficient * durability;
        return Float.isFinite(impulse) ? impulse : Float.POSITIVE_INFINITY;
    }

    /**
     * 方块承担的冲量份额：{@code w_block = A_p / (A_p + A_b)}。
     *
     * <p>{@code A_p} 为零件防护（RHA），{@code A_b} 为方块护甲；双方都为零时取 0.5。
     * 防护较高的一方承受较小的碰撞损伤。</p>
     */
    public static float blockShare(float partRha, float blockArmor) {
        float partSide = nonNegative(partRha);
        float blockSide = nonNegative(blockArmor);
        float sum = partSide + blockSide;
        return sum > NUMERIC_EPSILON ? partSide / sum : 0.5f;
    }

    /**
     * 方块贡献：{@code I_block = max(0, blockDamageFactor) · w_block · J}。
     *
     * <p>{@code blockDamageFactor} 只调破障贡献，不放大对部件自身的伤害。
     * 份额非法时该点作废，不产生任何一侧的作用量。</p>
     */
    public static float blockContribution(float blockDamageFactor, float blockShare, float impulse) {
        if (!Float.isFinite(blockShare)) return 0f;
        return nonNegative(blockDamageFactor) * Math.clamp(blockShare, 0f, 1f) * nonNegative(impulse);
    }

    /**
     * 部件自承贡献：{@code I_part = (1 - w_block) · J}，与方块贡献共用同一次分配。
     *
     * <p>份额非法时与 {@link #blockContribution} 一样作废，避免同一份作用量只落到一侧。</p>
     */
    public static float partContribution(float blockShare, float impulse) {
        if (!Float.isFinite(blockShare)) return 0f;
        return (1f - Math.clamp(blockShare, 0f, 1f)) * nonNegative(impulse);
    }

    /**
     * 部件碰撞伤害：{@code C_D · I_part}（线性第一版）。
     */
    public static float collisionDamage(float damagePerImpulse, float partImpulse) {
        return nonNegative(damagePerImpulse) * nonNegative(partImpulse);
    }

    /**
     * 方块是否达到破块门槛：{@code I_block >= J_break}。
     *
     * <p>聚合时先加总各点的 {@code I_block} 再调用本方法，不要先套某一点的材料再比。</p>
     */
    public static boolean meetsBreakThreshold(float accumulatedBlockContribution, float breakImpulse) {
        if (!Float.isFinite(breakImpulse)) return false;
        return nonNegative(accumulatedBlockContribution) >= nonNegative(breakImpulse);
    }

    /**
     * 本步求解器是否真的对本点施加了法向冲量：有限且为正。
     *
     * <p>新点的读数是 0，被禁用或未被求解的点保持历史缓存；CCD 预测接触没有解算冲量。
     * 这三种情况都不贡献普通撞击。</p>
     */
    public static boolean isSolverImpulse(float appliedImpulse) {
        return Float.isFinite(appliedImpulse) && appliedImpulse > 0f;
    }

    private static boolean isFinite(Vector3f vector) {
        return Float.isFinite(vector.x) && Float.isFinite(vector.y) && Float.isFinite(vector.z);
    }
}
