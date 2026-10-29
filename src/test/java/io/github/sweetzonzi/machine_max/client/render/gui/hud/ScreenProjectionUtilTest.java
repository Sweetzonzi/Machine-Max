package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import io.github.sweetzonzi.machine_max.util.ScreenProjectionUtil;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScreenProjectionUtilTest {
    private static final class MovingCamera extends Camera {
        void moveTo(double x, double y, double z) {
            setPosition(x, y, z);
        }
    }

    @Test
    void barrelDirectionHasNoParallaxWhenTheCameraMoves() {
        var camera = new MovingCamera();
        camera.rotation().identity();
        var direction = new Vec3(.1, .2, -1);
        float[] initial = project(direction, camera, 90);
        assertArrayEquals(new float[]{30, -60}, initial, 1e-4f);
        camera.moveTo(15_000_000, 42, -15_000_000);
        assertArrayEquals(initial, project(direction, camera, 90), 1e-4f);
        assertArrayEquals(initial, project(direction.scale(1000), camera, 90), 1e-4f);
        assertArrayEquals(initial, ScreenProjectionUtil.worldToScreenOffset(
                camera.getPosition().add(direction.scale(1000)), camera, 90, 800, 600), 1e-4f);
    }

    @Test
    void directionProjectionFollowsCameraRotationIncludingRollAndZoom() {
        var camera = new Camera();
        var rotation = new Quaternionf().rotateYXZ(.7f, -.3f, .5f);
        camera.rotation().set(rotation);
        var forward = rotation.transform(new Vector3f(0, 0, -1));
        assertArrayEquals(new float[]{0, 0}, project(vec(forward), camera, 70), 1e-4f);
        var offAxis = vec(rotation.transform(new Vector3f(.1f, .2f, -1)));
        assertArrayEquals(new float[]{30, -60}, project(offAxis, camera, 90), 1e-4f);
        float focal = 300f / (float) Math.tan(Math.toRadians(30));
        assertArrayEquals(new float[]{.1f * focal, -.2f * focal}, project(offAxis, camera, 60), 1e-4f);
    }

    @Test
    void invisibleOrInvalidDirectionsCannotReachTheHud() {
        var camera = new Camera();
        camera.rotation().identity();
        for (Vec3 direction : new Vec3[]{Vec3.ZERO, new Vec3(0, 0, 1), new Vec3(1, 0, 0),
                new Vec3(Double.NaN, 0, -1), new Vec3(Double.POSITIVE_INFINITY, 0, -1)}) {
            assertNull(project(direction, camera, 70));
        }
        for (float fov : new float[]{0, -1, 180, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertNull(project(new Vec3(0, 0, -1), camera, fov));
        }
        assertNull(ScreenProjectionUtil.worldDirectionToScreenOffset(new Vec3(0, 0, -1), camera, 70, 800, 0));
    }

    private static float[] project(Vec3 direction, Camera camera, float fov) {
        return ScreenProjectionUtil.worldDirectionToScreenOffset(direction, camera, fov, 800, 600);
    }

    private static Vec3 vec(Vector3f vector) {
        return new Vec3(vector.x, vector.y, vector.z);
    }
}
