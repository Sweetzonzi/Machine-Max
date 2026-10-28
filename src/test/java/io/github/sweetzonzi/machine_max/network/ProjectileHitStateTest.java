package io.github.sweetzonzi.machine_max.network;

import io.github.sweetzonzi.machine_max.network.payload.projectile.ProjectilesHitPayload.HitEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectileHitStateTest {
    private static HitEntry entry(double[] values) {
        return new HitEntry(59, values[0], values[1], values[2], values[3], values[4], values[5],
                false, values[6], values[7], values[8], -2199022858304L);
    }

    @Test
    void replayedNonFinite50mmHitIsRejectedBeforeClientEffects() {
        assertFalse(entry(new double[]{-7.222951889, -63, 96.341339, 0, 1, 0,
                Double.NaN, Double.NaN, Double.NaN}).isFinite());
    }

    @Test
    void everyGeometryAndVelocityComponentMustFitTheClientFloatArrays() {
        double[] valid = {-7.222951889, -63, 96.341339, 0, 1, 0, -30, -50, 800};
        assertTrue(entry(valid).isFinite());
        for (int component = 0; component < valid.length; component++) {
            for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, Double.MAX_VALUE, -Double.MAX_VALUE}) {
                double[] values = valid.clone();
                values[component] = invalid;
                assertFalse(entry(values).isFinite(), "component=" + component + " value=" + invalid);
            }
        }
    }
}
