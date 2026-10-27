package io.github.sweetzonzi.machine_max.mech.collision;

import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.collision.CollisionImpactModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 撞碎门槛的离线复现：接近速度、方向有效质量、预测冲量、材料分配与破块判定。
 *
 * <p>系数取 {@code docs/plan/计划中/碰撞系统-撞碎与压溃实现计划.md} §13.1 的起始值
 * （{@code C_J}=100 N·s/耐久点、{@code C_D}=0.003 HP/(N·s)、{@code approach_epsilon}=0.05 m/s、
 * 材质门 2.0 m/s），参考车型为 2S38 车体（15 t、RHA 25 mm、{@code rha} 材质
 * {@code block_damage_factor}=1.3）。方块护甲与耐久取 {@code ArmorUtil#getBlockArmor} 与
 * {@code DamageUtil#getMaxBlockDurability} 的现行输出：泥土 3.11 / 153、石头 33.6 / 226.5
 * （均已含一份背撑）、树叶 0.63 / 3.15。</p>
 */
class CollisionImpactModelTest {

    private static final float C_J = 100f;
    private static final float C_D = 0.003f;
    private static final float APPROACH_EPSILON = 0.05f;
    private static final float DEFAULT_MATERIAL_GATE = 2.0f;

    private static final float HULL_MASS = 15_000f;
    private static final float HULL_RHA = 25f;
    private static final float RHA_BLOCK_DAMAGE_FACTOR = 1.3f;

    private static final float DIRT_ARMOR = 3.11f;
    private static final float DIRT_DURABILITY = 153f;
    private static final float STONE_ARMOR = 33.6f;
    private static final float STONE_DURABILITY = 226.5f;
    private static final float LEAVES_ARMOR = 0.63f;
    private static final float LEAVES_DURABILITY = 3.15f;

    // ==================== 接近速度 ====================

    @Test
    void approachSpeedOnlyCountsClosingMotion() {
        Vector3f normal = new Vector3f(0f, 1f, 0f);
        assertEquals(3f, CollisionImpactModel.approachSpeed(new Vector3f(0f, -3f, 0f), normal), 1e-6f);
        assertEquals(0f, CollisionImpactModel.approachSpeed(new Vector3f(0f, 3f, 0f), normal), 1e-6f);
        assertEquals(0f, CollisionImpactModel.approachSpeed(Vector3f.ZERO, normal), 1e-6f);
    }

    @Test
    void approachSpeedIgnoresTangentialMotion() {
        Vector3f normal = new Vector3f(0f, 1f, 0f);
        assertEquals(0f, CollisionImpactModel.approachSpeed(new Vector3f(9f, 0f, 4f), normal), 1e-6f);
    }

    @Test
    void approachSpeedIsInvariantWhenBothSidesNegate() {
        Vector3f velocity = new Vector3f(-1.5f, 2.5f, -0.75f);
        Vector3f normal = new Vector3f(0.2f, -0.96f, 0f);
        float forward = CollisionImpactModel.approachSpeed(velocity, normal);
        float swapped = CollisionImpactModel.approachSpeed(velocity.negate(), normal.negate());
        assertEquals(forward, swapped, 1e-6f);
        assertTrue(forward > 0f);
    }

    @Test
    void approachSpeedSeesRotationalSurfaceVelocity() {
        // 质心平动为零，接触点仍因 ω × r 接近地面
        Vector3f r = new Vector3f(0f, 0f, 1f);
        Vector3f omega = new Vector3f(1f, 0f, 0f);
        Vector3f pointVelocity = omega.cross(r);
        assertEquals(1f, CollisionImpactModel.approachSpeed(pointVelocity, new Vector3f(0f, 1f, 0f)), 1e-6f);
    }

    // ==================== 方向有效质量 ====================

    @Test
    void normalEffectiveMassEqualsMassForCentralContact() {
        float mass = 1_200f;
        float effective = CollisionImpactModel.normalEffectiveMass(
                mass, Vector3f.ZERO, new Vector3f(0f, 1f, 0f), new Matrix3f());
        assertEquals(mass, effective, 1e-3f);
    }

    @Test
    void normalEffectiveMassDropsForOffsetContact() {
        Matrix3f inverseInertia = new Matrix3f();
        inverseInertia.set(0, 0, 1f / 1_000f);
        inverseInertia.set(1, 1, 1f / 1_000f);
        inverseInertia.set(2, 2, 1f / 1_000f);
        float effective = CollisionImpactModel.normalEffectiveMass(
                HULL_MASS, new Vector3f(2f, 0f, 0f), new Vector3f(0f, 1f, 0f), inverseInertia);
        // 杠杆项 (r × n)ᵀ · I⁻¹ · (r × n) = 4/1000 远大于 1/m
        assertEquals(1f / (1f / HULL_MASS + 4f / 1_000f), effective, 1e-2f);
        assertTrue(effective < HULL_MASS * 0.05f);
    }

    @Test
    void normalEffectiveMassDegeneratesForInvalidInput() {
        Matrix3f inverseInertia = new Matrix3f();
        Vector3f normal = new Vector3f(0f, 1f, 0f);
        assertEquals(0f, CollisionImpactModel.normalEffectiveMass(
                0f, Vector3f.ZERO, normal, inverseInertia), 0f);
        assertEquals(0f, CollisionImpactModel.normalEffectiveMass(
                Float.NaN, Vector3f.ZERO, normal, inverseInertia), 0f);
        assertEquals(0f, CollisionImpactModel.normalEffectiveMass(
                100f, null, normal, inverseInertia), 0f);
        assertEquals(0f, CollisionImpactModel.normalEffectiveMass(
                100f, Vector3f.ZERO, normal, null), 0f);
        assertEquals(0f, CollisionImpactModel.normalEffectiveMass(
                100f, new Vector3f(Float.NaN, 0f, 0f), normal, inverseInertia), 0f);
    }

    // ==================== 预测冲量 ====================

    @Test
    void predictedImpulseAppliesRestitutionBounce() {
        assertEquals(100f, CollisionImpactModel.predictedImpulse(10f, 10f, 0f), 1e-4f);
        assertEquals(200f, CollisionImpactModel.predictedImpulse(10f, 10f, 1f), 1e-4f);
        // 恢复系数越界按 [0, 1] 夹取
        assertEquals(100f, CollisionImpactModel.predictedImpulse(10f, 10f, -3f), 1e-4f);
        assertEquals(200f, CollisionImpactModel.predictedImpulse(10f, 10f, 7f), 1e-4f);
        // 非法恢复系数按完全非弹性处理，冲量不因此归零
        assertEquals(100f, CollisionImpactModel.predictedImpulse(10f, 10f, Float.NaN), 1e-4f);
    }

    // ==================== 资格门 ====================

    @Test
    void gateUsesTheLargerOfEpsilonAndMaterial() {
        assertEquals(DEFAULT_MATERIAL_GATE, CollisionImpactModel.impactGate(APPROACH_EPSILON, 2.0f), 1e-6f);
        assertEquals(3.0f, CollisionImpactModel.impactGate(APPROACH_EPSILON, 3.0f), 1e-6f);
        assertEquals(0.5f, CollisionImpactModel.impactGate(0.5f, 0.2f), 1e-6f);
        assertEquals(0.05f, CollisionImpactModel.impactGate(APPROACH_EPSILON, 0f), 1e-6f);
        assertEquals(0f, CollisionImpactModel.impactGate(Float.NaN, Float.NaN), 0f);
    }

    @Test
    void partPairGateTakesTheSmallerMaterialGate() {
        assertEquals(2.0f, CollisionImpactModel.pairImpactGate(2.0f, 3.0f), 1e-6f);
        assertEquals(3.0f, CollisionImpactModel.pairImpactGate(5.0f, 3.0f), 1e-6f);
        // 实际调用传入的是各侧 impactGate 结果，非法值按 0 参与比较
        assertEquals(0f, CollisionImpactModel.pairImpactGate(-4f, 1.0f), 1e-6f);
    }

    @Test
    void ordinaryImpactQualifiesAtOrAboveTheGate() {
        assertTrue(CollisionImpactModel.reachesGate(2.0f, 2.0f));
        assertFalse(CollisionImpactModel.reachesGate(1.999f, 2.0f));
        assertTrue(CollisionImpactModel.reachesGate(3.0f, Float.NaN));
    }

    @Test
    void instantBranchNeedsStrictlyMoreThanEpsilon() {
        assertTrue(CollisionImpactModel.exceedsEpsilon(0.06f, APPROACH_EPSILON));
        assertFalse(CollisionImpactModel.exceedsEpsilon(APPROACH_EPSILON, APPROACH_EPSILON));
        assertFalse(CollisionImpactModel.exceedsEpsilon(0f, APPROACH_EPSILON));
        assertTrue(CollisionImpactModel.exceedsEpsilon(0.001f, 0f));
    }

    // ==================== 耐久与破块门槛 ====================

    @Test
    void supportedDurabilityAddsHalfOfTheBackBlock() {
        assertEquals(153f, CollisionImpactModel.supportedDurability(102f, 102f), 1e-4f);
        assertEquals(102f, CollisionImpactModel.supportedDurability(102f, 0f), 1e-4f);
        assertEquals(102f, CollisionImpactModel.supportedDurability(102f, -50f), 1e-4f);
        assertEquals(0f, CollisionImpactModel.supportedDurability(Float.NaN, Float.NaN), 0f);
    }

    @Test
    void breakImpulseScalesLinearlyWithDurability() {
        assertEquals(15_300f, CollisionImpactModel.breakImpulse(C_J, DIRT_DURABILITY), 1e-2f);
        assertEquals(22_650f, CollisionImpactModel.breakImpulse(C_J, STONE_DURABILITY), 1e-2f);
    }

    @Test
    void breakImpulseRefusesToBreakOnInvalidNumbers() {
        assertFalse(Float.isFinite(CollisionImpactModel.breakImpulse(Float.NaN, DIRT_DURABILITY)));
        assertFalse(Float.isFinite(CollisionImpactModel.breakImpulse(0f, DIRT_DURABILITY)));
        assertFalse(Float.isFinite(CollisionImpactModel.breakImpulse(C_J, 0f)));
        assertFalse(Float.isFinite(CollisionImpactModel.breakImpulse(C_J, Float.NaN)));
        assertFalse(CollisionImpactModel.meetsBreakThreshold(1e9f, Float.POSITIVE_INFINITY));
    }

    // ==================== 材料分配 ====================

    @Test
    void blockShareFollowsTheArmorRatio() {
        assertEquals(0.5f, CollisionImpactModel.blockShare(0f, 0f), 1e-6f);
        // 零件没有防护时方块几乎不承担作用量，软质部件撞不碎硬块
        assertEquals(0f, CollisionImpactModel.blockShare(0f, 12f), 1e-6f);
        assertEquals(1f, CollisionImpactModel.blockShare(50f, 0f), 1e-6f);
        // 防护更高的一方承受更小的损伤：零件 RHA 25 对石头护甲 33.6
        assertEquals(25f / (25f + 33.6f), CollisionImpactModel.blockShare(HULL_RHA, STONE_ARMOR), 1e-6f);
    }

    @Test
    void contributionsSplitTheSameImpulse() {
        float share = CollisionImpactModel.blockShare(HULL_RHA, DIRT_ARMOR);
        float impulse = 20_000f;
        float block = CollisionImpactModel.blockContribution(RHA_BLOCK_DAMAGE_FACTOR, share, impulse);
        float part = CollisionImpactModel.partContribution(share, impulse);
        assertEquals(1.3f * share * impulse, block, 1e-3f);
        assertEquals((1f - share) * impulse, part, 1e-3f);
        assertTrue(block + part > impulse); // 方块系数只放大破障贡献，不参与自伤分配
    }

    @Test
    void materialGuardsPreventNonFiniteDamage() {
        assertEquals(0f, CollisionImpactModel.blockContribution(Float.NaN, 0.5f, 100f), 0f);
        assertEquals(0f, CollisionImpactModel.blockContribution(1f, Float.NaN, 100f), 0f);
        assertEquals(0f, CollisionImpactModel.partContribution(Float.NaN, 100f), 0f);
        assertEquals(0f, CollisionImpactModel.collisionDamage(C_D, Float.NaN), 0f);
        assertEquals(0f, CollisionImpactModel.collisionDamage(Float.NaN, 100f), 0f);
    }

    @Test
    void damageConversionIsLinearInPartImpulse() {
        float share = CollisionImpactModel.blockShare(HULL_RHA, DIRT_ARMOR);
        float part = CollisionImpactModel.partContribution(share, 20_000f);
        assertEquals(C_D * part, CollisionImpactModel.collisionDamage(C_D, part), 1e-6f);
        assertEquals(0f, CollisionImpactModel.collisionDamage(C_D, 0f), 0f);
    }

    // ==================== 求解器冲量读值 ====================

    @Test
    void onlyPositiveFiniteSolverImpulseCounts() {
        assertTrue(CollisionImpactModel.isSolverImpulse(1e-3f));
        assertFalse(CollisionImpactModel.isSolverImpulse(0f));
        assertFalse(CollisionImpactModel.isSolverImpulse(-0.1f));
        assertFalse(CollisionImpactModel.isSolverImpulse(Float.NaN));
        assertFalse(CollisionImpactModel.isSolverImpulse(Float.POSITIVE_INFINITY));
    }

    // ==================== 参考车型门槛表 ====================

    @Test
    void dirtBreaksJustBelowOneMetrePerSecond() {
        assertEquals(0.9f, requiredSpeed(DIRT_ARMOR, DIRT_DURABILITY), 0.05f);
    }

    @Test
    void stoneNeedsRoughlyTwoPointSevenMetresPerSecond() {
        assertEquals(2.7f, requiredSpeed(STONE_ARMOR, STONE_DURABILITY), 0.05f);
    }

    @Test
    void leavesBreakBelowTheDefaultGate() {
        float required = requiredSpeed(LEAVES_ARMOR, LEAVES_DURABILITY);
        assertTrue(required < DEFAULT_MATERIAL_GATE,
                "树叶门槛应低于默认材质门，普通撞击在 2 m/s 之前就应当由即时分支处理");
        assertTrue(required < 0.1f);
    }

    @Test
    void doublingHullMassHalfsTheRequiredSpeed() {
        float single = requiredSpeedForMass(HULL_MASS, DIRT_ARMOR, DIRT_DURABILITY);
        float doubled = requiredSpeedForMass(HULL_MASS * 2f, DIRT_ARMOR, DIRT_DURABILITY);
        assertEquals(single / 2f, doubled, 1e-3f);
    }

    @Test
    void stoneNeedsMoreImpulseThanDirtForTheSameHull() {
        float dirtImpulse = requiredImpulse(DIRT_ARMOR, DIRT_DURABILITY);
        float stoneImpulse = requiredImpulse(STONE_ARMOR, STONE_DURABILITY);
        assertTrue(stoneImpulse > dirtImpulse * 2f);
    }

    /**
     * §13.1 参考车型的破块门槛速度：{@code u = J_break / J_hat}，接触取自车体中心
     * （无杠杆项），恢复系数 0。
     */
    private static float requiredSpeed(float blockArmor, float blockDurability) {
        return requiredSpeedForMass(HULL_MASS, blockArmor, blockDurability);
    }

    private static float requiredSpeedForMass(float hullMass, float blockArmor, float blockDurability) {
        float impulse = requiredImpulse(blockArmor, blockDurability);
        float effectiveMass = CollisionImpactModel.normalEffectiveMass(
                hullMass, Vector3f.ZERO, new Vector3f(0f, 1f, 0f), new Matrix3f());
        return impulse / effectiveMass;
    }

    private static float requiredImpulse(float blockArmor, float blockDurability) {
        float share = CollisionImpactModel.blockShare(HULL_RHA, blockArmor);
        float breakImpulse = CollisionImpactModel.breakImpulse(C_J, blockDurability);
        return breakImpulse / (RHA_BLOCK_DAMAGE_FACTOR * share);
    }
}
