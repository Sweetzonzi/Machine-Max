package io.github.sweetzonzi.machine_max.util.mechanic;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.MaterialAttr;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import static java.lang.Math.cos;
import static java.lang.Math.log10;

/**
 * 此类中集中收纳了本模组与护甲有关的机理公式，方便管理与调用
 *
 * @author 甜粽子
 */
public class ArmorUtil {

    // ==================== 跳弹与碾压常量 ====================

    /** 跳弹起始角（与法线夹角，度）—— 小于此角不可能跳弹 */
    public static final float RICOCHET_MIN_ANGLE = 45f;
    /** 跳弹峰值角（与法线夹角，度）—— 到达此角跳弹概率为 100%（不计碾压时） */
    public static final float RICOCHET_MAX_ANGLE = 90f;

    /** 口径碾压系数：口径 / 装甲RHA > 此值时免疫跳弹 */
    private static final float CALIBER_OVERMATCH_RATIO = 2f;
    /** 穿深碾压系数：有效穿深 / 装甲RHA > 此值时免疫跳弹 */
    private static final float PEN_OVERMATCH_RATIO = 1.5f;

    // ==================== 跳弹与碾压 ====================

    /**
     * 角度因子：入射角在 [{@link #RICOCHET_MIN_ANGLE}, {@link #RICOCHET_MAX_ANGLE}] 间线性映射到 [0, 1]。
     * 不在该区间内时 clamp 到边界。
     */
    private static float computeAngleFactor(float impactAngleDeg) {
        if (impactAngleDeg <= RICOCHET_MIN_ANGLE) return 0f;
        if (impactAngleDeg >= RICOCHET_MAX_ANGLE) return 1f;
        return (impactAngleDeg - RICOCHET_MIN_ANGLE) / (RICOCHET_MAX_ANGLE - RICOCHET_MIN_ANGLE);
    }

    /**
     * 碾压判定：口径或穿深远超装甲厚度时，弹体可无视跳弹直接击穿。
     * <p>
     * 双维度独立检查，满足任一即碾压：
     * <ul>
     *   <li>口径维度：弹丸口径 &gt; {@link #CALIBER_OVERMATCH_RATIO} × 装甲RHA</li>
     *   <li>穿深维度：有效穿深 &gt; {@link #PEN_OVERMATCH_RATIO} × 装甲RHA</li>
     * </ul>
     *
     * @param caliberMm   弹丸口径（mm）
     * @param effectivePen 目标侧修正后的有效穿深（mm RHA）
     * @param armorRha     目标装甲 RHA（mm）
     * @return true 表示碾压——免疫跳弹
     */
    public static boolean isOvermatched(float caliberMm, float effectivePen, float armorRha) {
        if (armorRha <= 0) return true;
        if (caliberMm > armorRha * CALIBER_OVERMATCH_RATIO) return true;
        if (effectivePen > armorRha * PEN_OVERMATCH_RATIO) return true;
        return false;
    }

    /**
     * 跳弹概率判定：基于入射角计算概率，碾压时直接免疫跳弹。
     * <p>
     * 判定逻辑：
     * <ol>
     *   <li>入射角 ≤ {@link #RICOCHET_MIN_ANGLE} → 不可能跳弹</li>
     *   <li>{@link #isOvermatched} → 碾压免跳</li>
     *   <li>否则按线性角度因子随机判定</li>
     * </ol>
     *
     * @param impactAngleDeg 入射角（与法线夹角，度），0=直射 90=掠射
     * @param caliberMm      弹丸口径（mm）
     * @param effectivePen   目标侧修正后的有效穿深（mm RHA）
     * @param armorRha       目标装甲 RHA（mm）
     * @return true 表示跳弹
     */
    public static boolean shouldRicochet(float impactAngleDeg, float caliberMm, float effectivePen,
                                          float armorRha) {
        if (impactAngleDeg <= RICOCHET_MIN_ANGLE) return false;
        if (isOvermatched(caliberMm, effectivePen, armorRha)) return false;
        float ricochetProb = computeAngleFactor(impactAngleDeg);
        return (float) Math.random() < ricochetProb;
    }

    // ==================== 等效护甲 ====================

    /**
     * 角度效应允许的最小入射余弦。
     * <p>
     * 掠射时 {@code 1/cos} 发散，超过约 78.5°（cos = 0.2）后等效厚度不再继续增大，
     * 后续的防护由跳弹判定承担。
     */
    public static final float MIN_ANGLE_COS = 0.2f;

