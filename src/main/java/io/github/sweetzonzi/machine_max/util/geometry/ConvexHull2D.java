package io.github.sweetzonzi.machine_max.util.geometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 2D 凸包工具。
 *
 * <p>用于履带包络的凸包基线、两轮退化布局的 fallback，以及测试对照。
 * 输入点集是无序的；输出为逆时针凸包，共线点被剔除。</p>
 */
public final class ConvexHull2D {
    private static final double EPS = 1e-12;

    private ConvexHull2D() {
    }

    private static final Comparator<Vec2> POINT_ORDER = Comparator
            .comparingDouble(Vec2::x)
            .thenComparingDouble(Vec2::y);

    public static double cross(Vec2 o, Vec2 a, Vec2 b) {
        return (a.x() - o.x()) * (b.y() - o.y()) - (a.y() - o.y()) * (b.x() - o.x());
    }

    /**
     * @return 逆时针凸包；输入为空返回空列表，单点/两点按原样返回。
     */
    public static List<Vec2> hull(List<Vec2> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        List<Vec2> sorted = new ArrayList<>(points);
        sorted.sort(POINT_ORDER);

        List<Vec2> unique = new ArrayList<>(sorted.size());
        for (Vec2 point : sorted) {
            if (!point.isFinite()) {
                throw new IllegalArgumentException("point must be finite: " + point);
            }
            Vec2 last = unique.isEmpty() ? null : unique.get(unique.size() - 1);
            if (last == null || Math.abs(point.x() - last.x()) > EPS
                    || Math.abs(point.y() - last.y()) > EPS) {
                unique.add(point);
            }
        }
        if (unique.size() <= 1) {
            return List.copyOf(unique);
        }

        List<Vec2> lower = new ArrayList<>();
        for (Vec2 point : unique) {
            while (lower.size() >= 2
                    && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), point) <= EPS) {
                lower.remove(lower.size() - 1);
            }
            lower.add(point);
        }

        List<Vec2> upper = new ArrayList<>();
        for (int i = unique.size() - 1; i >= 0; i--) {
            Vec2 point = unique.get(i);
            while (upper.size() >= 2
                    && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), point) <= EPS) {
                upper.remove(upper.size() - 1);
            }
            upper.add(point);
        }

        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);
        return List.copyOf(lower);
    }

    /**
     * 每个圆的边界按固定段数采样；零半径圆只贡献圆心。
     */
    public static List<Vec2> sampleCircles(List<Circle2D> circles, int segments) {
        if (segments < 3) {
            throw new IllegalArgumentException("segments must be >= 3");
        }
        List<Vec2> points = new ArrayList<>(circles.size() * segments);
        for (Circle2D circle : circles) {
            if (circle.radius() <= EPS) {
                points.add(circle.center());
                continue;
            }
            for (int i = 0; i < segments; i++) {
                points.add(circle.pointAt((Math.PI * 2.0 * i) / segments));
            }
        }
        return points;
    }

    public static List<Vec2> hullOfCircles(List<Circle2D> circles, int segments) {
        return hull(sampleCircles(circles, segments));
    }

    public static double perimeter(List<Vec2> points) {
        if (points == null || points.size() < 2) {
            return 0;
        }
        double length = 0;
        for (int i = 0; i < points.size(); i++) {
            length += points.get(i).distance(points.get((i + 1) % points.size()));
        }
        return length;
    }

    public static double convexPerimeter(List<Circle2D> circles, int segments) {
        return perimeter(hullOfCircles(circles, segments));
    }
}
