package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import io.github.sweetzonzi.ballistics_framework.api.trajectory.BallisticConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudBallisticTableTest {
    @Test void tableInterpolatesLowArcAndRejectsUnavailableDistances() {
        var table = HudBallisticTable.solve(new BallisticConfig(0, 1, 0, 9.81f, 1f / 60, 600), 1000, 2000);
        double expected = Math.asin(9.81 * 1000 / 1_000_000) / 2;
        assertEquals(expected, table.hold(1000), 1e-6);
        assertTrue(table.hold(1001) > table.hold(1000));
        assertTrue(table.hold(1001) < table.hold(1015));
        assertEquals(0, table.hold(-1));
        assertEquals(0, table.hold(Double.NaN));
        assertEquals(0, table.hold(Double.POSITIVE_INFINITY));
        assertEquals(0, table.hold(2001));
        assertEquals(0, HudBallisticTable.EMPTY.hold(1000));
    }

    @Test void gravityFreeBallisticsAndExpiredSolutionsHaveDefinedFallbacks() {
        var straight = HudBallisticTable.solve(new BallisticConfig(0, 1, 0, 0, 1f / 60, 600), 1000, 2000);
        assertEquals(0, straight.hold(1000));
        var expired = HudBallisticTable.solve(new BallisticConfig(0, 1, 0, 9.81f, 1f / 60, 6), 1000, 2000);
        assertEquals(0, expired.hold(1000));
    }

    @Test void dragModelProducesFiniteIncreasingCorrections() {
        var table = HudBallisticTable.solve(new BallisticConfig(0.3f, 1, 0.001f, 9.81f, 1f / 60, 600), 500, 2000);
        assertTrue(Double.isFinite(table.hold(500)));
        assertTrue(table.hold(500) > 0);
        assertTrue(table.hold(1000) > table.hold(500));
    }
}
