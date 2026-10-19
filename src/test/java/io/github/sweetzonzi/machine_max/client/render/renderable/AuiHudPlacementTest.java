package io.github.sweetzonzi.machine_max.client.render.renderable;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuiHudPlacementTest {
    @Test
    void centerRemainsAtScreenCenterAcrossViewportAndElementScales() {
        for (float viewportScale : new float[]{427f / 1920f, 0.5f, 1f, 1.5f}) {
            for (double[] elementScale : new double[][]{{1, 1}, {2, 1}, {1, 2}, {0.5, 1.75}}) {
                AuiHudPlacement placement = AuiHudPlacement.resolve(280, 118, 427, 240,
                        viewportScale, elementScale[0], elementScale[1], 0, 0, 1, 1);
                Vector3f center = matrix(placement).transformPosition(new Vector3f(140, 59, 0));
                assertEquals(213.5f, center.x, 0.001f);
                assertEquals(120f, center.y, 0.001f);
            }
        }
    }

    @Test
    void rightBottomMarginsStayInGuiPixels() {
        AuiHudPlacement placement = AuiHudPlacement.resolve(280, 118, 640, 360,
                0.25f, 2, 0.5, -16, -12, 2, 2);
        Vector3f bottomRight = matrix(placement).transformPosition(new Vector3f(280, 118, 0));
        assertEquals(624f, bottomRight.x, 0.001f);
        assertEquals(348f, bottomRight.y, 0.001f);
    }

    @Test
    void verticalScaleChangesOnlyHeightAtTopLeftAnchor() {
        AuiHudPlacement normal = AuiHudPlacement.resolve(280, 118, 640, 360,
                0.5f, 1, 1, 16, 12, 0, 0);
        AuiHudPlacement tall = AuiHudPlacement.resolve(280, 118, 640, 360,
                0.5f, 1, 2, 16, 12, 0, 0);
        Vector3f normalCorner = matrix(normal).transformPosition(new Vector3f(280, 118, 0));
        Vector3f tallCorner = matrix(tall).transformPosition(new Vector3f(280, 118, 0));
        assertEquals(16f, tall.x(), 0.001f);
        assertEquals(12f, tall.y(), 0.001f);
        assertEquals(normalCorner.x, tallCorner.x, 0.001f);
        assertEquals((normalCorner.y - 12f) * 2f, tallCorner.y - 12f, 0.001f);
    }

    private static Matrix4f matrix(AuiHudPlacement placement) {
        return new Matrix4f().translation(placement.x(), placement.y(), 0)
                .scale(placement.scaleX(), placement.scaleY(), 1);
    }
}
