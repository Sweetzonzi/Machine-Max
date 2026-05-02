package io.github.sweetzonzi.machine_max.util;

import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.PlaneCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Plane;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;

public class ShapeHelper {

    private static final PlaneCollisionShape TEST_PLANE = new PlaneCollisionShape(new Plane(new Vector3f(0, 1, 0), 0));
    private static final PhysicsRigidBody TEST_PCO = new PhysicsRigidBody(TEST_PLANE, 0);
    public static float getShapeMinY(PhysicsCollisionObject pco, float resolution) {
        float centerY = pco.getPhysicsLocation(null).y;
        if (pco.getCollisionShape() instanceof SphereCollisionShape sphere) {
            return centerY - sphere.getRadius();
        } else {
            BoundingBox boundingBox;
            float height;
            try {
                boundingBox = PhysicsBodyExtensionKt.stateOf(pco).getCachedBoundingBox();
                height = boundingBox.getMin(null).y;
//                return height;
            } catch (Exception e) {
                MachineMax.LOGGER.error("{}碰撞箱计算结果异常: ", pco.name, e);
                return -9999;
            }
            while (height < centerY) {
                TEST_PCO.setPhysicsLocation(new Vector3f(0, height, 0));
                var space = pco.getCollisionSpace();
                if (space == null) return -9999;
                int count = space.pairTest(pco, TEST_PCO, null);
                if (count > 0) break;
                else height += resolution;
            }
            return height;
        }
    }
}
