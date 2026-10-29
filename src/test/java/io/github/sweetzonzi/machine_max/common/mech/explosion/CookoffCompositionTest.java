package io.github.sweetzonzi.machine_max.common.mech.explosion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CookoffComposition} 的合成规则与标定锚。
 *
 * <p>合成式：{@code D = Σn·d}、{@code S = Σn·j²}、{@code W = Σn·d·p}，
 * {@code J = √S}、{@code P = W/D}（{@code D = 0} 时 {@code P = 0}）；
 * 硬截断半径 {@code r₀·max(1, √(P/Pcut), √(D/Dcut), J/Jcut)}，{@code r₀ = 1 m}。</p>
 *
 * <p>7.62×54R 的标定锚取单发发射药贡献 {@code (p=0, d=0.1, j=1.0)}：
 * 1500 发 → 10 m、2000 发 → 约 11.5 m、100 发 → 约 2.6 m、500 发 → 约 5.8 m。</p>
 */
class CookoffCompositionTest {

    /** 第一方 7.62×54R 的单发发射药贡献：d = 0.1、j = 1.0，没有穿深通道。 */
    private static CookoffContribution propellant762() {
        return new CookoffContribution(0.0, 0.1, 1.0);
    }

    private static CookoffComposition compositionOf(CookoffContribution contribution, int count) {
        CookoffComposition composition = new CookoffComposition();
        composition.add(contribution, count);
        return composition;
    }

    // ==================== 合成式 ====================

    @Test
    void accumulatesDamageLinearlyAndImpulseByRootSumOfSquares() {
        CookoffComposition composition = new CookoffComposition();
        composition.add(new CookoffContribution(10.0, 1.0, 2.0), 3);
        composition.add(new CookoffContribution(20.0, 3.0, 1.0), 2);

        // D = 3·1 + 2·3
        assertEquals(9.0, composition.totalDamage(), 0.0);
        // J = √(3·2² + 2·1²)，不是各份冲量直接相加
        assertEquals(Math.sqrt(14.0), composition.impulse(), 0.0);
        // P = (3·1·10 + 2·3·20) / 9
        assertEquals(150.0 / 9.0, composition.penetration(), 1e-12);
        assertFalse(composition.isEmpty());
    }

    @Test
    void penetrationIsDamageWeightedAverageBetweenContributions() {
        CookoffComposition composition = new CookoffComposition();
        composition.add(new CookoffContribution(10.0, 1.0, 0.0));
        composition.add(new CookoffContribution(20.0, 3.0, 0.0));
        // (1·10 + 3·20) / (1 + 3) = 17.5
        assertEquals(17.5, composition.penetration(), 1e-12);
        assertEquals(4.0, composition.totalDamage(), 0.0);
    }

    @Test
    void sameRecipeKeepsPenetrationAndScalesDamageAndImpulse() {
        CookoffContribution contribution = new CookoffContribution(30.0, 0.5, 1.0);
        CookoffComposition once = compositionOf(contribution, 1);
        CookoffComposition many = compositionOf(contribution, 100);

        assertEquals(30.0, once.penetration(), 0.0);
        assertEquals(30.0, many.penetration(), 0.0);
        assertEquals(0.5, once.totalDamage(), 0.0);
        assertEquals(50.0, many.totalDamage(), 0.0);
        assertEquals(1.0, once.impulse(), 0.0);
        assertEquals(10.0, many.impulse(), 0.0);
    }

    @Test
    void addOnceEqualsAddWithCountOne() {
        CookoffContribution contribution = new CookoffContribution(5.0, 2.0, 3.0);
        CookoffComposition single = new CookoffComposition();
        single.add(contribution);
        CookoffComposition counted = compositionOf(contribution, 1);
        assertEquals(counted.totalDamage(), single.totalDamage(), 0.0);
        assertEquals(counted.impulse(), single.impulse(), 0.0);
        assertEquals(counted.penetration(), single.penetration(), 0.0);
    }

