package io.github.sweetzonzi.machine_max.util.geometry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BeltPath2D} 的纯 JUnit 验证。
 *
 * <p>不启动 Minecraft，也不加载 Bullet；只检查 2D 几何。覆盖凸布局、平地、2 轮 stadium/包含、
 * 抬起/降低负重轮、锚定顺序（三角形 vs 抬起中间轮）、反向输入、弧长采样、无自交/不切入轮子
 * 与非法输入。</p>
 */
class BeltPath2DTest {
    private static final double EPS = 1e-9;
    private static final double PI = Math.PI;

    private static List<Circle2D> track(double road2Y) {
        return List.of(
                new Circle2D(-2.0, 1.0, 0.40),   // 0 sprocket
                new Circle2D(-1.5, -0.6, 0.35),  // 1 road1
                new Circle2D(0.0, road2Y, 0.35), // 2 road2
                new Circle2D(1.5, -0.6, 0.35),   // 3 road3
                new Circle2D(2.0, 1.0, 0.40)     // 4 idler
        );
    }

    private static List<Circle2D> square() {
        return List.of(
                new Circle2D(0, 0, 0.2),
                new Circle2D(2, 0, 0.2),
                new Circle2D(2, 2, 0.2),
                new Circle2D(0, 2, 0.2)
        );
    }

    private static BeltPath2D.PointContact pointContact(BeltPath2D path, int wheelIndex) {
        return path.pointContacts().stream()
                .filter(contact -> contact.wheelIndex() == wheelIndex)
                .findFirst()
                .orElse(null);
    }

    private static BeltPath2D.ArcContact arcContact(BeltPath2D path, int wheelIndex) {
        return path.arcs().stream()
                .filter(arc -> arc.wheelIndex() == wheelIndex)
                .findFirst()
                .orElse(null);
    }

    private static double distancePointSegment(Vec2 point, Vec2 a, Vec2 b) {
        Vec2 ab = b.sub(a);
        double lengthSq = ab.lengthSquared();
        if (lengthSq <= EPS * EPS) {
            return point.distance(a);
        }
        double t = point.sub(a).dot(ab) / lengthSq;
        t = Math.max(0, Math.min(1, t));
        return point.distance(a.add(ab.mul(t)));
    }

    private static double boundaryArcAt(BeltPath2D path, Vec2 point) {
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestArc = 0;
        for (BeltPath2D.Edge edge : path.edges()) {
            double distance = distancePointSegment(point, edge.start(), edge.end());
            if (distance < bestDistance) {
                bestDistance = distance;
                Vec2 ab = edge.end().sub(edge.start());
                double t = Math.max(0, Math.min(1,
                        point.sub(edge.start()).dot(ab) / ab.lengthSquared()));
                bestArc = edge.startDistance() + t * edge.length();
            }
        }
        return bestArc;
    }

    private static double forwardArcDiff(double a, double b, double length) {
        double diff = (b - a) % length;
        if (diff < 0) {
            diff += length;
        }
        return diff;
    }

    private static double normalizeAngle(double angle) {
        while (angle > PI) {
            angle -= 2 * PI;
        }
        while (angle < -PI) {
            angle += 2 * PI;
        }
        return angle;
    }

    private static double minWheelClearance(BeltPath2D path, List<Circle2D> circles) {
        double worst = Double.POSITIVE_INFINITY;
        for (Circle2D circle : circles) {
            for (BeltPath2D.Edge edge : path.edges()) {
                worst = Math.min(worst,
                        distancePointSegment(circle.center(), edge.start(), edge.end()) - circle.radius());
            }
        }
        return worst;
    }

