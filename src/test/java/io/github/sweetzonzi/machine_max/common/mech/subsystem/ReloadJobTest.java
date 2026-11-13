package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.SupplierRef;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReloadJob} 的推进与分配契约：进度单调不减、到点即完成、推进量由执行者给出而不是自己走秒。
 * 作业不存档，也不关心推进量从哪来——缺弹与机构受阻都表现为本 tick 的推进量为 0。
 * <p>
 * 受理时定下的四项分配（身份、交付目标、实际来源、冻结规格与份额）在作业存续期间不变：机构占用与
 * 架预留按它们匹配，换架或换规格要作废旧作业并重新申请。
 */
class ReloadJobTest {

    private static final SupplierRef TARGET = new SupplierRef(4, "m1a2_sep.ammo_autoloader");
    private static final SupplierRef ORIGIN = new SupplierRef(5, "m1a2_sep.ammo_loader");
    private static final AmmoBelt SPEC = new AmmoBelt(null, "test_belt",
            List.of(new AmmoBelt.PatternEntry(ResourceLocation.parse("machine_max:test_round"), 5)), 5, 5, null);
    private static final ReloadJobKey KEY = new ReloadJobKey(3, "m1a2_sep.launcher", 7);

    private static ReloadJob job(int totalTicks) {
        return new ReloadJob(KEY, TARGET, ORIGIN, SPEC, 5, totalTicks);
    }

    @Test
    void startsEmptyAndCompletesAfterExactlyTotalTicks() {
        ReloadJob job = job(9);
        assertEquals(0, job.elapsedTicks());
        assertEquals(9, job.remainingTicks());
        assertEquals(0f, job.progress());
        assertFalse(job.isComplete());

        job.advance(4);
        assertEquals(4, job.elapsedTicks());
        assertEquals(5, job.remainingTicks());
        assertFalse(job.isComplete());

        job.advance(5);
        assertEquals(9, job.elapsedTicks());
        assertEquals(0, job.remainingTicks());
        assertEquals(1f, job.progress());
        assertTrue(job.isComplete());
    }

    @Test
    void stalledTickKeepsProgressAndDoesNotCountAsElapsed() {
        ReloadJob job = job(4);
        job.advance(2);
        job.advance(0);   // 缺弹或机构受阻：本 tick 推不动
        job.advance(-3);  // 负推进量不倒退
        assertEquals(2, job.elapsedTicks());
        assertFalse(job.isComplete());
    }

    @Test
    void elapsedIsClampedAtTotalSoProgressNeverExceedsOne() {
        ReloadJob job = job(3);
        job.advance(10);
        assertEquals(3, job.elapsedTicks());
        assertEquals(1f, job.progress());
        assertTrue(job.isComplete());
    }

    @Test
    void totalTicksIsAtLeastOneSoAZeroLengthSegmentStillCompletes() {
        ReloadJob job = job(0);
        assertEquals(1, job.totalTicks());
        job.advance(1);
        assertTrue(job.isComplete());
    }

    @Test
    void allocationIsKeptAsGivenAndNeverRewrittenByAdvance() {
        ReloadJob job = job(5);
        assertEquals(KEY, job.key());
        assertEquals(7, job.jobId());
        assertEquals(TARGET, job.deliveryTarget());
        assertEquals(ORIGIN, job.upstreamOrigin());
        assertEquals(5, job.reservedCount());
        assertEquals(SPEC, job.spec());

        job.advance(3);
        assertEquals(KEY, job.key());
        assertEquals(ORIGIN, job.upstreamOrigin());
        assertEquals(SPEC, job.spec());
        assertEquals(5, job.reservedCount());
    }

    @Test
    void negativeReservationIsClampedToZero() {
        ReloadJob job = new ReloadJob(KEY, TARGET, ORIGIN, SPEC, -2, 5);
        assertEquals(0, job.reservedCount());
    }
}
