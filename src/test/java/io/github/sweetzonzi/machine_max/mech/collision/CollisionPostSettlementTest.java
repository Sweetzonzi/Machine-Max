package io.github.sweetzonzi.machine_max.mech.collision;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.collision.CollisionPostSettlement;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 碰撞 Post 结算器的分组键与坐标域语义。
 *
 * <p>键决定“同一块 / 同一部件 / 同一组”如何归并，串组会让同一份冲量被重复计账或让不同方块
 * 互借资格。整轮 manifold 枚举需要真实物理世界与地形快照，在游戏内验收，不在这里伪造。</p>
 */
class CollisionPostSettlementTest {

    @Test
    void targetBlockKeySeparatesCoordinateDomains() {
        BlockPos pos = new BlockPos(3, 64, -7);
        CollisionPostSettlement.TargetBlockKey vanilla =
                new CollisionPostSettlement.TargetBlockKey.Vanilla(9L, pos);
        CollisionPostSettlement.TargetBlockKey create =
                new CollisionPostSettlement.TargetBlockKey.Create(9L, pos);
        assertEquals(CollisionPostSettlement.TerrainDomain.VANILLA, vanilla.domain());
        assertEquals(CollisionPostSettlement.TerrainDomain.CREATE, create.domain());
        assertNotEquals(vanilla, create);
        assertNotEquals(vanilla, new CollisionPostSettlement.TargetBlockKey.Vanilla(10L, pos));
    }

    @Test
    void terrainImpactKeySeparatesHitBoxesAndTargets() {
        BlockPos pos = new BlockPos(0, 70, 0);
        CollisionPostSettlement.TerrainImpactKey key = new CollisionPostSettlement.TerrainImpactKey(
                41L, 1, new CollisionPostSettlement.TargetBlockKey.Vanilla(9L, pos));
        assertEquals(key, new CollisionPostSettlement.TerrainImpactKey(
                41L, 1, new CollisionPostSettlement.TargetBlockKey.Vanilla(9L, new BlockPos(0, 70, 0))));
        assertNotEquals(key, new CollisionPostSettlement.TerrainImpactKey(
                41L, 2, new CollisionPostSettlement.TargetBlockKey.Vanilla(9L, pos)));
        assertNotEquals(key, new CollisionPostSettlement.TerrainImpactKey(
                42L, 1, new CollisionPostSettlement.TargetBlockKey.Vanilla(9L, pos)));
    }

    @Test
    void partDamageKeySeparatesHitBoxes() {
        assertEquals(
                new CollisionPostSettlement.PartDamageKey(41L, 1, CollisionPostSettlement.ImpactSource.TERRAIN),
                new CollisionPostSettlement.PartDamageKey(41L, 1, CollisionPostSettlement.ImpactSource.TERRAIN));
        assertNotEquals(
                new CollisionPostSettlement.PartDamageKey(41L, 1, CollisionPostSettlement.ImpactSource.TERRAIN),
                new CollisionPostSettlement.PartDamageKey(41L, 2, CollisionPostSettlement.ImpactSource.TERRAIN));
    }
}
