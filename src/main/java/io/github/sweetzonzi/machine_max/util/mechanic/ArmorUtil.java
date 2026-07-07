package io.github.sweetzonzi.machine_max.util.mechanic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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
     * 考虑入射角的影响，计算不同入射角下的等效护甲水平
     *
     * @param pArmor 基础护甲水平
     * @param pAngle 弧度制入射角(投射物速度方向与装甲法线方向的夹角)
     * @return 等效护甲水平
     */
    public static float getEquivalentArmor(float pArmor, float pAngle) {
        if (Double.isNaN(cos(pAngle))) return Float.MAX_VALUE;
        else return (float) (pArmor / Math.abs(cos(pAngle)));
    }

    /**
     * 获取方块护甲水平，护甲公式为：100*lg(0.1+爆炸抗性)*(0.1+方块硬度)
     *
     * @return 给定方块的护甲水平
     */
    public static float getBlockArmor(Level level, BlockState blockState, BlockPos blockPos) {
        return (float) (100f * (log10(blockState.getBlock().getExplosionResistance() + 1) * (log10(1.001+blockState.getDestroySpeed(level, blockPos)))));
    }
}
