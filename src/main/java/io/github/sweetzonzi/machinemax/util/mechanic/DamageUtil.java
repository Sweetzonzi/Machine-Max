package io.github.sweetzonzi.machinemax.util.mechanic;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 此类中集中收纳了本模组与伤害有关的机理公式，方便管理与调用
 * @author 甜粽子
*/
public class DamageUtil {
    /**
     * 计算实体投射物对护盾的伤害
     * @param pDamage 投射物伤害
     * @param pMultiplier 护盾伤害倍率
     * @param pPenetrationIndex 护盾穿透系数
     * @return 对护盾造成的实际伤害
     */
    public static double BulletDamage2Shield(float pDamage, float pMultiplier, float pPenetrationIndex){
        return pDamage*pMultiplier*(1F-pPenetrationIndex);
    }
    /**
     * 计算实体投射物对生命值的伤害
     * @param pDamage 投射物伤害
     * @return 对生命值造成的实际伤害
     */
    public static double BulletDamage2Health(float pDamage){
        return pDamage;
    }

    public static float getMaxBlockDurability(Level level, BlockState blockState, BlockPos blockPos){
        float blockDurability;
        //软质吸能地面方块更不易被破坏，特殊处理沙土雪等软质地面方块的耐久度
        if (blockState.is(BlockTags.DIRT) || blockState.is(BlockTags.SNOW) || blockState.is(BlockTags.SAND)) {
            blockDurability = 200 * (0.01f + blockState.getDestroySpeed(level, blockPos));
        } else if (blockState.is(BlockTags.WOOL)) {//吸能材料超高耐久度
            blockDurability = 200 * (0.01f + blockState.getDestroySpeed(level, blockPos));
        } else if (blockState.is(BlockTags.LEAVES)) {//脆弱
            blockDurability = 10 * (0.01f + blockState.getDestroySpeed(level, blockPos));
        } else if (blockState.isStickyBlock()) {
            blockDurability = 1000f;
        } else {//一般方块
            blockDurability = 100 * (0.01f + blockState.getDestroySpeed(level, blockPos));
        }
        AABB aabb = blockState.getCollisionShape(level, blockPos).bounds();
        //根据方块体积调整耐久度
        blockDurability *= (float) (0.25f + 0.75f * aabb.getXsize() * aabb.getYsize() * aabb.getZsize());
        return blockDurability;
    }
}
