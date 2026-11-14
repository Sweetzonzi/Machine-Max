package io.github.sweetzonzi.machine_max.client.render.gui.seat;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 座位图的投影矩阵约定：沿参考零件自身的上轴俯视，把载具摆正、车头朝上。
 *
 * <p>车头取局部 {@code -Z}（项目约定，与 {@code DestroyableObject#getFrontVector()} 一致）。
 * 判据：任意偏航、俯仰、侧倾下，局部 {@code -Z} → {@code (0,-1)}、局部 {@code +X}
 * → {@code (+1,0)}、局部 {@code +Y} → {@code (0,0,+1)}（GUI 坐标 y 向下、z 朝观察者）。
 * 若沿世界竖直方向投影，俯仰/侧倾会把车顶斜切进平面位置，只有车身摆正时碰巧正确，所以这里
 * 逐个姿态断言，而不是只测偏航。</p>
 */
class SeatMapProjectionTest {

    private static final float EPS = 1e-4f;
    /** 偏航、俯仰、侧倾（度） */
    private static final float[][] ATTITUDES = {
            {0f, 0f, 0f}, {45f, 0f, 0f}, {90f, 0f, 0f}, {180f, 0f, 0f}, {-170f, 0f, 0f},
            {30f, 25f, 0f}, {30f, 0f, 40f}, {-60f, -18f, 22f}, {135f, 8f, -35f}
    };
    /** 局部车头轴 */
    private static final Vector3f FRONT = new Vector3f(0f, 0f, -1f);
    /** 局部车体右轴：{@code front × up} */
    private static final Vector3f RIGHT = new Vector3f(1f, 0f, 0f);
    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);

    /** 参考零件的世界旋转：先偏航、再俯仰、最后侧倾 */
    private static Quaternionf attitude(float yawDeg, float pitchDeg, float rollDeg) {
        return new Quaternionf().rotationYXZ(
                (float) Math.toRadians(yawDeg),
                (float) Math.toRadians(pitchDeg),
                (float) Math.toRadians(rollDeg));
    }

    private static Matrix4f map(float fit, Vector3f center, Vector3f origin, Quaternionf attitude) {
        var bounds = new SeatMapLayout.Bounds(center.x, center.y, center.z, 1f, 1f, 1f);
        float side = 2f * fit / 0.9f;
        return bounds.projection(new Matrix4f().translationRotate(origin, attitude).invert(),
                100f - side / 2f, 100f - side / 2f, side, side, 0f, 90f, 0f);
    }

    /** 局部方向 → 世界方向 */
    private static Vector3f world(Quaternionf attitude, Vector3f local) {
        return attitude.transform(new Vector3f(local), new Vector3f());
    }

    private static String caseName(float[] a) {
        return "偏航=" + a[0] + " 俯仰=" + a[1] + " 侧倾=" + a[2];
    }

    @Test
    void frontPointsUpOnScreenForEveryAttitude() {
        for (float[] a : ATTITUDES) {
            Quaternionf attitude = attitude(a[0], a[1], a[2]);
            Matrix4f matrix = map(1f, new Vector3f(), new Vector3f(), attitude);
            Vector3f mapped = matrix.transformDirection(world(attitude, FRONT));
            assertEquals(0f, mapped.x, EPS, caseName(a) + " 时车头的屏幕横轴必须为 0");
            assertEquals(-1f, mapped.y, EPS, caseName(a) + " 时车头必须指向屏幕上方");
        }
    }

    @Test
    void rightPointsRightOnScreenForEveryAttitude() {
        for (float[] a : ATTITUDES) {
            Quaternionf attitude = attitude(a[0], a[1], a[2]);
            Matrix4f matrix = map(1f, new Vector3f(), new Vector3f(), attitude);
            Vector3f mapped = matrix.transformDirection(world(attitude, RIGHT));
            assertEquals(1f, mapped.x, EPS, caseName(a) + " 时车体右侧必须在屏幕右侧");
            assertEquals(0f, mapped.y, EPS, caseName(a) + " 时车体右侧不能有纵向分量");
        }
    }

    @Test
    void localUpPointsAtTheViewerForEveryAttitude() {
        for (float[] a : ATTITUDES) {
            Quaternionf attitude = attitude(a[0], a[1], a[2]);
            Matrix4f matrix = map(1f, new Vector3f(), new Vector3f(), attitude);
            Vector3f mapped = matrix.transformDirection(world(attitude, UP));
            assertEquals(0f, mapped.x, EPS, caseName(a) + " 时车体上轴不能有横向分量");
            assertEquals(0f, mapped.y, EPS, caseName(a) + " 时车体上轴不能有纵向分量");
            // GUI 的 +z 朝向观察者：俯视时车顶必须比车底更近
            assertEquals(1f, mapped.z, EPS, caseName(a) + " 时车顶必须指向观察者");
        }
    }

    @Test
    void heightNeverLeaksIntoPlanePosition() {
        for (float[] a : ATTITUDES) {
            Quaternionf attitude = attitude(a[0], a[1], a[2]);
            float fit = 3f;
            Vector3f origin = new Vector3f(11f, 4f, -7f);
            Matrix4f matrix = map(fit, new Vector3f(), origin, attitude);

            Vector3f roof = matrix.transformPosition(new Vector3f(origin)
                    .add(world(attitude, new Vector3f(0f, 3f, 0f))));
            Vector3f floor = matrix.transformPosition(new Vector3f(origin)
                    .add(world(attitude, new Vector3f(0f, -3f, 0f))));
            assertTrue(roof.z > floor.z, caseName(a) + " 时车顶必须比车底更靠近观察者（俯视）");
            // 局部上轴就是投影方向：车顶与车底必须落在同一平面位置
            assertEquals(floor.x, roof.x, 1e-3f, caseName(a) + " 时高度不应产生横向偏移");
            assertEquals(floor.y, roof.y, 1e-3f, caseName(a) + " 时高度不应产生纵向偏移");
        }
    }

    @Test
    void originLandsOnPanelCenterAndDistanceScalesByFit() {
        for (float[] a : ATTITUDES) {
            Quaternionf attitude = attitude(a[0], a[1], a[2]);
            float fit = 4f;
            Vector3f origin = new Vector3f(37f, 12f, -58f);
            Matrix4f matrix = map(fit, new Vector3f(), origin, attitude);

            Vector3f center = matrix.transformPosition(new Vector3f(origin));
            assertEquals(100f, center.x, 1e-3f, caseName(a) + " 时参考点必须落在面板中心");
            assertEquals(100f, center.y, 1e-3f, caseName(a) + " 时参考点必须落在面板中心");

            // 车头前方 2 格：屏幕上移 2 * fit 像素
            Vector3f ahead = new Vector3f(world(attitude, FRONT)).mul(2f).add(origin);
            Vector3f mapped = matrix.transformPosition(ahead);
            assertEquals(100f, mapped.x, 1e-3f, caseName(a) + " 时正前方不应有横向偏移");
            assertEquals(100f - 2f * fit, mapped.y, 1e-3f, caseName(a) + " 时正前方必须按 fit 上移");
        }
    }

    @Test
    void centeringOffsetPutsBoundingBoxCenterOnPanelCenter() {
        for (float[] a : ATTITUDES) {
            Quaternionf attitude = attitude(a[0], a[1], a[2]);
            Vector3f origin = new Vector3f(-3f, 1f, 5f);
            float fit = 3f;
            Vector3f localCenter = new Vector3f(1.25f, 2.5f, -0.75f);
            Matrix4f matrix = map(fit, localCenter, origin, attitude);
            Vector3f bboxCenter = world(attitude, localCenter).add(origin);
            Vector3f mapped = matrix.transformPosition(bboxCenter);
            assertEquals(100f, mapped.x, 1e-3f, caseName(a) + " 时包围盒中心应被居中");
            assertEquals(100f, mapped.y, 1e-3f, caseName(a) + " 时包围盒中心应被居中");
        }
    }

    @Test
    void sideViewSeparatesSeatHeightFromLongitudinalPosition() {
        var bounds = SeatMapLayout.Bounds.between(new Vector3f(-3, 1, -10), new Vector3f(1, 7, 2));
        assertEquals(-1f, bounds.centerX(), EPS);
        assertEquals(4f, bounds.centerY(), EPS);
        assertEquals(-4f, bounds.centerZ(), EPS);
        Matrix4f side = bounds.projection(new Matrix4f(), 0, 0, 100, 40, 90, 0, 0);
        Vector3f low = side.transformPosition(-1, 3, -4, new Vector3f());
        Vector3f high = side.transformPosition(-1, 5, -4, new Vector3f());
        Vector3f front = side.transformPosition(-1, 3, -6, new Vector3f());
        assertEquals(low.x, high.x, EPS);
        assertTrue(high.y < low.y);
        assertEquals(low.y, front.y, EPS);
        assertTrue(front.x < low.x);
    }

    @Test
    void obliqueViewFitsAllCornersAndIsIndependentOfVehicleWorldPose() {
        Vector3f min = new Vector3f(-3, 1, -9);
        Vector3f max = new Vector3f(6, 8, 2);
        var bounds = SeatMapLayout.Bounds.between(min, max);
        Matrix4f localProjection = bounds.projection(new Matrix4f(), 12, 31, 48, 70, 32, 27, 16);
        Matrix4f reference = new Matrix4f().translationRotate(new Vector3f(15, 8, 31), attitude(50, -19, 71));
        Matrix4f worldProjection = bounds.projection(new Matrix4f(reference).invert(),
                12, 31, 48, 70, 32, 27, 16);
        for (int i = 0; i < 8; i++) {
            var corner = new Vector3f((i & 1) == 0 ? min.x : max.x,
                    (i & 2) == 0 ? min.y : max.y, (i & 4) == 0 ? min.z : max.z);
            var local = localProjection.transformPosition(new Vector3f(corner));
            var world = worldProjection.transformPosition(reference.transformPosition(corner));
            assertTrue(local.x >= 12 && local.x <= 60);
            assertTrue(local.y >= 31 && local.y <= 101);
            assertEquals(local.x, world.x, EPS);
            assertEquals(local.y, world.y, EPS);
            assertEquals(local.z, world.z, EPS);
        }
    }

    @Test
    void cameraViewProjectsTheVehicleOntoTheActualCameraBasis() {
        var bounds = new SeatMapLayout.Bounds(1, 2, -3, 3, 2, 7);
        for (float[] body : ATTITUDES) for (float[] camera : ATTITUDES) {
            var referenceRotation = attitude(body[0], body[1], body[2]);
            var reference = new Matrix4f().translationRotate(new Vector3f(17, -8, 33), referenceRotation);
            // NeoForge Camera#setRotation：yaw/pitch/roll 全部在 Camera.rotation 内。
            var cameraRotation = new Quaternionf().rotationYXZ((float) Math.PI - (float) Math.toRadians(camera[0]),
                    (float) -Math.toRadians(camera[1]), (float) -Math.toRadians(camera[2]));
            var unchanged = new Quaternionf(cameraRotation);
            Matrix4f localToView = SeatMapLayout.cameraRelativeRotation(reference, cameraRotation);
            Matrix4f projection = bounds.projection(new Matrix4f(reference).invert(), 12, 31, 140, 160, localToView);
            Vector3f cameraRight = cameraRotation.transform(new Vector3f(1, 0, 0));
            Vector3f cameraUp = cameraRotation.transform(new Vector3f(0, 1, 0));
            Vector3f cameraBack = cameraRotation.transform(new Vector3f(0, 0, 1));
            float fit = projection.transformDirection(new Vector3f(cameraRight)).x;
            assertTrue(fit > 0);
            for (Vector3f local : new Vector3f[]{FRONT, RIGHT, UP}) {
                Vector3f world = referenceRotation.transform(new Vector3f(local));
                Vector3f mapped = projection.transformDirection(new Vector3f(world));
                assertEquals(world.dot(cameraRight) * fit, mapped.x, 1e-3f);
                assertEquals(-world.dot(cameraUp) * fit, mapped.y, 1e-3f);
                assertEquals(world.dot(cameraBack) * fit, mapped.z, 1e-3f);
            }
            Vector3f center = projection.transformPosition(reference.transformPosition(new Vector3f(1, 2, -3)));
            assertEquals(82, center.x, 1e-3f);
            assertEquals(111, center.y, 1e-3f);
            assertTrue(cameraRotation.equals(unchanged), "HUD must not mutate the main camera quaternion");
        }
    }

    @Test
    void cameraPitchRollAndVerticalViewsFitEveryBoundingCorner() {
        Vector3f min = new Vector3f(-3, 1, -9), max = new Vector3f(6, 8, 2);
        var bounds = SeatMapLayout.Bounds.between(min, max);
        var reference = new Matrix4f().translationRotate(new Vector3f(-30, 9, 71), attitude(50, -19, 71));
        for (float pitch : new float[]{-90, -89.999f, 0, 89.999f, 90}) {
            var camera = attitude(131, pitch, 37);
            Matrix4f projection = bounds.projection(new Matrix4f(reference).invert(), 12, 31, 48, 70,
                    SeatMapLayout.cameraRelativeRotation(reference, camera));
            assertTrue(projection.isFinite());
            for (int i = 0; i < 8; i++) {
                var corner = new Vector3f((i & 1) == 0 ? min.x : max.x,
                        (i & 2) == 0 ? min.y : max.y, (i & 4) == 0 ? min.z : max.z);
                var mapped = projection.transformPosition(reference.transformPosition(corner));
                assertTrue(mapped.x >= 12 && mapped.x <= 60);
                assertTrue(mapped.y >= 31 && mapped.y <= 101);
            }
        }
    }

    @Test
    void headingTracksRelativeAzimuthAndPreservesSideOnOrientation() {
        var bounds = new SeatMapLayout.Bounds(0, 0, 0, 2, 1, 6);
        for (float vehicleYaw : new float[]{0, 30, -170, 180}) {
            var reference = new Matrix4f().rotateY((float) Math.toRadians(vehicleYaw));
            for (float cameraYaw : new float[]{-1, 0, 90, 180, 359}) {
                float actualYaw = SeatMapLayout.relativeHeadingYaw(reference, cameraYaw, 17);
                float expectedYaw = vehicleYaw + cameraYaw - 180;
                Matrix4f actual = bounds.projection(new Matrix4f(), 0, 0, 100, 100, actualYaw, 70, 0);
                Matrix4f expected = bounds.projection(new Matrix4f(), 0, 0, 100, 100, expectedYaw, 70, 0);
                Vector3f a = actual.transformDirection(new Vector3f(FRONT));
                Vector3f b = expected.transformDirection(new Vector3f(FRONT));
                assertEquals(b.x, a.x, 1e-3f);
                assertEquals(b.y, a.y, 1e-3f);
            }
        }
        float sideYaw = SeatMapLayout.relativeHeadingYaw(new Matrix4f(), 90, 0);
        Vector3f side = bounds.projection(new Matrix4f(), 0, 0, 100, 100, sideYaw, 70, 0)
                .transformDirection(new Vector3f(FRONT));
        assertTrue(Math.abs(side.x) > 0);
        assertEquals(0, side.y, EPS, "side-on vehicle should lie horizontally in the heading HUD");
    }

    @Test
    void undefinedHeadingHoldsItsPreviousValueAndCameraRollIsAppliedOnce() {
        assertEquals(42, SeatMapLayout.relativeHeadingYaw(new Matrix4f().rotateZ((float) Math.PI / 2), 180, 42));
        Matrix4f rotation = SeatMapLayout.cameraRelativeRotation(new Matrix4f(),
                new Quaternionf().rotationZ((float) -Math.PI / 2));
        Vector3f right = rotation.transformDirection(new Vector3f(RIGHT));
        assertEquals(0, right.x, EPS);
        assertEquals(1, right.y, EPS);
    }
}
