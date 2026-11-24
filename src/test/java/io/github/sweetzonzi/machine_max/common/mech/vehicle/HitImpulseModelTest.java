package io.github.sweetzonzi.machine_max.common.mech.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 命中冲量规律的离线复现：线性段、质量缩放的速度渐近线、标度不变性与退化输入。
 *
 * <p>系数取服务端配置的默认值（{@code C_hit}=6 N·s、{@code C_melee}=80 N·s、{@code V_hit}=5 m/s、
 * {@code C_blast}=3000 N·s、{@code V_blast}=25 m/s），参考件是桑塔纳车门的 30 耐久的 24 kg 门板、
 * 吉普车体的 200 耐久 550 kg 与 700 耐久 12 t 的车体。规律本身由
 * {@link HitImpulseModel} 表达，测试只断言它对外承诺的性质，不复述实现。</p>
 */
class HitImpulseModelTest {

    private static final float C_HIT = 6f;
    private static final float C_MELEE = 80f;
    private static final float V_HIT = 5f;
    private static final float C_BLAST = 3_000f;
    private static final float V_BLAST = 25f;

    private static final float DOOR_DURABILITY = 30f;
    private static final float DOOR_MASS = 24f;

    private static final float JEEP_DURABILITY = 200f;
    private static final float JEEP_MASS = 550f;

    private static final float TANK_DURABILITY = 700f;
    private static final float TANK_MASS = 12_000f;

    // ==================== 线性段与量级 ====================

    /**
     * 小比值时冲量就是 {@code C·r}：命中之间的强弱次序由伤害比决定，不被上限搅乱。
     */
    @Test
    void smallRatiosStayLinearInTheDamageRatio() {
        float damage = 0.3f; // r = 0.01
        float impulse = HitImpulseModel.impulse(damage, DOOR_DURABILITY, DOOR_MASS, C_HIT, V_HIT);
        float linear = C_HIT * (damage / DOOR_DURABILITY);
        assertEquals(linear, impulse, linear * 0.01f);
    }

    /**
     * 一发 5 点伤害的手枪弹打在 24 kg / 30 耐久的车门上约 1 N·s：门内板会被推得转一点，
     * 但远不足以撞到限位。
     */
    @Test
    void pistolRoundOnACarDoorStaysAroundOneNewtonSecond() {
        float impulse = HitImpulseModel.impulse(5f, DOOR_DURABILITY, DOOR_MASS, C_HIT, V_HIT);
        assertEquals(0.992f, impulse, 0.005f);
    }

    /**
     * 原版 TNT 近炸（约 40 点）打在 550 kg / 200 耐久的车体上：575 N·s，约 1.05 m/s。
     */
    @Test
    void vanillaTntOnAJeepHullLandsAtAboutOneMetrePerSecond() {
        float impulse = HitImpulseModel.impulse(40f, JEEP_DURABILITY, JEEP_MASS, C_BLAST, V_BLAST);
        assertEquals(574.9f, impulse, 0.5f);
        assertEquals(1.045f, impulse / JEEP_MASS, 0.002f);
    }

    /**
     * 高伤害绑在轻件上会进入饱和段：1200 点伤害打在车门上给 80 N·s（3.3 m/s），
     * 而不是线性的 240 N·s（10 m/s）。
     */
    @Test
    void heavyHitsSaturateAgainstTheMassScaledCeiling() {
        float impulse = HitImpulseModel.impulse(1_200f, DOOR_DURABILITY, DOOR_MASS, C_HIT, V_HIT);
        assertEquals(80f, impulse, 0.05f);
        assertEquals(3.333f, impulse / DOOR_MASS, 0.002f);
        // 线性外推会给出 10 m/s，饱和后必须明显低于它
        assertTrue(impulse < C_HIT * (1_200f / DOOR_DURABILITY) * 0.5f);
    }

    // ==================== 渐近线与单调性 ====================

    /**
     * 渐近线 {@code m·V} 永不到达，但可以无限逼近：上限长在规律里，不是从外面裁的一刀。
     */
    @Test
    void impulseApproachesButNeverReachesTheAsymptote() {
        float asymptote = DOOR_MASS * V_HIT;
        float huge = HitImpulseModel.impulse(30f * 1.0e6f, DOOR_DURABILITY, DOOR_MASS, C_HIT, V_HIT);
        assertTrue(huge < asymptote);
        assertTrue(huge > asymptote * 0.999f);

        // 更极端的比值仍然只逼近渐近线；单精度在这里与大数相消，允许它落在渐近线上
        float absurd = HitImpulseModel.impulse(3.0e13f, DOOR_DURABILITY, DOOR_MASS, C_HIT, V_HIT);
        assertTrue(absurd >= huge);
        assertTrue(absurd <= asymptote * 1.0001f);
    }

