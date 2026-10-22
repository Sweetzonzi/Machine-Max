package io.github.sweetzonzi.machine_max.client.render.renderer;

import io.github.sweetzonzi.machine_max.util.geometry.Vec2;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 单节履带放置矩阵的轴向约定；纯矩阵运算，不加载 Minecraft。
 */
class TrackLinkPoseTest {
    private static final Vec2 TANGENT = new Vec2(0.6, 0.8);
    private static final Vec2 NORMAL = new Vec2(0.8, -0.6);
    private static final Vec2 POINT = new Vec2(1.5, -2.5);
    private static final float THICKNESS = 0.05f;
    /** 逆时针闭环（有向面积 &gt; 0）时渲染器取轮轴符号 -1，基才是右手系。 */
    private static final float WHEEL_AXIS = -1f;

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 1.0e-6f);
        assertEquals(expected.y, actual.y, 1.0e-6f);
        assertEquals(expected.z, actual.z, 1.0e-6f);
    }

    private static void assertBasis(Matrix4f pose, Vector3f x, Vector3f y, Vector3f z) {
        assertVector(x, pose.getColumn(0, new Vector3f()));
        assertVector(y, pose.getColumn(1, new Vector3f()));
        assertVector(z, pose.getColumn(2, new Vector3f()));
    }

    private static float determinant(Matrix4f pose) {
        Vector3f x = pose.getColumn(0, new Vector3f());
        Vector3f y = pose.getColumn(1, new Vector3f());
        Vector3f z = pose.getColumn(2, new Vector3f());
        return x.dot(y.cross(z, new Vector3f()));
    }

    @Test
    void proceduralBasisIsTangentNormalAxleAtLinkCenter() {
        Matrix4f pose = TrackLinkPose.procedural(TANGENT, NORMAL, POINT, WHEEL_AXIS, THICKNESS * 0.5f);

        // +X = 切线，+Y = 外法线，+Z = 轮轴：2D 的 (x, y) 依次是局部 Y / Z
        assertBasis(pose,
                new Vector3f(0f, (float) TANGENT.x(), (float) TANGENT.y()),
                new Vector3f(0f, (float) NORMAL.x(), (float) NORMAL.y()),
                new Vector3f(WHEEL_AXIS, 0f, 0f));
        // 原点在节中心：采样点沿外法线偏移半个厚度
        assertVector(new Vector3f(0f,
                        (float) (POINT.x() + NORMAL.x() * THICKNESS * 0.5),
                        (float) (POINT.y() + NORMAL.y() * THICKNESS * 0.5)),
                pose.getTranslation(new Vector3f()));
        assertEquals(1f, determinant(pose), 1.0e-6f);
    }

    @Test
    void modelBasisIsAxleInwardNormalTangentAtOuterSurface() {
        Matrix4f pose = TrackLinkPose.model(TANGENT, NORMAL, POINT, WHEEL_AXIS, THICKNESS);

        // +X = 轮轴，+Y = 指向轮组（内法线），+Z = 切线
        assertBasis(pose,
                new Vector3f(WHEEL_AXIS, 0f, 0f),
                new Vector3f(0f, (float) -NORMAL.x(), (float) -NORMAL.y()),
                new Vector3f(0f, (float) TANGENT.x(), (float) TANGENT.y()));
        // 原点在节外表面：采样点沿外法线偏移一个完整厚度
        assertVector(new Vector3f(0f,
                        (float) (POINT.x() + NORMAL.x() * THICKNESS),
                        (float) (POINT.y() + NORMAL.y() * THICKNESS)),
                pose.getTranslation(new Vector3f()));
        assertEquals(1f, determinant(pose), 1.0e-6f);
    }
}