    @Test
    void zeroDamageContributionDoesNotEnterPenetrationAverage() {
        CookoffComposition composition = new CookoffComposition();
        composition.add(new CookoffContribution(100.0, 0.0, 5.0), 2);
        composition.add(new CookoffContribution(10.0, 1.0, 0.0));

        // 只有 d·p 项进 W：零伤害贡献不改变 P，但仍提供冲量
        assertEquals(10.0, composition.penetration(), 1e-12);
        assertEquals(1.0, composition.totalDamage(), 0.0);
        assertEquals(Math.sqrt(50.0), composition.impulse(), 1e-12);
    }

    @Test
    void zeroDamageMeansZeroPenetrationButCanStillHaveImpulse() {
        CookoffComposition composition = compositionOf(new CookoffContribution(60.0, 0.0, 4.0), 3);
        assertEquals(0.0, composition.totalDamage(), 0.0);
        assertEquals(0.0, composition.penetration(), 0.0);
        assertEquals(Math.sqrt(48.0), composition.impulse(), 1e-12);
        assertFalse(composition.isEmpty());
    }

    @Test
    void nullContributionAndNonPositiveCountAreNoOps() {
        CookoffComposition composition = new CookoffComposition();
        composition.add(null);
        composition.add(null, 5);
        composition.add(CookoffContribution.ZERO);
        composition.add(new CookoffContribution(10.0, 1.0, 1.0), 0);
        composition.add(new CookoffContribution(10.0, 1.0, 1.0), -3);

        assertTrue(composition.isEmpty());
        assertEquals(0.0, composition.totalDamage(), 0.0);
        assertEquals(0.0, composition.impulse(), 0.0);
        assertEquals(0.0, composition.penetration(), 0.0);
    }

    // ==================== isEmpty ====================

    @Test
    void emptyCompositionHasNoExplosion() {
        CookoffComposition composition = new CookoffComposition();
        assertTrue(composition.isEmpty());
        assertEquals(1.0, composition.maxRadius(), 0.0);
        assertEquals(0.0f, composition.particleScale(), 0.0f);
    }

    @Test
    void penetrationOnlyContributionIsAnEmptyComposition() {
        CookoffComposition composition = compositionOf(new CookoffContribution(60.0, 0.0, 0.0), 4);
        assertTrue(composition.isEmpty());
        assertEquals(0.0, composition.penetration(), 0.0);
        assertEquals(0.0, composition.impulse(), 0.0);
    }

    @Test
    void damageOrImpulseMakesTheCompositionNonEmpty() {
        assertFalse(compositionOf(new CookoffContribution(0.0, 0.1, 0.0), 1).isEmpty());
        assertFalse(compositionOf(new CookoffContribution(0.0, 0.0, 1.0), 1).isEmpty());
    }

    // ==================== maxRadius 标定锚 ====================

    @Test
    void beltRadiusAnchorsMatchTheCalibration() {
        // 1500 发 → 10 m
        assertEquals(10.0, compositionOf(propellant762(), 1500).maxRadius(), 1e-6);
        // 2000 发 → 约 11.5 m
        assertEquals(11.5, compositionOf(propellant762(), 2000).maxRadius(), 0.05);
        // 100 发 → 约 2.6 m
        assertEquals(2.6, compositionOf(propellant762(), 100).maxRadius(), 0.05);
        // 500 发 → 约 5.8 m
        assertEquals(5.8, compositionOf(propellant762(), 500).maxRadius(), 0.05);
    }

    @Test
    void radiusUsesTheDamageAndImpulseRatiosExactly() {
        // 两通道在同一标定锚上对齐：√(N·d/Dcut) = J/Jcut = √(N/15)，1500 发时都是 10
        assertEquals(Math.sqrt(1500.0 / 15.0),
                compositionOf(propellant762(), 1500).maxRadius(), 1e-9);
        assertEquals(Math.sqrt(100.0 / 15.0),
                compositionOf(propellant762(), 100).maxRadius(), 1e-9);
        assertEquals(Math.sqrt(500.0 / 15.0),
                compositionOf(propellant762(), 500).maxRadius(), 1e-9);
        assertEquals(Math.sqrt(2000.0 / 15.0),
                compositionOf(propellant762(), 2000).maxRadius(), 1e-9);
    }

