package io.github.sweetzonzi.machine_max.client.render.renderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleRenderDistanceTest {

    @Test
    void entityPathCoversSixteenChunks() {
        assertEquals(256.0 * 256.0, VehicleRenderDistance.entityRenderDistanceSqr(), 1.0e-9);
    }

    @Test
    void entityAndDistantRenderersHaveOneExclusiveBoundary() {
        double boundarySqr = VehicleRenderDistance.entityRenderDistanceSqr();

        assertTrue(VehicleRenderDistance.isEntityRenderOwnerSqr(boundarySqr - 0.01));
        assertFalse(VehicleRenderDistance.isEntityRenderOwnerSqr(boundarySqr));
        assertFalse(VehicleRenderDistance.isEntityRenderOwnerSqr(boundarySqr + 0.01));
    }

    @Test
    void handoffDistanceUsesThreeDimensionalCameraDistance() {
        double boundary = Math.sqrt(VehicleRenderDistance.entityRenderDistanceSqr() + 0.01);

        assertFalse(VehicleRenderDistance.isEntityRenderOwner(0, boundary, 0));
    }
}