    private static boolean selfIntersects(List<Vec2> points) {
        int n = points.size();
        for (int i = 0; i < n; i++) {
            Vec2 a = points.get(i);
            Vec2 b = points.get((i + 1) % n);
            if (a.distance(b) <= EPS) {
                continue;
            }
            for (int j = i + 1; j < n; j++) {
                if (j == i || j == (i + 1) % n || i == (j + 1) % n) {
                    continue;
                }
                Vec2 c = points.get(j);
                Vec2 d = points.get((j + 1) % n);
                if (c.distance(d) <= EPS) {
                    continue;
                }
                if (segmentsIntersect(a, b, c, d)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean segmentsIntersect(Vec2 a, Vec2 b, Vec2 c, Vec2 d) {
        Vec2 ab = b.sub(a);
        Vec2 cd = d.sub(c);
        double denominator = ab.cross(cd);
        if (Math.abs(denominator) <= 1e-12) {
            return false;
        }
        Vec2 ac = c.sub(a);
        double t = ac.cross(cd) / denominator;
        double u = ac.cross(ab) / denominator;
        return t > 1e-9 && t < 1 - 1e-9 && u > 1e-9 && u < 1 - 1e-9;
    }

    private static boolean polygonContains(List<Vec2> points, Vec2 point) {
        boolean inside = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            Vec2 a = points.get(i);
            Vec2 b = points.get(j);
            if (((a.y() > point.y()) != (b.y() > point.y()))
                    && (point.x() < (b.x() - a.x()) * (point.y() - a.y())
                    / (b.y() - a.y()) + a.x())) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static double minDistanceToWheel(BeltPath2D path, Circle2D circle) {
        double best = Double.POSITIVE_INFINITY;
        for (Vec2 point : path.points()) {
            best = Math.min(best, point.distance(circle.center()));
        }
        return best;
    }

    @Test
    void squareHasFourQuarterArcs() {
        BeltPath2D path = BeltPath2D.build(square());

        assertTrue(path.isConvex());
        assertTrue(path.pointContacts().isEmpty());
        assertEquals(4, path.arcs().size());
        for (BeltPath2D.ArcContact arc : path.arcs()) {
            assertEquals(PI / 2, Math.abs(arc.sweep()), 1e-5);
            assertEquals(0.2 * PI / 2, arc.contactLength(), 1e-5);
        }
        assertEquals(8 + 2 * PI * 0.2, path.length(), 0.005);
        assertTrue(path.signedArea() > 0);
    }

    @Test
    void flatTrackUsesPointContactForStraightMiddleWheelAndMatchesConvexHull() {
        List<Circle2D> circles = track(-0.6);
        BeltPath2D path = BeltPath2D.build(circles);

        assertTrue(path.isConvex());
        assertEquals(1, path.pointContacts().size());
        BeltPath2D.PointContact contact = pointContact(path, 2);
        assertNotNull(contact);
        assertEquals(0.0, contact.point().x(), 1e-7);
        assertEquals(-0.95, contact.point().y(), 1e-7);
        assertEquals(null, arcContact(path, 2));

        double hull = ConvexHull2D.convexPerimeter(circles, 64);
        assertEquals(12.7396, hull, 0.01);
        assertEquals(hull, path.length(), 0.01);
        assertEquals(64, path.linkCount(0.2));
    }

    @Test
    void raisedTwoCentimetersIsNonConvexWithSinglePointContact() {
        List<Circle2D> circles = track(-0.58);
        BeltPath2D path = BeltPath2D.build(circles);
        double hull = ConvexHull2D.convexPerimeter(circles, 64);

        assertFalse(path.isConvex());
        BeltPath2D.PointContact contact = pointContact(path, 2);
        assertNotNull(contact);
        assertEquals(0.0, contact.point().x(), 1e-7);
        assertEquals(-0.93, contact.point().y(), 1e-7);
        assertEquals(null, arcContact(path, 2));
        assertTrue(path.length() > hull);
        assertTrue(path.length() - hull < 0.01);
        assertTrue(minDistanceToWheel(path, circles.get(2)) <= 0.35 + 1e-6);
        assertTrue(minDistanceToWheel(path, circles.get(2)) >= 0.35 - 1e-6);
        assertTrue(minWheelClearance(path, circles) >= -1e-4);
        assertFalse(selfIntersects(path.points()));
    }

    @Test
    void raisedThirtyCentimetersKeepsTheWheelInsteadOfBridgingIt() {
        List<Circle2D> circles = track(-0.30);
        BeltPath2D path = BeltPath2D.build(circles);
        double hullLength = ConvexHull2D.convexPerimeter(circles, 64);
        List<Vec2> hullPoints = ConvexHull2D.hullOfCircles(circles, 64);

        assertFalse(path.isConvex());
        BeltPath2D.PointContact contact = pointContact(path, 2);
        assertNotNull(contact);
        assertEquals(0.0, contact.point().x(), 1e-7);
        assertEquals(-0.65, contact.point().y(), 1e-7);
        assertTrue(path.length() > hullLength + 0.05);
        assertTrue(path.length() - hullLength < 0.1);
        assertTrue(minDistanceToWheel(path, circles.get(2)) <= 0.35 + 1e-6);
        assertTrue(minDistanceToWheel(path, circles.get(2)) >= 0.35 - 1e-6);
        assertTrue(minWheelClearance(path, circles) >= -1e-4);
        assertFalse(selfIntersects(path.points()));

        // 凸包从下面跨过去；非凸包络在凹口处是空的。
        assertTrue(polygonContains(hullPoints, new Vec2(0, -0.8)));
        assertFalse(path.containsPoint(new Vec2(0, -0.8)));
    }

    @Test
    void liftedMiddleWheelBetweenGroundWheelsGetsConcavePointContact() {
        List<Circle2D> circles = List.of(
                new Circle2D(-1.0, 0.0, 0.4),  // 0 主动轮，接地
                new Circle2D(0.0, 0.2, 0.4),   // 1 顶起，夹在两接地轮之间
                new Circle2D(1.0, 0.0, 0.4)    // 2 接地
        );
        BeltPath2D path = BeltPath2D.build(circles);

        assertFalse(path.isConvex());
        assertFalse(path.reversedFromInput());
        BeltPath2D.PointContact contact = pointContact(path, 1);
        assertNotNull(contact);
        assertEquals(0.0, contact.point().x(), 1e-7);
        assertEquals(-0.2, contact.point().y(), 1e-7);
        // 同一轮高过回程边，因此还会在上行程以圆弧出现一次。
        assertNotNull(arcContact(path, 1));
        assertNotNull(arcContact(path, 0));
        assertNotNull(arcContact(path, 2));
        assertTrue(minWheelClearance(path, circles) >= -1e-4);
        assertFalse(selfIntersects(path.points()));
    }

    @Test
    void liftedWheelGetsASecondContactOnTheReturnRun() {
        // 三个等半径轮：中间轮抬起 0.2 m 后顶部高过 B->A 闭合边。
        // 下行程仍在轮底点接触；回程在轮顶再加一段圆弧，同一轮出现两次。
        List<Circle2D> circles = List.of(
                new Circle2D(-1.0, 0.0, 0.4),
                new Circle2D(0.0, 0.2, 0.4),
                new Circle2D(1.0, 0.0, 0.4)
        );
        BeltPath2D path = BeltPath2D.build(circles);

        assertEquals(4, path.vertices().size());
        assertEquals(1, path.pointContacts().size());
        assertEquals(3, path.arcs().size());
        assertEquals(1, pointContact(path, 1).wheelIndex());
        assertEquals(1, arcContact(path, 1).wheelIndex());
        assertTrue(path.sampleAt(path.length() * 0.37).point().isFinite());
        assertTrue(minWheelClearance(path, circles) >= -1e-4);
        assertFalse(selfIntersects(path.points()));
    }

    @Test
    void degenerateEndCapStaysConvexAcrossPoseJitter() {
        // 前三个轮心严格共线、第四个把平面撑开：端帽处轮心折线回折，转角是 ±π。
        // 这是“几个不可动轮在悬挂压到极限时落到一条直线上”的形态。转角符号只由
        // 共线的哪一侧决定，微小位姿噪声会翻掉它；带路必须始终绕过该轮并包住所有轮心。
        for (double idlerY : new double[]{-0.15, -0.05, 0.05, 0.15}) {
            List<Circle2D> circles = List.of(
                    new Circle2D(0.0, 0.0, 0.4),    // 0 主动轮
                    new Circle2D(-1.45, 0.0, 0.4),  // 1 左端帽，内部下标
                    new Circle2D(4.55, 0.0, 0.4),   // 2 回程轮
                    new Circle2D(6.55, idlerY, 0.4) // 3 最后一个成员
            );
            BeltPath2D path = BeltPath2D.build(circles);

            BeltPath2D.Vertex endCap = path.vertices().get(1);
            assertEquals(1, endCap.wheelIndex());
            assertEquals(BeltPath2D.VertexType.CONVEX, endCap.type(),
                    "end cap flipped at idlerY=" + idlerY);
            assertTrue(Math.abs(endCap.turn()) > Math.PI * 0.75,
                    "end cap turn should be near pi at idlerY=" + idlerY);
            assertNotNull(arcContact(path, 1), "end cap lost its arc at idlerY=" + idlerY);
            for (Circle2D circle : circles) {
                assertTrue(path.containsPoint(circle.center()),
                        "wheel center left outside the loop at idlerY=" + idlerY);
            }
            assertFalse(selfIntersects(path.points()));
        }
    }

    @Test
    void triangularThreeWheelsKeepTheGroundRunDirect() {
        List<Circle2D> circles = List.of(
                new Circle2D(0.0, 1.0, 0.4),   // 0 主动轮，三角顶点
                new Circle2D(-1.0, 0.0, 0.4),  // 1 接地
                new Circle2D(1.0, 0.0, 0.4)    // 2 接地
        );
        BeltPath2D path = BeltPath2D.build(circles);

        assertTrue(path.isConvex());
        assertTrue(path.pointContacts().isEmpty());
        assertEquals(3, path.arcs().size());
        assertNull(pointContact(path, 0));
        assertNull(pointContact(path, 1));
        assertNull(pointContact(path, 2));
        assertTrue(minWheelClearance(path, circles) >= -1e-4);
        assertFalse(selfIntersects(path.points()));
    }

    @Test
    void sameCentersWithDifferentAnchoredOrderGiveDifferentPaths() {
        Circle2D left = new Circle2D(-1.0, 0.0, 0.4);
        Circle2D middle = new Circle2D(0.0, 0.2, 0.4);
        Circle2D right = new Circle2D(1.0, 0.0, 0.4);

        BeltPath2D lifted = BeltPath2D.build(List.of(left, middle, right));
        BeltPath2D triangle = BeltPath2D.build(List.of(left, right, middle));

        assertFalse(lifted.isConvex());
        assertTrue(triangle.isConvex());
        assertNotNull(pointContact(lifted, 1));
        assertNotNull(arcContact(lifted, 1));
        assertNull(pointContact(triangle, 1));
        assertNotNull(arcContact(triangle, 1));
        assertNotEquals(lifted.length(), triangle.length(), 1e-6);
        assertNotEquals(lifted.signedArea(), triangle.signedArea(), 1e-6);
    }

    @Test
    void loweredThirtyCentimetersUsesArcContact() {
        List<Circle2D> circles = track(-0.90);
        BeltPath2D path = BeltPath2D.build(circles);

        assertTrue(path.isConvex());
        assertTrue(path.pointContacts().isEmpty());
        BeltPath2D.ArcContact arc = arcContact(path, 2);
        assertNotNull(arc);
        assertEquals(22.62, Math.toDegrees(arc.sweep()), 0.02);
        assertEquals(0.1382, arc.contactLength(), 0.001);
        assertEquals(12.8000, path.length(), 0.005);
    }

    @Test
    void reversedInputKeepsLengthAndFollowsInputDirection() {
        List<Circle2D> clockwise = new ArrayList<>(track(-0.30));
        Collections.reverse(clockwise);

        BeltPath2D ccw = BeltPath2D.build(track(-0.30));
        BeltPath2D path = BeltPath2D.build(clockwise);

        // 3 个及以上不反转输入：输出方向跟随锚定顺序，反向输入得到顺时针带路。
        assertFalse(ccw.reversedFromInput());
        assertFalse(path.reversedFromInput());
        assertEquals(ccw.length(), path.length(), 1e-6);
        assertTrue(ccw.signedArea() > 0);
        assertTrue(path.signedArea() < 0);
        assertEquals(ccw.linkCount(0.2), path.linkCount(0.2));
    }

    @Test
    void arcSweepEndsAtItsGeometricEndpoint() {
        BeltPath2D path = BeltPath2D.build(track(-0.30));
        for (BeltPath2D.ArcContact arc : path.arcs()) {
            Circle2D circle = path.inputCircles().get(arc.wheelIndex());
            Vec2 end = arc.contactPoints().get(arc.contactPoints().size() - 1);
            double endAngle = Math.atan2(end.y() - circle.y(), end.x() - circle.x());
            double delta = normalizeAngle(endAngle - (arc.startAngle() + arc.sweep()));
            assertEquals(0.0, delta, 1e-6,
                    "arc sweep does not land on its endpoint for wheel " + arc.wheelIndex());
        }
    }

    @Test
    void sampleAtIsArcLengthParameterized() {
        BeltPath2D path = BeltPath2D.build(track(-0.30));
        double length = path.length();
        int count = path.linkCount(0.2);

        assertEquals(0.0, path.sampleAt(0).point().distance(path.sampleAt(length).point()), 1e-7);
        double maxSpacingError = 0;
        for (int i = 0; i < count; i++) {
            Vec2 a = path.sampleAt(i * length / count).point();
            Vec2 b = path.sampleAt((i + 1) * length / count).point();
            double arcA = boundaryArcAt(path, a);
            double arcB = boundaryArcAt(path, b);
            maxSpacingError = Math.max(maxSpacingError,
                    Math.abs(forwardArcDiff(arcA, arcB, length) - length / count));
        }
        assertEquals(0.0, maxSpacingError, 1e-6);

        for (int i = 0; i < 64; i++) {
            BeltPath2D.Sample sample = path.sampleAt((i + 0.37) * length / 64);
            assertEquals(1.0, sample.tangent().length(), 1e-9);
            assertEquals(1.0, sample.normal().length(), 1e-9);
            assertEquals(0.0, sample.tangent().dot(sample.normal()), 1e-9);
            assertEquals(0.0, path.distanceToBoundary(sample.point()), 1e-6);
        }
    }

    @Test
    void pathsDoNotCutWheelsOrSelfIntersect() {
        for (double road2Y : new double[]{-0.6, -0.58, -0.30, -0.90}) {
            List<Circle2D> circles = track(road2Y);
            BeltPath2D path = BeltPath2D.build(circles);
            assertTrue(minWheelClearance(path, circles) >= -1e-4,
                    "path cuts a wheel at road2Y=" + road2Y);
            assertFalse(selfIntersects(path.points()),
                    "path self-intersects at road2Y=" + road2Y);
        }
    }

    @Test
    void invalidInputsThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> BeltPath2D.build(List.of(new Circle2D(0, 0, 1))));
        assertThrows(IllegalArgumentException.class,
                () -> BeltPath2D.build(List.of(
                        new Circle2D(0, 0, 1),
                        new Circle2D(1, 0, 1),
                        new Circle2D(2, 0, 1)
                )));
        assertThrows(IllegalArgumentException.class,
                () -> BeltPath2D.build(List.of(
                        new Circle2D(0, 0, 1),
                        new Circle2D(1, 0, 0),
                        new Circle2D(0, 1, 1)
                )));
    }

    @Test
    void twoEqualWheelsFormAStadium() {
        List<Circle2D> circles = List.of(
                new Circle2D(-1.5, 0, 1),
                new Circle2D(1.5, 0, 1)
        );
        BeltPath2D path = BeltPath2D.build(circles);

        assertTrue(path.isConvex());
        assertTrue(path.pointContacts().isEmpty());
        assertEquals(2, path.arcs().size());
        for (BeltPath2D.ArcContact arc : path.arcs()) {
            assertEquals(PI, arc.sweep(), 1e-6);
            assertEquals(PI, arc.contactLength(), 1e-6);
        }
        assertEquals(2 * PI + 6, path.length(), 0.005);
        assertEquals(ConvexHull2D.convexPerimeter(circles, 2048), path.length(), 0.005);
    }

    @Test
    void twoUnequalWheelsUseExternalTangents() {
        List<Circle2D> circles = List.of(
                new Circle2D(-2, 0, 2),
                new Circle2D(2, 0, 1)
        );
        BeltPath2D path = BeltPath2D.build(circles);

        double expected = twoCirclePerimeter(2, 1, 4);
        assertTrue(path.isConvex());
        assertEquals(2, path.arcs().size());
        assertEquals(expected, path.length(), 0.005);
        assertEquals(ConvexHull2D.convexPerimeter(circles, 2048), path.length(), 0.01);
    }

    @Test
    void twoContainedWheelsFallBackToOuterCircle() {
        List<Circle2D> circles = List.of(
                new Circle2D(0, 0, 3),
                new Circle2D(0.5, 0, 1)
        );
        BeltPath2D path = BeltPath2D.build(circles);

        assertTrue(path.isConvex());
        assertEquals(1, path.arcs().size());
        assertEquals(2 * PI * 3, path.length(), 0.01);
        assertEquals(PI * 9, Math.abs(path.signedArea()), 0.1);
    }

    @Test
    void twoWheelClockwiseInputKeepsLengthAndNormalizes() {
        List<Circle2D> forward = List.of(
                new Circle2D(-2, 0, 2),
                new Circle2D(2, 0, 1)
        );
        List<Circle2D> backward = List.of(forward.get(1), forward.get(0));

        BeltPath2D a = BeltPath2D.build(forward);
        BeltPath2D b = BeltPath2D.build(backward);

        assertFalse(a.reversedFromInput());
        assertTrue(b.reversedFromInput());
        assertEquals(a.length(), b.length(), 1e-9);
    }

    @Test
    void convexFallbackHandlesThreeCollinearWheels() {
        List<Circle2D> circles = List.of(
                new Circle2D(0, 0, 0.4),
                new Circle2D(1.2, 0, 0.4),
                new Circle2D(2.4, 0, 0.4)
        );

        BeltPath2D path = BeltPath2D.convexFallback(circles);

        assertEquals(0, path.arcs().size());
        assertEquals(0, path.pointContacts().size());
        assertTrue(path.isConvex());
        assertEquals(ConvexHull2D.convexPerimeter(circles, 128), path.length(), 0.01);
        assertTrue(path.sampleAt(0).point().isFinite());
        assertTrue(path.sampleAt(path.length() * 0.37).point().isFinite());
        assertTrue(path.linkCount(0.2) > 0);
    }

    private static double twoCirclePerimeter(double r1, double r2, double distance) {
        double dr = r1 - r2;
        double phi = Math.acos(dr / distance);
        return 2 * Math.sqrt(distance * distance - dr * dr)
                + r1 * (2 * PI - 2 * phi)
                + r2 * (2 * phi);
    }

    @Test
    void convexHullBaselineMatchesAnalyticCases() {
        List<Circle2D> one = List.of(new Circle2D(0, 0, 1));
        assertEquals(2 * PI, ConvexHull2D.convexPerimeter(one, 2048), 0.001);

        List<Circle2D> stadium = List.of(
                new Circle2D(-1.5, 0, 1),
                new Circle2D(1.5, 0, 1)
        );
        assertEquals(2 * PI + 2 * 3, ConvexHull2D.convexPerimeter(stadium, 2048), 0.01);

        List<Vec2> collinear = ConvexHull2D.hull(Arrays.asList(
                new Vec2(0, 0), new Vec2(1, 0), new Vec2(2, 0)
        ));
        assertEquals(2, collinear.size());
        assertEquals(4.0, ConvexHull2D.perimeter(collinear), 1e-9);
    }
}