    /**
     * 计算入射角（弧度制）——速度方向与命中面法线的夹角，0 表示垂直入射。
     * <p>
     * 取绝对值，因此从背面命中与从正面命中得到相同的角度。
     * 任一方量为零（例如原版伤害没有弹道信息）时返回 0，即按垂直入射处理。
     *
     * @param velocity 命中速度方向（世界坐标，无需归一化）
     * @param normal   命中面法线（世界坐标，无需归一化）
     * @return 入射角弧度，范围 [0, π/2]；缺少几何信息时为 0
     */
    public static float getImpactAngle(Vec3 velocity, Vec3 normal) {
        if (velocity == null || normal == null) return 0f;
        double velocityLenSqr = velocity.lengthSqr();
        double normalLenSqr = normal.lengthSqr();
        if (velocityLenSqr < 1e-12 || normalLenSqr < 1e-12) return 0f;
        double cosTheta = Math.abs(velocity.dot(normal)) / Math.sqrt(velocityLenSqr * normalLenSqr);
        if (Double.isNaN(cosTheta)) return 0f;
        return (float) Math.acos(Math.clamp(cosTheta, 0.0, 1.0));
    }

    /**
     * 考虑入射角的影响，计算不同入射角下的等效护甲水平。
     * <p>
     * 公式为 {@code armor / max(|cos(angle)|, }{@link #MIN_ANGLE_COS}{@code )}，
     * 角度为 NaN 时按垂直入射处理。
     *
     * @param pArmor 基础护甲水平
     * @param pAngle 弧度制入射角(投射物速度方向与装甲法线方向的夹角)
     * @return 等效护甲水平
     */
    public static float getEquivalentArmor(float pArmor, float pAngle) {
        double cosTheta = Math.abs(cos(pAngle));
        if (!Double.isFinite(cosTheta)) return pArmor;
        return (float) (pArmor / Math.max(cosTheta, MIN_ANGLE_COS));
    }

    /**
     * 获取方块护甲水平，护甲公式为：100*lg(0.1+爆炸抗性)*(0.1+方块硬度)
     *
     * @return 给定方块的护甲水平
     */
    public static float getBlockArmor(BlockGetter level, BlockState blockState, BlockPos blockPos) {
        return (float) (100f * (log10(blockState.getBlock().getExplosionResistance() + 1) * (log10(1.001+blockState.getDestroySpeed(level, blockPos)))));
    }

    // ==================== 材质装甲系数 ====================

    /**
     * 耐久衰减后的抗穿系数：
     * {@code decay_rha + (rha − decay_rha) × ratio^decay_power}。
     *
     * <p>{@code rha} 与 {@code decay_rha} 都是相对厚度的系数，毫米数由调用方的厚度提供
     * （碰撞箱用 {@link io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.HitBoxAttr#thickness}，
     * 履带用 {@code thickness_mm}）。</p>
     *
     * @param armor 材质装甲属性
     * @param ratio 当前耐久比例，0~1
     * @return 抗穿系数
     */
    public static float rhaCoeff(MaterialAttr.ArmorAttr armor, float ratio) {
        float clamped = Math.clamp(ratio, 0f, 1f);
        return armor.decay_rha() + (armor.rha() - armor.decay_rha())
                * (float) Math.pow(clamped, armor.decay_power());
    }

    /**
     * 等效护甲厚度（mm RHA）：{@code thicknessMm × rhaCoeff(ratio)}。
     *
     * @param armor       材质装甲属性
     * @param thicknessMm 装甲厚度（毫米）
     * @param ratio       当前耐久比例，0~1
     * @return 等效护甲厚度
     */
    public static float rha(MaterialAttr.ArmorAttr armor, float thicknessMm, float ratio) {
        return thicknessMm * rhaCoeff(armor, ratio);
    }

    /**
     * 耐久衰减后的线性减伤：{@code decay_dmg_resist + (dmg_resist − decay_dmg_resist) × ratio^decay_power}。
     *
     * @param armor 材质装甲属性
     * @param ratio 当前耐久比例，0~1
     * @return 线性减伤值
     */
    public static float resist(MaterialAttr.ArmorAttr armor, float ratio) {
        float clamped = Math.clamp(ratio, 0f, 1f);
        return armor.decay_dmg_resist() + (armor.dmg_resist() - armor.decay_dmg_resist())
                * (float) Math.pow(clamped, armor.decay_power());
    }

    /**
     * 耐久衰减后的伤害乘数：{@code decay_dmg_mult + (dmg_mult − decay_dmg_mult) × ratio^decay_power}。
     *
     * @param armor 材质装甲属性
     * @param ratio 当前耐久比例，0~1
     * @return 伤害乘数
     */
    public static float mult(MaterialAttr.ArmorAttr armor, float ratio) {
        float clamped = Math.clamp(ratio, 0f, 1f);
        return armor.decay_dmg_mult() + (armor.dmg_mult() - armor.decay_dmg_mult())
                * (float) Math.pow(clamped, armor.decay_power());
    }
}
