package io.github.sweetzonzi.machine_max.util.geometry;

/**
 * 纯 2D 双精度向量。
 *
 * <p>这个包只做几何计算，不依赖 Minecraft、JME 或 JOML，服务端推导长度和客户端渲染共用。</p>
 */
public record Vec2(double x, double y) {
    public static final Vec2 ZERO = new Vec2(0, 0);
    public static final Vec2 UNIT_X = new Vec2(1, 0);
    public static final Vec2 UNIT_Y = new Vec2(0, 1);
    private static final double EPS = 1e-12;

    public Vec2 add(Vec2 other) {
        return new Vec2(x + other.x, y + other.y);
    }

    public Vec2 sub(Vec2 other) {
        return new Vec2(x - other.x, y - other.y);
    }

    public Vec2 negate() {
        return new Vec2(-x, -y);
    }

    public Vec2 mul(double scalar) {
        return new Vec2(x * scalar, y * scalar);
    }

    public Vec2 div(double scalar) {
        if (Math.abs(scalar) <= EPS) {
            throw new ArithmeticException("division by zero");
        }
        return new Vec2(x / scalar, y / scalar);
    }

    public double dot(Vec2 other) {
        return x * other.x + y * other.y;
    }

    public double cross(Vec2 other) {
        return x * other.y - y * other.x;
    }

    public double lengthSquared() {
        return x * x + y * y;
    }

    public double length() {
        return Math.hypot(x, y);
    }

    public double distance(Vec2 other) {
        return Math.hypot(x - other.x, y - other.y);
    }

    /**
     * 单位化；零向量返回 {@link #ZERO}，调用侧对零向量单独处理。
     */
    public Vec2 normalized() {
        double length = length();
        if (length <= EPS) {
            return ZERO;
        }
        return new Vec2(x / length, y / length);
    }

    /** 逆时针旋转 90°。 */
    public Vec2 leftNormal() {
        return new Vec2(-y, x);
    }

    /** 顺时针旋转 90°；逆时针闭环的外法线方向。 */
    public Vec2 rightNormal() {
        return new Vec2(y, -x);
    }

    public Vec2 rotate(double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec2(x * cos - y * sin, x * sin + y * cos);
    }

    public double angle() {
        return Math.atan2(y, x);
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y);
    }

    public static Vec2 fromAngle(double radians, double radius) {
        return new Vec2(Math.cos(radians) * radius, Math.sin(radians) * radius);
    }
}
