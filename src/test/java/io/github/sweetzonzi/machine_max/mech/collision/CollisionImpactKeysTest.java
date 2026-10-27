package io.github.sweetzonzi.machine_max.mech.collision;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.collision.CollisionImpactKeys;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 候选分组键的规范序与坐标域隔离。
 *
 * <p>物理引擎对同一次接触会回调双方各一次，{@link CollisionImpactKeys.PartPairKey#ordered}
 * 必须把两次调用折叠成同一个键；不同子形状、不同坐标域则必须保持区分，否则会串组。
 */
class CollisionImpactKeysTest {

    @Test
    void partPairKeyIgnoresCallbackOrder() {
        CollisionImpactKeys.PartPairKey forward =
                CollisionImpactKeys.PartPairKey.ordered(41L, 2, 17L, 5);
        CollisionImpactKeys.PartPairKey backward =
                CollisionImpactKeys.PartPairKey.ordered(17L, 5, 41L, 2);
        assertEquals(forward, backward);
        assertEquals(new CollisionImpactKeys.PartPairKey(17L, 5, 41L, 2), forward);
    }

    @Test
    void partPairKeySeparatesHitBoxesOnOneBody() {
        CollisionImpactKeys.PartPairKey left = CollisionImpactKeys.PartPairKey.ordered(41L, 2, 41L, 5);
        CollisionImpactKeys.PartPairKey right = CollisionImpactKeys.PartPairKey.ordered(41L, 5, 41L, 2);
        assertEquals(left, right);
        assertNotEquals(left, CollisionImpactKeys.PartPairKey.ordered(41L, 2, 41L, 3));
    }

    @Test
    void targetBlockKeySeparatesCoordinateDomains() {
        BlockPos pos = new BlockPos(3, 64, -7);
        CollisionImpactKeys.TargetBlockKey vanilla =
                new CollisionImpactKeys.TargetBlockKey.Vanilla(9L, pos);
        CollisionImpactKeys.TargetBlockKey create =
                new CollisionImpactKeys.TargetBlockKey.Create(9L, pos);
        assertEquals(CollisionImpactKeys.TerrainDomain.VANILLA, vanilla.domain());
        assertEquals(CollisionImpactKeys.TerrainDomain.CREATE, create.domain());
        assertNotEquals(vanilla, create);
        assertNotEquals(vanilla,
                new CollisionImpactKeys.TargetBlockKey.Vanilla(10L, pos));
    }

    @Test
    void terrainImpactKeySeparatesHitBoxesAndTargets() {
        BlockPos pos = new BlockPos(0, 70, 0);
        CollisionImpactKeys.TerrainImpactKey key = new CollisionImpactKeys.TerrainImpactKey(
                41L, 1, new CollisionImpactKeys.TargetBlockKey.Vanilla(9L, pos));
        assertEquals(key, new CollisionImpactKeys.TerrainImpactKey(
                41L, 1, new CollisionImpactKeys.TargetBlockKey.Vanilla(9L, new BlockPos(0, 70, 0))));
        assertNotEquals(key, new CollisionImpactKeys.TerrainImpactKey(
                41L, 2, new CollisionImpactKeys.TargetBlockKey.Vanilla(9L, pos)));
        assertNotEquals(key, new CollisionImpactKeys.TerrainImpactKey(
                42L, 1, new CollisionImpactKeys.TargetBlockKey.Vanilla(9L, pos)));
    }

    @Test
    void partDamageKeySeparatesSources() {
        assertEquals(
                new CollisionImpactKeys.PartDamageKey(41L, 1, CollisionImpactKeys.ImpactSource.TERRAIN),
                new CollisionImpactKeys.PartDamageKey(41L, 1, CollisionImpactKeys.ImpactSource.TERRAIN));
        assertNotEquals(
                new CollisionImpactKeys.PartDamageKey(41L, 1, CollisionImpactKeys.ImpactSource.TERRAIN),
                new CollisionImpactKeys.PartDamageKey(41L, 1, CollisionImpactKeys.ImpactSource.PART));
    }
}
