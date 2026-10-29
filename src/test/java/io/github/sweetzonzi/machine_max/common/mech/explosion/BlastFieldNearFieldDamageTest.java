package io.github.sweetzonzi.machine_max.common.mech.explosion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BlastField#damage(float, ExplosionParams, float)} 的近场钳制契约。
 *
 * <p>验收口径（{@code near_radius = r}）：无遮挡、完全击穿的 1 m² 靶在任意 {@code d ≤ r} 处
 * 都应得到 {@code base_damage × 1 m²}，{@code d > r} 处按 {@code (r/d)²} 衰减；
 * 包住爆点的靶不超过 {@code base_damage × A_ref}。远场必须与原实现逐位相同。</p>
 */
class BlastFieldNearFieldDamageTest {

    private static final float R = 5f;
    private static final float BASE = 22f;
    /** 参考场强 Φ₀ = 1/(4π r²)，即"每 1 m² 的参考能量"，其倒数为 4πr² = 314.16 m²。 */
    private static final float PHI0 = 1f / (4f * (float) Math.PI * R * R);

    private static ExplosionParams params() {
        return new ExplosionParams(
                10f, BASE, 800f, R, 20f, 20f,
                true, false, false, List.of(), 1f, null, null);
    }

    /**
     * 无遮挡的迎流面积 area、距离 d 的靶：命中射线能量和 = A/(4π d²)，且不可能超过总能量 1。
     */
    private static float deltaEForPlate(float area, float distance) {
        float raw = area / (4f * (float) Math.PI * distance * distance);
        return Math.min(1f, raw);
    }

    private static float damage(float area, float distance) {
        return BlastField.damage(deltaEForPlate(area, distance), params(), area);
    }

    @Test
    void referenceRadiusReproducesCalibration() {
        assertEquals(BASE, damage(1f, R), 1e-3f, "1 m² 靶在 near_radius 处必须等于 base_damage");
    }

    @Test
    void nearFieldIsClampedToReferenceArea() {
        for (float d : new float[]{0.1f, 0.5f, 1f, 2.5f, 4.9f, R}) {
            assertEquals(BASE, damage(1f, d), 1e-2f * BASE,
                    "d=" + d + " 时 1 m² 靶的伤害必须被钳在标定值（未钳时 d=0.5 为 2200）");
        }
    }

    @Test
    void farFieldIsUnchangedByTheClamp() {
        float area = 1f;
        for (float d : new float[]{6f, 8f, 10f}) {
            float expected = BASE * area * (R * R) / (d * d);
            assertEquals(expected, damage(area, d), 1e-2f * expected);
        }
    }

    @Test
    void engulfedTargetIsClampedToItsOwnReferenceArea() {
        // 靶包住爆点：原始 ΔE 达到总能量上限 1.0，未钳时 D = base/Φ₀ = 6912
        float aRef = 0.6f * 1.8f;                       // 玩家迎流截面
        float damage = BlastField.damage(1f, params(), aRef);
        assertEquals(BASE * aRef, damage, 1e-2f);
        assertTrue(damage < BASE / PHI0 / 250f,
                "加权后 0 距离必须远低于未钳的 base·4π r² = " + (BASE / PHI0));
    }

    @Test
    void damageNeverExceedsBaseTimesReferenceArea() {
        float aRef = 0.35f;
        float previous = -1f;
        for (float deltaE = 0f; deltaE <= 1.0f; deltaE += 0.05f) {
            float damage = BlastField.damage(deltaE, params(), aRef);
            assertTrue(damage <= BASE * aRef + 1e-4f, "伤害上限必须为 base × A_ref");
            assertTrue(damage >= previous, "伤害对沉积能量必须单调不减");
            previous = damage;
        }
    }

    @Test
    void degenerateInputsProduceZeroDamage() {
        assertEquals(0f, BlastField.damage(Float.NaN, params(), 1f), 0f);
        assertEquals(0f, BlastField.damage(-0.5f, params(), 1f), 0f);
        assertEquals(0f, BlastField.damage(0.5f, params(), 0f), 0f, "参考面积为 0 时不得产生伤害");
        assertEquals(0f, BlastField.damage(0.5f, params(), Float.NaN), 0f);
        assertEquals(0f, BlastField.damage(0.5f, params(), -2f), 0f);
    }
}