    @Test
    void radiusFloorsAtOneMetreBelowFifteenRounds() {
        for (int rounds : new int[]{1, 2, 5, 10, 14}) {
            assertEquals(1.0, compositionOf(propellant762(), rounds).maxRadius(), 0.0, "N=" + rounds);
        }
        // N = 15 时两个通道恰好为 1，取 max 后仍是 1 m 地板
        assertEquals(1.0, compositionOf(propellant762(), 15).maxRadius(), 1e-12);
        // N = 16 起超过地板
        assertTrue(compositionOf(propellant762(), 16).maxRadius() > 1.0);
    }

    @Test
    void singleRoundNeverExtrapolatesToLargeRadius() {
        // 单发弹药不会被按量外推：半径由标定锚反推，不取“单发半径 × √N”
        assertEquals(1.0, compositionOf(propellant762(), 1).maxRadius(), 0.0);
    }

    @Test
    void calibrationConstantsMatchTheDesign() {
        assertEquals(1.0, CookoffComposition.REFERENCE_RADIUS, 0.0);
        assertEquals(100.0, CookoffComposition.P_CUT, 0.0);
        assertEquals(15.0 * 0.1, CookoffComposition.D_CUT, 0.0);
        assertEquals(Math.sqrt(1500.0) / 10.0, CookoffComposition.J_CUT, 0.0);
    }

    // ==================== particleScale ====================

    @Test
    void particleScaleIsCubeRootOfLargestChannelRatio() {
        // 伤害通道：D/Dv = 150/150 = 1
        assertEquals(1.0f, compositionOf(new CookoffContribution(0.0, 150.0, 0.0), 1).particleScale(), 1e-6f);
        // 冲量通道：J²/Jv² = 40²/40² = 1
        assertEquals(1.0f, compositionOf(new CookoffContribution(0.0, 0.0, 40.0), 1).particleScale(), 1e-6f);
        // 穿深通道：P/Pv = 100/100 = 1
        assertEquals(1.0f, compositionOf(new CookoffContribution(100.0, 1.0, 0.0), 1).particleScale(), 1e-6f);
    }

    @Test
    void particleScaleGrowsWithTheDominantChannel() {
        // 伤害主导：D/Dv = 1500/150 = 10
        assertEquals((float) Math.cbrt(10.0),
                compositionOf(new CookoffContribution(0.0, 1500.0, 0.0), 1).particleScale(), 1e-6f);
        // 冲量主导：J²/Jv² = 80²/40² = 4
        assertEquals((float) Math.cbrt(4.0),
                compositionOf(new CookoffContribution(0.0, 0.0, 80.0), 1).particleScale(), 1e-6f);
    }

    @Test
    void particleScaleTakesTheMaximumAcrossChannels() {
        CookoffComposition composition = new CookoffComposition();
        composition.add(new CookoffContribution(100.0, 1.0, 0.0)); // P/Pv = 1
        composition.add(new CookoffContribution(0.0, 0.0, 80.0));  // J²/Jv² = 4
        assertEquals((float) Math.cbrt(4.0), composition.particleScale(), 1e-6f);
    }

    @Test
    void calibrated762BeltHasUnitParticleScale() {
        assertEquals(1.0f, compositionOf(propellant762(), 1500).particleScale(), 1e-6f);
    }

    // ==================== 普通爆炸参数出口 ====================

    @Test
    void toExplosionParamsCarriesCompositionIntoStandardExplosion() {
        CookoffComposition composition = new CookoffComposition();
        composition.add(new CookoffContribution(12.0, 30.0, 9.0));
        ExplosionParams params = composition.toExplosionParams();

        assertEquals(12.0f, params.basePenetration(), 1e-6f);
        assertEquals(30.0f, params.baseDamage(), 1e-6f);
        assertEquals(9.0f, params.baseImpulse(), 1e-6f);
        assertEquals(1.0f, params.nearRadius(), 0.0f);
        assertEquals((float) composition.maxRadius(), params.maxRadius(), 1e-6f);
        assertEquals(20.0f, params.frontSpeed(), 0.0f);
        assertEquals(1, params.particles().size());
        assertEquals(CookoffComposition.BLAST_MEDIUM_PARTICLE, params.particles().get(0));
        assertEquals(composition.particleScale(), params.particleScale(), 0.0f);
        assertEquals(ExplosionParams.DEFAULT_DAMAGE_TYPE, params.damageType());
        assertFalse(params.causesFire());
    }
}
