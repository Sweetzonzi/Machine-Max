package io.github.sweetzonzi.machine_max.part;

import io.github.sweetzonzi.machine_max.common.item.prop.WeldingTorchItem;
import io.github.sweetzonzi.machine_max.common.mech.part.PartStaleness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartStalenessTest {
    @Test
    void mapsContinuousProgressToSixteenRenderStages() {
        assertEquals(0, PartStaleness.stage(0f));
        assertEquals(1, PartStaleness.stage(1f / 32f));
        assertEquals(8, PartStaleness.stage(0.5f));
        assertEquals(16, PartStaleness.stage(1f));
    }

    @Test
    void reachesFullWeatheringAfterConfiguredDuration() {
        int fullWeatheringTicks = 2000;
        float staleness = 0f;
        for (int tick = 0; tick < fullWeatheringTicks - 1; tick++) {
            staleness = PartStaleness.advance(staleness, fullWeatheringTicks);
        }
        assertTrue(staleness < 1f);
        assertEquals(1f, PartStaleness.advance(staleness, fullWeatheringTicks));
    }

    @Test
    void configuredWeatheringDurationControlsProgressRate() {
        assertEquals(0.1f, PartStaleness.advance(0f, 10), 0.00001f);
        assertEquals(0.01f, PartStaleness.advance(0f, 100), 0.00001f);
        assertThrows(IllegalArgumentException.class, () -> PartStaleness.advance(0f, 0));
    }

    @Test
    void weldingRepairDurationTracksPartDurabilityAndRepairRate() {
        assertEquals(0f, repairAfterTicks(100f, 1f, 100), 0.00001f);
        assertEquals(0f, repairAfterTicks(200f, 1f, 200), 0.00001f);
        assertEquals(0f, repairAfterTicks(200f, 2f, 100), 0.00001f);
        assertEquals(0.95f, repairAfterTicks(100f, 1f, 5), 0.00001f);
        assertEquals(0.975f, repairAfterTicks(200f, 1f, 5), 0.00001f);
        assertThrows(IllegalArgumentException.class, () -> PartStaleness.repair(0.5f, -1f, 100f));
        assertThrows(IllegalArgumentException.class, () -> PartStaleness.repair(0.5f, 1f, 0f));
    }

    private static float repairAfterTicks(float partMaxDurability, float durabilityRepairPerTick, int ticks) {
        float staleness = 1f;
        int pulseTicks = WeldingTorchItem.WELDING_REPAIR_PULSE_TICKS;
        float repairedDurability = pulseTicks * durabilityRepairPerTick;
        int pulseCount = ticks / pulseTicks;
        for (int pulse = 0; pulse < pulseCount; pulse++) {
            staleness = PartStaleness.repair(staleness, repairedDurability, partMaxDurability);
        }
        return staleness;
    }

    @Test
    void rejectsNonFiniteProgress() {
        assertThrows(IllegalArgumentException.class, () -> PartStaleness.clamp(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> PartStaleness.requireValid(Float.POSITIVE_INFINITY));
    }
}
