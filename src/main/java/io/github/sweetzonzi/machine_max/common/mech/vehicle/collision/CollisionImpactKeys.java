package io.github.sweetzonzi.machine_max.common.mech.vehicle.collision;

import net.minecraft.core.BlockPos;

/**
 * 撞碎与压溃的候选分组键。
 *
 * <p>键只在一个物理子步内有效：对象身份取本步有效的刚体 native ID，仅作 Java 索引使用，
 * 不用于延迟解引用原生对象。接触点先在 Processed 登记资格，再在 Post 的一次枚举中按这些键
 * 归组，因此同一物理点不会被重复计账，双方的两次回调也只产生一个 {@link PartPairKey}。</p>
 *
 * <p>方块坐标域必须显式区分：原版地形用世界 {@link BlockPos}，Create 装置用装置本地
 * {@link BlockPos}，后者不能与前者合并去重。</p>
 */
public final class CollisionImpactKeys {

    private CollisionImpactKeys() {
    }

    /**
     * 目标方块所属的坐标域。
     */
    public enum TerrainDomain {
        /** 原版世界地形，坐标是 {@link BlockPos} 的世界坐标。 */
        VANILLA,
        /** Create 装置，坐标是装置本地坐标。 */
        CREATE
    }

    /**
     * 被撞击方块的目标身份：地形侧刚体身份 + 该坐标域内的方块坐标。
     *
     * <p>两个实现都是本文件的嵌套记录，因此 {@code sealed} 不需要显式 {@code permits} 列表。</p>
     */
    public sealed interface TargetBlockKey {

        /** 该目标的坐标域。 */
        TerrainDomain domain();

        /**
         * 原版地形方块。
         *
         * @param sectionBodyId 承载该方块的区块物理对象 native ID
         * @param worldPos      世界方块坐标
         */
        record Vanilla(long sectionBodyId, BlockPos worldPos) implements TargetBlockKey {
            @Override
            public TerrainDomain domain() {
                return TerrainDomain.VANILLA;
            }
        }

        /**
         * Create 装置方块。
         *
         * @param contraptionBodyId 装置的物理对象 native ID
         * @param localPos          装置本地方块坐标，不是世界坐标
         */
        record Create(long contraptionBodyId, BlockPos localPos) implements TargetBlockKey {
            @Override
            public TerrainDomain domain() {
                return TerrainDomain.CREATE;
            }
        }
    }

    /**
     * 地形撞击组：同一零件刚体、同一 HitBox、同一目标方块。
     *
     * <p>资格按组判定：本步任一点通过速度门，该组本步所有有效接触的冲量都进入统计，
     * 但只对解析到同一目标的点生效——同一 manifold 中的其他方块不能借这个资格。</p>
     *
     * @param partBodyId   零件刚体 native ID
     * @param hitBoxIndex  接触点对应的子形状索引
     * @param target       被撞方块身份
     */
    public record TerrainImpactKey(long partBodyId, int hitBoxIndex, TargetBlockKey target) {
    }

    /**
     * 零件间撞击组：两个刚体与各自子形状索引的规范序对。
     *
     * <p>物理引擎会让双方各回调一次，{@link #ordered} 把两次调用折叠成同一个键，
     * 物理作用量只分发一次，再分别计算双方承受的贡献。</p>
     */
    public record PartPairKey(long firstBodyId, int firstHitBoxIndex,
                              long secondBodyId, int secondHitBoxIndex) {

        /**
         * 按 {@code (bodyId, hitBoxIndex)} 字典序排列两端后构造键。
         *
         * @param bodyIdA   一侧零件刚体 native ID
         * @param hitBoxA   该侧子形状索引
         * @param bodyIdB   另一侧零件刚体 native ID
         * @param hitBoxB   该侧子形状索引
         * @return 与调用顺序无关的组键
         */
        public static PartPairKey ordered(long bodyIdA, int hitBoxA, long bodyIdB, int hitBoxB) {
            boolean aFirst = bodyIdA < bodyIdB || (bodyIdA == bodyIdB && hitBoxA <= hitBoxB);
            return aFirst
                    ? new PartPairKey(bodyIdA, hitBoxA, bodyIdB, hitBoxB)
                    : new PartPairKey(bodyIdB, hitBoxB, bodyIdA, hitBoxA);
        }
    }

    /**
     * 部件 Post 伤害投递组：同一零件刚体、同一 HitBox、同一来源类别。
     *
     * @param partBodyId   承受伤害的零件刚体 native ID
     * @param hitBoxIndex  接触点对应的子形状索引
     * @param source       伤害来源类别
     */
    public record PartDamageKey(long partBodyId, int hitBoxIndex, ImpactSource source) {
    }

    /**
     * 部件伤害的来源类别；不同类别各自累计，不合并成一份冲击。
     */
    public enum ImpactSource {
        /** 与方块的撞击。 */
        TERRAIN,
        /** 与另一个零件的撞击。 */
        PART
    }
}
