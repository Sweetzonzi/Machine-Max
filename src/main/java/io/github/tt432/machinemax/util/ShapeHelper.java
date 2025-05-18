package io.github.tt432.machinemax.util;

import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.*;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Plane;
import com.jme3.math.Vector3f;
import io.github.tt432.machinemax.MachineMax;

public class ShapeHelper {

    public static float getShapeMinY(PhysicsCollisionObject pco, float resolution) {
        float centerY = pco.getPhysicsLocation(null).y;
        if (pco.getCollisionShape() instanceof SphereCollisionShape sphere) {
            return centerY - sphere.getRadius();
        } else {
            BoundingBox boundingBox;
            float height;
            try {
                boundingBox = pco.boundingBox(null);
                height = boundingBox.getMin(null).y;
            } catch (Exception e) {
                MachineMax.LOGGER.error("{}碰撞箱计算结果异常: ", pco.name, e);
                return 9999;
            }
            PlaneCollisionShape testPlane;
            PhysicsRigidBody testPco = new PhysicsRigidBody("test_plane", null, new EmptyShape(false), 0);
            while (height < centerY) {
                testPlane = new PlaneCollisionShape(new Plane(new Vector3f(0, 1, 0), height));
                testPco.setCollisionShape(testPlane);
                var space = pco.getCollisionSpace();
                if (space == null) return 0;
                int count = space.pairTest(pco, testPco, null);
                if (count > 0) break;
                else height += resolution;
            }
            return height;
        }
    }
}
