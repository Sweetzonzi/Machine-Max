package io.github.sweetzonzi.machine_max.common.gametest;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import com.simsilica.mathd.Vec3d;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldSnapshotPawnGameTest {
    private WorldSnapshotPawnGameTest() {
    }

    @GameTest(template = "part_foundation", batch = "mmWorldSnapshotPawn", timeoutTicks = 80)
    public static void ordinaryEntityProxyIsRayQueryable(GameTestHelper helper) {
        BlockPos spawn = helper.absolutePos(new BlockPos(2, 2, 2));
        var pig = EntityType.PIG.create(helper.getLevel());
        helper.assertTrue(pig != null, "pig entity can be created");
        pig.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        helper.getLevel().addFreshEntity(pig);

        helper.runAfterDelay(10, () -> {
            Object bodyObject = ((PhysicsHost) (Object) pig).getPhysicsBody("body");
            helper.assertTrue(bodyObject instanceof PhysicsRigidBody,
                    "ordinary entity has its PAWN physics proxy");
            var body = (PhysicsRigidBody) bodyObject;
            helper.assertTrue(body.getCollisionGroup() == CollisionGroups.PAWN,
                    "the entity proxy retains its PAWN collision group");
            helper.assertTrue(body.getCollideWithGroups() == CollisionGroups.NONE,
                    "the entity proxy remains non-responsive in the main physics world");

            var center = pig.getBoundingBox().getCenter();
            var hits = SparkLevel.getPhysicsLevel(helper.getLevel()).getWorld().getWorldSnapshot()
                    .rayTestDp(new Vec3d(center.x - 2, center.y, center.z),
                            new Vec3d(center.x + 2, center.y, center.z), new java.util.ArrayList<>());
            boolean found = hits.stream().anyMatch(hit ->
                    PhysicsBodyExtensionKt.getOwner(hit.getCollisionObject()) == pig);
            helper.assertTrue(found, "snapshot ray query returns the original entity owner");

            var halfExtents = new Vector3f(pig.getBbWidth() * 0.5f,
                    pig.getBbHeight() * 0.5f, pig.getBbWidth() * 0.5f);
            var query = new PhysicsGhostObject(new BoxCollisionShape(halfExtents));
            query.setPhysicsLocationDp(new Vec3d(center.x, center.y, center.z));
            query.setCollisionGroup(CollisionGroups.PAWN);
            query.setCollideWithGroups(CollisionGroups.PAWN);
            boolean[] overlapsPig = {false};
            SparkLevel.getPhysicsLevel(helper.getLevel()).getWorld().getWorldSnapshot()
                    .contactTest(query, event -> {
                        PhysicsCollisionObject other = event.getObjectA() == query
                                ? event.getObjectB() : event.getObjectA();
                        if (PhysicsBodyExtensionKt.getOwner(other) == pig) overlapsPig[0] = true;
                    });
            helper.assertTrue(overlapsPig[0],
                    "a query mask containing PAWN finds the proxy although its response mask is NONE");
            helper.succeed();
        });
    }

    @GameTest(template = "part_foundation", batch = "mmWorldSnapshotPawn", timeoutTicks = 100)
    public static void removedEntityProxyLeavesSnapshot(GameTestHelper helper) {
        BlockPos spawn = helper.absolutePos(new BlockPos(2, 2, 2));
        var pig = EntityType.PIG.create(helper.getLevel());
        helper.assertTrue(pig != null, "pig entity can be created");
        pig.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        helper.getLevel().addFreshEntity(pig);

        helper.runAfterDelay(10, () -> {
            Object bodyObject = ((PhysicsHost) (Object) pig).getPhysicsBody("body");
            helper.assertTrue(bodyObject instanceof PhysicsRigidBody,
                    "ordinary entity has its PAWN physics proxy before removal");
            var body = (PhysicsRigidBody) bodyObject;
            var center = pig.getBoundingBox().getCenter();
            var query = new PhysicsGhostObject(new BoxCollisionShape(new Vector3f(1, 1, 1)));
            query.setPhysicsLocationDp(new Vec3d(center.x, center.y, center.z));
            query.setCollisionGroup(CollisionGroups.PAWN);
            query.setCollideWithGroups(CollisionGroups.PAWN);
            var snapshot = SparkLevel.getPhysicsLevel(helper.getLevel()).getWorld().getWorldSnapshot();
            PhysicsCollisionObject[] snapshotProxy = {null};
            snapshot.contactTest(query, event -> {
                PhysicsCollisionObject a = event.getObjectA();
                PhysicsCollisionObject b = event.getObjectB();
                PhysicsCollisionObject other = a == query ? b : a;
                if (PhysicsBodyExtensionKt.getOwner(other) == pig) snapshotProxy[0] = other;
            });
            helper.assertTrue(snapshotProxy[0] != null,
                    "snapshot contains the entity proxy before removal");

            pig.discard();
            helper.runAfterDelay(10, () -> {
                boolean[] staleProxyPresent = {false};
                snapshot.contactTest(query, event -> {
                    PhysicsCollisionObject a = event.getObjectA();
                    PhysicsCollisionObject b = event.getObjectB();
                    if (a == snapshotProxy[0] || b == snapshotProxy[0]) staleProxyPresent[0] = true;
                });
                helper.assertTrue(!staleProxyPresent[0],
                        "snapshot no longer returns the removed entity proxy");
                helper.succeed();
            });
        });
    }
}
