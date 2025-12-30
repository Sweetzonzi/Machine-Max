package io.github.sweetzonzi.machine_max.util.data;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import lombok.Getter;

@Getter
public enum Axis {

    // 平动 / 方向轴
    X(0),
    Y(1),
    Z(2),

    // 旋转轴（仅用于关节 / 约束标识）
    XR(3),
    YR(4),
    ZR(5),

    // 带方向的装配轴
    XP(10),
    YP(11),
    ZP(12),
    XN(-10),
    YN(-11),
    ZN(-12);

    private final int value;

    Axis(int value) {
        this.value = value;
    }

    /* ------------------------- 基础查找接口 ------------------------- */

    /**
     * 根据 int 值获取对应的枚举名称
     */
    public static String fromValue(int value) {
        for (Axis axis : values()) {
            if (axis.value == value) {
                return axis.name();
            }
        }
        throw new IllegalArgumentException("No Axis with value: " + value);
    }

    /**
     * 根据枚举名称获取对应的 int 值
     */
    public static int getValue(String name) {
        try {
            return Axis.valueOf(name.toUpperCase()).value;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid axis name: " + name, e);
        }
    }

    /* ------------------------- 语义判断 ------------------------- */

    /**
     * 是否为带正负方向的装配轴
     */
    public boolean isDirectional() {
        return this == XP || this == XN
                || this == YP || this == YN
                || this == ZP || this == ZN;
    }

    /**
     * 是否为旋转轴（XR / YR / ZR）
     */
    public boolean isRotational() {
        return this == XR || this == YR || this == ZR;
    }

    /* ------------------------- 数学工具 ------------------------- */

    /**
     * 将 Axis 枚举转换为本地空间单位向量
     * <p>
     * 注意：
     * - 不允许传入旋转轴（XR / YR / ZR）
     *
     * @param axis 轴枚举
     * @return 本地空间单位向量
     */
    public static Vector3f axisToVector(Axis axis) {
        return switch (axis) {
            case XP, X -> new Vector3f(1, 0, 0);
            case XN -> new Vector3f(-1, 0, 0);
            case YP, Y -> new Vector3f(0, 1, 0);
            case YN -> new Vector3f(0, -1, 0);
            case ZP, Z -> new Vector3f(0, 0, 1);
            case ZN -> new Vector3f(0, 0, -1);
            default -> throw new IllegalArgumentException(
                    "Axis " + axis + " cannot be converted to direction vector"
            );
        };
    }

    /**
     * 构造绕指定法线轴的离散 90° 旋转
     *
     * @param normalAxis  本地空间的法线轴（必须是方向轴）
     * @param rotationDeg 旋转角度（必须是 90° 的倍数）
     * @return 对应的旋转四元数
     */
    public static Quaternion discreteTwist(Axis normalAxis, float rotationDeg) {
        if (!normalAxis.isDirectional()) {
            throw new IllegalArgumentException(
                    "Axis " + normalAxis + " is not a directional axis"
            );
        }

        Quaternion q = new Quaternion();
        if (rotationDeg != 0) {
            q.fromAngleNormalAxis(
                    (float) Math.toRadians(rotationDeg),
                    axisToVector(normalAxis)
            );
        }
        return q;
    }

    /* ------------------------- Codec 定义 ------------------------- */

    /**
     * Axis 的通用 Codec
     * <p>
     * JSON / datapack 示例：
     * {
     *   "normal": "XP"
     * }
     */
    public static final Codec<Axis> CODEC =
            Codec.STRING.xmap(
                    name -> {
                        try {
                            return Axis.valueOf(name.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException("Unknown Axis: " + name, e);
                        }
                    },
                    axis -> axis.name().toUpperCase()
            );
}

