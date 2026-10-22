package io.github.sweetzonzi.machine_max.client.render.renderer;

import io.github.sweetzonzi.machine_max.util.geometry.Vec2;
import org.joml.Matrix4f;

/**
 * 单节履带在主动轮局部系中的放置矩阵。
 *
 * <p>包络求解在主动轮局部 (Y, Z) 平面上给出采样点：{@link Vec2#x()} 是竖直分量，
 * {@link Vec2#y()} 是纵向分量，映射回局部系是 {@code (0, x, y)}。这里把 2D 采样点、
 * 切线与外法线拼成 JOML 的 4×4 矩阵，供 {@link TrackRenderer} 的模型 / 程序化两条
 * 路径使用。</p>
 *
 * <p>JOML 的 16 参构造按列主序收参：前 4 个是第 0 列，最后 4 个是平移列。</p>
 */
final class TrackLinkPose {
    private TrackLinkPose() {
    }

    /**
     * 程序化扁长方体：+X = 切线，+Y = 外法线，+Z = 轮轴，原点在节中心。
     *
     * @param wheelAxis 轮轴方向在局部 +X 上的符号，取闭合带绕向的反号以保持右手系
     */
    static Matrix4f procedural(Vec2 tangent, Vec2 normal, Vec2 point, float wheelAxis, float halfThickness) {
        float centerY = (float) (point.x() + normal.x() * halfThickness);
        float centerZ = (float) (point.y() + normal.y() * halfThickness);
        return new Matrix4f(
                0, (float) tangent.x(), (float) tangent.y(), 0,
                0, (float) normal.x(), (float) normal.y(), 0,
                wheelAxis, 0, 0, 0,
                0, centerY, centerZ, 1
        );
    }

    /**
     * 单节模型：+X = 轮轴 / 宽度，+Y = 指向轮组（内法线），+Z = 切线，原点在节外表面。
     *
     * <p>模型与零件模型同一套载具坐标系（X 横向、Y 向上、Z 纵向），履带齿朝 +Y。
     * 上下行程上外法线方向相反，把模型 +Y 对齐内法线可以让履带齿始终朝向轮组。</p>
     */
    static Matrix4f model(Vec2 tangent, Vec2 normal, Vec2 point, float wheelAxis, float thickness) {
        float outerY = (float) (point.x() + normal.x() * thickness);
        float outerZ = (float) (point.y() + normal.y() * thickness);
        return new Matrix4f(
                wheelAxis, 0, 0, 0,
                0, (float) -normal.x(), (float) -normal.y(), 0,
                0, (float) tangent.x(), (float) tangent.y(), 0,
                0, outerY, outerZ, 1
        );
    }
}
