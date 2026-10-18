package io.github.sweetzonzi.machine_max.util.geometry;

/**
 * 平面圆：轮心 + 半径，单位与调用方一致（履带使用米）。
 */
public record Circle2D(double x, double y, double radius) {
    public Circle2D {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("circle center must be finite");
        }
        if (!Double.isFinite(radius) || radius < 0) {
            throw new IllegalArgumentException("circle radius must be finite and >= 0");
        }
    }

    public Vec2 center() {
        return new Vec2(x, y);
    }

    public Vec2 pointAt(double radians) {
        return new Vec2(x + Math.cos(radians) * radius, y + Math.sin(radians) * radius);
    }
}