    /**
     * 冲量随伤害严格单调递增：排序不能被上限折断，否则 20 点与 5000 点伤害会给出同一发冲量。
     */
    @Test
    void impulseIsStrictlyMonotonicInDamage() {
        float[] damages = {0.5f, 2f, 5f, 20f, 100f, 1_000f, 1.0e5f, 1.0e8f};
        float previous = 0f;
        for (float damage : damages) {
            float impulse = HitImpulseModel.impulse(damage, DOOR_DURABILITY, DOOR_MASS, C_HIT, V_HIT);
            assertTrue(impulse > previous,
                    "伤害 " + damage + " 的冲量应大于更低伤害的冲量，实际 " + impulse + " <= " + previous);
            previous = impulse;
        }
    }

    // ==================== 质量缩放 ====================

    /**
     * 同一发命中打更重的件，冲量更大而速度增量更小：上限按质量缩放，
     * 轻小部件与沉重零部件不共享同一个天花板。
     */
    @Test
    void heavierPartsTakeMoreImpulseButLessSpeed() {
        float damage = 100f;
        float light = HitImpulseModel.impulse(damage, 100f, 20f, C_HIT, V_HIT);
        float heavy = HitImpulseModel.impulse(damage, 100f, 2_000f, C_HIT, V_HIT);
        assertTrue(heavy > light);
        assertTrue(heavy / 2_000f < light / 20f);
    }

    /**
     * 爆炸推重车几乎没有速度增量：原版 TNT 打 12 t / 700 耐久车体只有 0.014 m/s。
     */
    @Test
    void blastBarelyMovesAHeavyHull() {
        float impulse = HitImpulseModel.impulse(40f, TANK_DURABILITY, TANK_MASS, C_BLAST, V_BLAST);
        assertEquals(171.4f, impulse, 0.5f);
        assertTrue(impulse / TANK_MASS < 0.02f);
    }

    // ==================== 标度不变性 ====================

    /**
     * 伤害与耐久同乘一个系数时冲量不变：内容包整包换标度不会把载具变成炮弹。
     */
    @Test
    void scalingDamageAndDurabilityTogetherKeepsTheImpulse() {
        float baseline = HitImpulseModel.impulse(5f, DOOR_DURABILITY, DOOR_MASS, C_HIT, V_HIT);
        float scaled = HitImpulseModel.impulse(500f, DOOR_DURABILITY * 100f, DOOR_MASS, C_HIT, V_HIT);
        assertEquals(baseline, scaled, 1e-6f);

        float blastBaseline = HitImpulseModel.impulse(40f, JEEP_DURABILITY, JEEP_MASS, C_BLAST, V_BLAST);
        float blastScaled = HitImpulseModel.impulse(4_000f, JEEP_DURABILITY * 100f, JEEP_MASS, C_BLAST, V_BLAST);
        assertEquals(blastBaseline, blastScaled, 1e-3f);
    }

    /**
     * 伤害放大而耐久不动（内容包只调伤害）时冲量确实变强，但被速度渐近线兜住。
     */
    @Test
    void damageOnlyInflationIsHeldByTheCeiling() {
        float normal = HitImpulseModel.impulse(40f, JEEP_DURABILITY, JEEP_MASS, C_BLAST, V_BLAST);
        float inflated = HitImpulseModel.impulse(4_000f, JEEP_DURABILITY, JEEP_MASS, C_BLAST, V_BLAST);
        assertTrue(inflated > normal);
        assertTrue(inflated < JEEP_MASS * V_BLAST);
    }

    // ==================== 近战行 ====================

    /**
     * 近战行只换斜率：同一个伤害值下，一刀给的冲量是弹丸的十余倍。
     */
    @Test
    void meleeStrikeOutweighsAProjectileOfTheSameDamage() {
        float projectile = HitImpulseModel.impulse(6f, DOOR_DURABILITY, DOOR_MASS, C_HIT, V_HIT);
        float sword = HitImpulseModel.impulse(6f, DOOR_DURABILITY, DOOR_MASS, C_MELEE, V_HIT);
        assertTrue(sword > projectile * 10f);
        assertTrue(sword < DOOR_MASS * V_HIT);
    }

