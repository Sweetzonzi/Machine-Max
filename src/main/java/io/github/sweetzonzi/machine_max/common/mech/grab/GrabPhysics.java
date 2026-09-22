package io.github.sweetzonzi.machine_max.common.mech.grab;

import io.github.sweetzonzi.machine_max.common.registry.MMAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * 抓取系统的静态标定量与静态换算。
 * <p>
 * 无状态：只持有只读的标定量，换算都是纯函数。有效力量倍率与触及距离由属性现读，不在此缓存
 * （属性、装备、药水效果都可能在任何 tick 变化，见 docs/抓取系统-详细设计文档.md §5.2）。
 * <p>
 * 参数取值与推导见设计文档 §4（刚度、阻尼、力上限）、§7（抛掷）与 §9（参数总表）。
 */
public final class GrabPhysics {
    private GrabPhysics() {
    }

    // ==================== 标定量（设计文档 §9） ====================

    /** 参考力（N）：S_eff = 1 时玩家能施加的典型牵引力。 */
    public static final float F_REF = 1500f;
    /** 参考形变量（m）：形变量达到该值时牵引力等于 F_REF · S_eff。 */
    public static final float DELTA_REF = 1.0f;
    /** 平动允许的最大跟随角频率（rad/s），同时也是引擎自动裁剪的位置。 */
    public static final float OMEGA_MAX = 25f;
    /** 平动阻尼比（设计意图，不是写入值）。 */
    public static final float ZETA = 0.707f;
    /** 脱手防抖：手距连续超过阈值的物理步数。 */
    public static final int BREAK_DEBOUNCE_STEPS = 8;
    /** 冲量上限标定量（N·s）。 */
    public static final float I_REF = 390f;
    /** 参考离手速度（m/s）。 */
    public static final float V_REF = 13f;
    /** 移动速度对离手速度的幂次。 */
    public static final float GAMMA = 0.7f;
    /**
     * 物理标称步长（s）。物理层每主 tick 执行 baseStep(5) 次步进，每次步长 1/(5×20)，
     * 即 100 Hz；负载调节改变的是步数而不是步长，因此这是一个常量（设计文档 §4.1.1、§8.1）。
     */
    public static final float NOMINAL_STEP = 1f / 100f;
    /** 引擎刚度裁剪上限系数：ks ≤ m / (16·Δt²)。 */
    public static final float ENGINE_STIFFNESS_PER_KG = 1f / (16f * NOMINAL_STEP * NOMINAL_STEP);
    /** 引擎阻尼裁剪上限系数：kd ≤ m / Δt。 */
    public static final float ENGINE_DAMPING_PER_KG = 1f / NOMINAL_STEP;

    // ==================== 玩家侧换算（设计文档 §5） ====================

    /**
     * 计算玩家的有效抓取力量倍率：属性层 × 力量药水效果层。
     * <p>
     * 效果层在读取时相乘，不写入任何持久状态，因此不存在需要随效果到期而撤销的中间状态。
     * 力量药水 I 级提高 50%，II 级提高 100%。
     *
     * @param player 玩家
     * @return 有效力量倍率 S_eff
     */
    public static double grabStrength(Player player) {
        double base = player.getAttributeValue(MMAttributes.GRAB_STRENGTH);
        MobEffectInstance strength = player.getEffect(MobEffects.DAMAGE_BOOST);
        double potionFactor = strength == null ? 1.0 : 1.0 + 0.5 * (strength.getAmplifier() + 1);
        return base * potionFactor;
    }

    /**
     * 计算玩家的移动速度倍率 S_spd = 当前移动速度 / 该实体的移动速度基础值。
     * <p>
     * 必须按基础值归一化：移动速度属性的绝对值不是直观单位，且各版本的基础值会变化；
     * 用比值得到纯倍率，跨版本、跨实体都稳定（设计文档 §7.2）。
     *
     * @param player 玩家
     * @return 移动速度倍率，基础情况下为 1
     */
    public static float movementSpeedFactor(Player player) {
        double base = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        if (base <= 0) return 1f;
        return (float) (player.getAttributeValue(Attributes.MOVEMENT_SPEED) / base);
    }

    // ==================== 弹簧参数（设计文档 §4） ====================

    /**
     * 平动刚度 k = min(F_REF·S_eff/Δ_ref, ω_max²·m)，并额外受引擎裁剪上限约束。
     * <p>
     * 两个分支的物理含义：力受限分支适用于较重的零件，刚度由"在参考形变处产生参考力"标定；
     * 频率受限分支适用于很轻的零件，刚度随质量线性下降，保证跟随角频率恒为 ω_max。
     * 折算用被抓刚体自身质量，"重物拖不动"的观感来自关节网络而不是本公式（设计文档 §4.1）。
     *
     * @param mass     被抓刚体自身质量（kg）
     * @param strength 有效力量倍率 S_eff
     * @return 平动刚度（N/m）
     */
    public static float stiffness(float mass, double strength) {
        float forceLimited = (float) (F_REF * strength / DELTA_REF);
        float frequencyLimited = OMEGA_MAX * OMEGA_MAX * mass;
        float engineLimited = ENGINE_STIFFNESS_PER_KG * mass;
        return Math.min(Math.min(forceLimited, frequencyLimited), engineLimited);
    }

    /**
     * 平动阻尼系数 kd = 2·ζ·√(k·m)，并额外受引擎裁剪上限约束。
     * <p>
     * 求解代码把该参数当系数（与相对速度相乘）而不是阻尼比，因此必须用本式换算后再写
     * （设计文档 §4.2）。ζ 与质量和力量无关，kd 不是。
     *
     * @param stiffness 已算出的平动刚度（N/m）
     * @param mass      被抓刚体自身质量（kg）
     * @return 平动阻尼系数（N·s/m）
     */
    public static float damping(float stiffness, float mass) {
        float damping = 2f * ZETA * (float) Math.sqrt(stiffness * mass);
        return Math.min(damping, ENGINE_DAMPING_PER_KG * mass);
    }

    // ==================== 抛掷（设计文档 §7） ====================

    /**
     * 计算抛掷冲量 I = min(I_max, m_eff · v_hand)。
     * <p>
     * 重物受冲量项限制（越重扔得越慢），轻物受速度项限制（不可能比手快）；
     * 力量只抬高冲量上限，不改变轻物的离手速度。
     *
     * @param effectiveMass 有效质量（kg）：被抓装配体的总质量，未装配的零件即自身质量
     * @param strength      有效力量倍率 S_eff
     * @param speedFactor   移动速度倍率 S_spd
     * @return 冲量（N·s）
     */
    public static float throwImpulse(float effectiveMass, double strength, float speedFactor) {
        float impulseLimit = (float) (I_REF * strength);
        float speedLimit = effectiveMass * handReleaseSpeed(speedFactor);
        return Math.min(impulseLimit, speedLimit);
    }

    /**
     * 最大离手速度 v_hand = V_REF · S_spd^γ。
     *
     * @param speedFactor 移动速度倍率 S_spd
     * @return 最大离手速度（m/s）
     */
    public static float handReleaseSpeed(float speedFactor) {
        return V_REF * (float) Math.pow(Math.max(speedFactor, 0f), GAMMA);
    }
}
