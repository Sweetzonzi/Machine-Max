package io.github.sweetzonzi.machine_max.track;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackEnvelope2D;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrackEnvelope2DTest {
    private static final float THICKNESS = .05f;
    private static final List<Float> RADII = List.of(.45f, .45f, .45f, .45f, .45f);

    private static List<Matrix4f> poses(float spin, int direction) {
        List<Matrix4f> poses = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            poses.add(new Matrix4f().translation(0, i == 2 ? .2f : 0, direction * i * 1.2f)
                    .rotateX(i == 0 ? spin : i * .37f));
        }
        return poses;
    }

    @Test
    void yzProjectionKeepsBottomPointAndTopArcOnBothSides() {
        for (int direction : new int[]{-1, 1}) {
            var poses = poses(0, direction);
            boolean clockwise = TrackEnvelope2D.layingClockwise(poses);
            var result = TrackEnvelope2D.buildPoses(poses, RADII, THICKNESS, clockwise);
            assertNotNull(result);
            assertFalse(result.fallback());
            assertEquals(direction > 0, clockwise);
            assertTrue(result.path().arcs().stream().anyMatch(a -> a.wheelIndex() == 2));
            var point = result.path().pointContacts().stream().filter(p -> p.wheelIndex() == 2)
                    .findFirst().orElseThrow().point();
            Vector3f world = result.sprocketWorld().transformPosition(new Vector3f(0, (float) point.x(), (float) point.y()));
            assertEquals(-.2, world.y, 1e-5);
            assertEquals(direction * 2.4, world.z, 1e-5);
        }
    }

    @Test
    void sprocketSpinDoesNotChangeContactsOrWorldGeometry() {
        boolean clockwise = TrackEnvelope2D.layingClockwise(poses(0, 1));
        var baseline = TrackEnvelope2D.buildPoses(poses(0, 1), RADII, THICKNESS, clockwise);
        assertNotNull(baseline);
        for (int degrees = 0; degrees < 360; degrees += 15) {
            var result = TrackEnvelope2D.buildPoses(poses((float) Math.toRadians(degrees), 1),
                    RADII, THICKNESS, clockwise);
            assertNotNull(result, "spin=" + degrees);
            assertFalse(result.fallback());
            assertEquals(baseline.path().length(), result.path().length(), 1e-4);
            assertEquals(baseline.path().vertices().size(), result.path().vertices().size());
            var point = result.path().pointContacts().stream().filter(p -> p.wheelIndex() == 2)
                    .findFirst().orElseThrow().point();
            Vector3f world = result.sprocketWorld().transformPosition(new Vector3f(0, (float) point.x(), (float) point.y()));
            assertEquals(-.2, world.y, 1e-5);
            assertEquals(2.4, world.z, 1e-5);
            assertEquals(clockwise,
                    TrackEnvelope2D.layingClockwise(poses((float) Math.toRadians(degrees), 1)));
        }
    }

    @Test
    void savedTopologyRotatesWithTheWheelGroupIncludingUpsideDown() {
        var original = poses(.3f, 1);
        boolean clockwise = TrackEnvelope2D.layingClockwise(original);
        var baseline = TrackEnvelope2D.buildPoses(original, RADII, THICKNESS, clockwise);
        assertNotNull(baseline);
        for (float pitch : new float[]{.7f, 1.5f, (float) Math.PI}) {
            Matrix4f transform = new Matrix4f().translation(9, 17, -13).rotateXYZ(pitch, .6f, .9f);
            List<Matrix4f> moved = original.stream().map(p -> new Matrix4f(transform).mul(p)).toList();
            var result = TrackEnvelope2D.buildPoses(moved, RADII, THICKNESS, clockwise);
            assertNotNull(result);
            assertFalse(result.fallback());
            assertEquals(baseline.path().length(), result.path().length(), 1e-4);
            var point = result.path().pointContacts().stream().filter(p -> p.wheelIndex() == 2)
                    .findFirst().orElseThrow().point();
            Vector3f actual = result.sprocketWorld().transformPosition(new Vector3f(0, (float) point.x(), (float) point.y()));
            Vector3f expected = transform.transformPosition(new Vector3f(0, -.2f, 2.4f));
            assertTrue(actual.distance(expected) < 1e-4);
        }
    }

    @Test
    void twoWheelPathsPreserveSavedDirectionAndSprocketAnchor() {
        for (boolean clockwise : new boolean[]{false, true}) {
            for (int degrees = 0; degrees < 360; degrees += 30) {
                List<Matrix4f> poses = List.of(new Matrix4f().rotateX((float) Math.toRadians(degrees)),
                        new Matrix4f().translation(0, 0, 2));
                var result = TrackEnvelope2D.buildPoses(poses, List.of(.45f, .65f), THICKNESS, clockwise);
                assertNotNull(result);
                assertEquals(clockwise ? -1 : 1, Math.signum(result.path().signedArea()));
                assertEquals(0, result.path().vertices().getFirst().wheelIndex());
            }
        }
    }

    @Test
    void thicknessKeepsUnequalWheelOuterSurfacesAtTheCollisionGroundPlane() {
        List<Float> collisionRadii = List.of(.55f, .8f);
        List<Matrix4f> poses = List.of(new Matrix4f().translation(0, .55f, 0),
                new Matrix4f().translation(0, .8f, 2));
        for (boolean clockwise : new boolean[]{false, true}) {
            var result = TrackEnvelope2D.buildPoses(poses, collisionRadii, THICKNESS, clockwise);
            assertNotNull(result);
            assertFalse(result.fallback());
            assertEquals(.5, result.path().inputCircles().get(0).radius(), 1e-6);
            assertEquals(.75, result.path().inputCircles().get(1).radius(), 1e-6);
            var bottomEdge = result.path().edges().stream()
                    .filter(edge -> Math.abs(edge.start().x() + .5) < 1e-6
                            && Math.abs(edge.end().x() + .5) < 1e-6)
                    .findFirst().orElseThrow();
            var sample = result.path().sampleAt(bottomEdge.startDistance() + bottomEdge.length() * .5);
            assertEquals(-1, sample.normal().x(), 1e-6);
            for (double offset : new double[]{0, THICKNESS * .5, THICKNESS}) {
                var point = sample.point().add(sample.normal().mul(offset));
                Vector3f world = result.sprocketWorld()
                        .transformPosition(new Vector3f(0, (float) point.x(), (float) point.y()));
                assertEquals(THICKNESS - offset, world.y, 1e-6);
            }
        }
        assertEquals(List.of(.55f, .8f), collisionRadii);
    }

    @Test
    void lengthAndOverlapFallbackUseInnerSurfaceRadii() {
        var twoWheels = TrackEnvelope2D.buildPoses(List.of(new Matrix4f(),
                new Matrix4f().translation(0, 0, 2)), List.of(.55f, .55f), THICKNESS, false);
        assertNotNull(twoWheels);
        assertEquals(4 + Math.PI, twoWheels.path().length(), 1e-4);

        var overlapping = TrackEnvelope2D.buildPoses(List.of(new Matrix4f(),
                new Matrix4f().translation(0, 0, .6f),
                new Matrix4f().translation(0, .5f, 1.2f)),
                List.of(.55f, .55f, .55f), THICKNESS, false);
        assertNotNull(overlapping);
        assertTrue(overlapping.fallback());
        overlapping.path().inputCircles().forEach(circle -> assertEquals(.5, circle.radius(), 1e-6));
    }

    @Test
    void invalidThicknessOrNonPositiveInnerRadiusIsRejected() {
        List<Matrix4f> poses = List.of(new Matrix4f(), new Matrix4f().translation(0, 0, 2));
        for (float thickness : new float[]{0, -.05f, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> TrackEnvelope2D.buildPoses(poses, List.of(.55f, .8f), thickness, false));
        }
        for (float radius : new float[]{THICKNESS, .01f, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> TrackEnvelope2D.buildPoses(poses, List.of(.55f, radius), THICKNESS, false));
        }
    }
}