    /**
     * 近战行的标定锚点：空手、铁剑与满配暴击斧砍在 24 kg / 30 耐久的车门上。
     * 三个值分别对应“几乎没有反应”“砍得动但推不开”“接近驱动器转速但不撞限位”。
     */
    @Test
    void meleeAnchorsOnTheCarDoor() {
        assertEquals(2.61f, HitImpulseModel.impulse(1f, DOOR_DURABILITY, DOOR_MASS, C_MELEE, V_HIT), 0.05f);
        assertEquals(14.12f, HitImpulseModel.impulse(6f, DOOR_DURABILITY, DOOR_MASS, C_MELEE, V_HIT), 0.1f);
        // 锋利 V + 力量 II + 暴击 ≈ 24.75 点
        assertEquals(42.6f, HitImpulseModel.impulse(24.75f, DOOR_DURABILITY, DOOR_MASS, C_MELEE, V_HIT), 0.3f);
    }

    // ==================== 各行参数共用一条规律 ====================

    /**
     * 爆炸与普通命中只差斜率与渐近线：同一个比值下爆炸给得更多，但同样受自身渐近线约束。
     */
    @Test
    void blastAndImpactShareTheSameLawWithDifferentCoefficients() {
        float damage = 30f;
        float hit = HitImpulseModel.impulse(damage, DOOR_DURABILITY, DOOR_MASS, C_HIT, V_HIT);
        float blast = HitImpulseModel.impulse(damage, DOOR_DURABILITY, DOOR_MASS, C_BLAST, V_BLAST);
        assertTrue(blast > hit);
        assertTrue(blast < DOOR_MASS * V_BLAST);
        // 轻件上的爆炸已经饱和，落在渐近线附近
        assertTrue(blast > DOOR_MASS * V_BLAST * 0.8f);
    }

    // ==================== 退化输入 ====================

    /**
     * 无法给出可信读数的输入一律按“不产生冲量”处理，不抛异常也不返回负数。
     */
    @Test
    void invalidInputProducesNoImpulse() {
        assertEquals(0f, HitImpulseModel.impulse(0f, DOOR_DURABILITY, DOOR_MASS, C_HIT, V_HIT), 0f);
        assertEquals(0f, HitImpulseModel.impulse(-5f, DOOR_DURABILITY, DOOR_MASS, C_HIT, V_HIT), 0f);
        assertEquals(0f, HitImpulseModel.impulse(Float.NaN, DOOR_DURABILITY, DOOR_MASS, C_HIT, V_HIT), 0f);
        assertEquals(0f, HitImpulseModel.impulse(5f, 0f, DOOR_MASS, C_HIT, V_HIT), 0f);
        assertEquals(0f, HitImpulseModel.impulse(5f, Float.NaN, DOOR_MASS, C_HIT, V_HIT), 0f);
        assertEquals(0f, HitImpulseModel.impulse(5f, DOOR_DURABILITY, 0f, C_HIT, V_HIT), 0f);
        assertEquals(0f, HitImpulseModel.impulse(5f, DOOR_DURABILITY, DOOR_MASS, 0f, V_HIT), 0f);
        assertEquals(0f, HitImpulseModel.impulse(5f, DOOR_DURABILITY, DOOR_MASS, C_HIT, 0f), 0f);
        assertEquals(0f, HitImpulseModel.impulse(5f, DOOR_DURABILITY, DOOR_MASS, C_HIT, Float.NaN), 0f);
    }

    /**
     * 极端但有限的数值不产生 {@code NaN} 或无穷：外部模组的伤害值是信任边界。
     */
    @Test
    void extremeFiniteInputsStayFinite() {
        float hugeDamage = HitImpulseModel.impulse(Float.MAX_VALUE, 1e-20f, 1e-20f, C_HIT, V_HIT);
        assertTrue(Float.isFinite(hugeDamage));
        assertTrue(hugeDamage >= 0f);

        float hugeMass = HitImpulseModel.impulse(5f, DOOR_DURABILITY, Float.MAX_VALUE, C_HIT, V_HIT);
        assertTrue(Float.isFinite(hugeMass));
    }
}
