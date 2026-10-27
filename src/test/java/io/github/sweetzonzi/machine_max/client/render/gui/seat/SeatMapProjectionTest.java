package io.github.sweetzonzi.machine_max.client.render.gui.seat;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 座位图的投影矩阵约定：把载具摆正、车头朝上。
 *
 * <p>判据来自设计文档：任意朝向下都应有 {@code front → (0,-1)}、{@code right → (+1,0)}
 * （GUI 坐标 y 向下）。写成“先偏航再俯视”时，偏航作用的是一张已经躺倒的图，
 * 屏幕纵轴恒等于世界 z，偏航只把世界高度混进横轴——只有正南正北碰巧正确，所以这里
 * 逐个朝向断言，而不是只测一个角度。</p>
 */
class SeatMapProjectionTest {

    private static final float EPS = 1e-4f;
    private static final float[] HEADINGS = {0f, 45f, 90f, 135f, 180f, -33f, -170f};

    /** 车头方向（世界 XZ 平面） */
    private static Vector3f front(float headingDeg) {
        float h = (float) Math.toRadians(headingDeg);
        return new Vector3f((float) Math.sin(h), 0f, (float) Math.cos(h));
    }

    /** 车体右侧：{@code front × up} */
    private static Vector3f right(Vector3f front) {
        return new Vector3f(front).cross(0f, 1f, 0f).normalize();
    }

    @Test
    void frontPointsUpOnScreenForEveryHeading() {
        for (float heading : HEADINGS) {
            Matrix4f matrix = SeatMapLayout.mapMatrix(1f, 0f, 0f, 100f, 100f, new Vector3f(), heading);
            Vector3f mappedFront = matrix.transformDirection(front(heading));
            assertEquals(0f, mappedFront.x, EPS, "heading=" + heading + " 时车头的屏幕横轴必须为 0");
            assertEquals(-1f, mappedFront.y, EPS, "heading=" + heading + " 时车头必须指向屏幕上方");
        }
    }

    @Test
    void rightPointsRightOnScreenForEveryHeading() {
        for (float heading : HEADINGS) {
            Matrix4f matrix = SeatMapLayout.mapMatrix(1f, 0f, 0f, 100f, 100f, new Vector3f(), heading);
            Vector3f mappedRight = matrix.transformDirection(right(front(heading)));
            assertEquals(1f, mappedRight.x, EPS, "heading=" + heading + " 时车体右侧必须在屏幕右侧");
            assertEquals(0f, mappedRight.y, EPS, "heading=" + heading + " 时车体右侧不能有纵向分量");
        }
    }

    @Test
    void originLandsOnPanelCenterAndDistanceScalesByFit() {
        for (float heading : HEADINGS) {
            float fit = 4f;
            Vector3f origin = new Vector3f(37f, 12f, -58f);
            Matrix4f matrix = SeatMapLayout.mapMatrix(fit, 0f, 0f, 100f, 100f, origin, heading);

            Vector3f center = matrix.transformPosition(new Vector3f(origin));
            assertEquals(100f, center.x, 1e-3f, "参考点必须落在面板中心");
            assertEquals(100f, center.y, 1e-3f, "参考点必须落在面板中心");

            // 车头前方 2 格：屏幕上移 2 * fit 像素
            Vector3f ahead = new Vector3f(front(heading)).mul(2f).add(origin);
            Vector3f mapped = matrix.transformPosition(ahead);
            assertEquals(100f, mapped.x, 1e-3f, "正前方不应有横向偏移");
            assertEquals(100f - 2f * fit, mapped.y, 1e-3f, "正前方必须按 fit 上移");
        }
    }

    @Test
    void roofIsCloserThanFloorSoTheMapLooksDown() {
        for (float heading : HEADINGS) {
            float fit = 3f;
            Vector3f origin = new Vector3f(11f, 4f, -7f);
            Matrix4f matrix = SeatMapLayout.mapMatrix(fit, 0f, 0f, 100f, 100f, origin, heading);

            Vector3f roof = matrix.transformPosition(new Vector3f(origin).add(0f, 3f, 0f));
            Vector3f floor = matrix.transformPosition(new Vector3f(origin).add(0f, -3f, 0f));
            // GUI 的 +z 朝向观察者：俯视时车顶必须比车底更近，否则画出来是仰视图
            org.junit.jupiter.api.Assertions.assertTrue(roof.z > floor.z,
                    "heading=" + heading + " 时车顶必须比车底更靠近观察者（俯视）");
            // 高度不能影响平面位置：俯视是正交投影，车顶与车底应落在同一点
            assertEquals(floor.x, roof.x, 1e-3f, "高度不应产生横向偏移");
            assertEquals(floor.y, roof.y, 1e-3f, "高度不应产生纵向偏移");
        }
    }

    @Test
    void centeringOffsetPutsBoundingBoxCenterOnPanelCenter() {
        for (float heading : HEADINGS) {
            Vector3f origin = new Vector3f(-3f, 1f, 5f);
            float fit = 3f;
            float centerSx = 1.25f;
            float centerSy = -0.75f;
            Matrix4f matrix = SeatMapLayout.mapMatrix(fit, centerSx, centerSy, 100f, 100f, origin, heading);

            Vector3f f = front(heading);
            Vector3f r = right(f);
            // 参考系坐标 (sx, sy) 对应的世界点：sx 沿右轴，sy 沿 -front
            Vector3f bboxCenter = new Vector3f(r).mul(centerSx)
                    .add(new Vector3f(f).mul(-centerSy))
                    .add(origin);
            Vector3f mapped = matrix.transformPosition(bboxCenter);
            assertEquals(100f, mapped.x, 1e-3f, "包围盒中心应被居中");
            assertEquals(100f, mapped.y, 1e-3f, "包围盒中心应被居中");
        }
    }
}
