package io.github.sweetzonzi.machinemax.util.mechanic;

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
        return (float) (100f * (log10(blockState.getBlock().getExplosionResistance() + 1) * (log10(1.01+blockState.getDestroySpeed(level, blockPos)))));
    }
}
