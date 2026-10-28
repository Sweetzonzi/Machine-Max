package io.github.sweetzonzi.machine_max.common.mech.projectile;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

import static io.github.sweetzonzi.machine_max.common.mech.projectile.BallisticProjectile.AfterHitResult;
import static org.junit.jupiter.api.Assertions.*;

class AfterHitResultTest {
    @Test
    void invalidPenetrationResultsStopInsteadOfPublishingNonFiniteVelocity() {
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertSame(AfterHitResult.DESTROYED, AfterHitResult.passThrough(invalid, new Vector3f(0, 0, 835)));
            assertSame(AfterHitResult.DESTROYED, AfterHitResult.passThrough(0.8f, new Vector3f(invalid, 0, 835)));
        }
        assertSame(AfterHitResult.DESTROYED, AfterHitResult.passThrough(0f, new Vector3f(0, 0, 835)));
        assertSame(AfterHitResult.DESTROYED, AfterHitResult.passThrough(Float.MAX_VALUE, new Vector3f(0, 0, 835)));
    }

    @Test
    void invalidRicochetNormalCannotPublishNonFiniteVelocity() {
        assertSame(AfterHitResult.DESTROYED,
                AfterHitResult.ricochet(0.8f, new Vector3f(0, -10, 835), new Vector3f(0, Float.NaN, 0)));
    }

    @Test
    void finitePenetrationAndRicochetKeepTheirVelocityAndDoNotMutateInputs() {
        Vector3f velocity = new Vector3f(0, -10, 800);
        assertEquals(new Vector3f(0, -8, 640), AfterHitResult.passThrough(0.8f, velocity).newVelocity());
        assertEquals(new Vector3f(0, 8, 640),
                AfterHitResult.ricochet(0.8f, velocity, Vector3f.UNIT_Y).newVelocity());
        assertEquals(new Vector3f(0, -10, 800), velocity);
    }
}
