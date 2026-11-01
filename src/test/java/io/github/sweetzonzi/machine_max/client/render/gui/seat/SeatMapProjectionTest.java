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

    private static Matrix4f map(float fit, float centerSx, float centerSy, Vector3f origin,
                                Quaternionf attitude) {
        return SeatMapLayout.mapMatrix(fit, centerSx, centerSy, 100f, 100f, origin,
                new Quaternionf(attitude).conjugate());
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
            Matrix4f matrix = map(1f, 0f, 0f, new Vector3f(), attitude);
            Vector3f mapped = matrix.transformDirection(world(attitude, FRONT));
            assertEquals(0f, mapped.x, EPS, caseName(a) + " 时车头的屏幕横轴必须为 0");
            assertEquals(-1f, mapped.y, EPS, caseName(a) + " 时车头必须指向屏幕上方");
        }
    }

    @Test
    void rightPointsRightOnScreenForEveryAttitude() {
        for (float[] a : ATTITUDES) {
            Quaternionf attitude = attitude(a[0], a[1], a[2]);
            Matrix4f matrix = map(1f, 0f, 0f, new Vector3f(), attitude);
            Vector3f mapped = matrix.transformDirection(world(attitude, RIGHT));
            assertEquals(1f, mapped.x, EPS, caseName(a) + " 时车体右侧必须在屏幕右侧");
            assertEquals(0f, mapped.y, EPS, caseName(a) + " 时车体右侧不能有纵向分量");
        }
    }

    @Test
    void localUpPointsAtTheViewerForEveryAttitude() {
        for (float[] a : ATTITUDES) {
            Quaternionf attitude = attitude(a[0], a[1], a[2]);
            Matrix4f matrix = map(1f, 0f, 0f, new Vector3f(), attitude);
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
            Matrix4f matrix = map(fit, 0f, 0f, origin, attitude);

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
            Matrix4f matrix = map(fit, 0f, 0f, origin, attitude);

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
            float centerSx = 1.25f;
            float centerSy = -0.75f;
            Matrix4f matrix = map(fit, centerSx, centerSy, origin, attitude);

            // 局部坐标 (centerSx, centerSy) 对应的世界点：沿右轴走 centerSx，沿 -前向走 centerSy
            Vector3f bboxCenter = new Vector3f(world(attitude, RIGHT)).mul(centerSx)
                    .add(new Vector3f(world(attitude, FRONT)).mul(-centerSy))
                    .add(origin);
            Vector3f mapped = matrix.transformPosition(bboxCenter);
            assertEquals(100f, mapped.x, 1e-3f, caseName(a) + " 时包围盒中心应被居中");
            assertEquals(100f, mapped.y, 1e-3f, caseName(a) + " 时包围盒中心应被居中");
        }
    }
}
