package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.SupplierRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReloadJob} 的推进契约：进度单调不减、到点即完成、推进量由执行者给出而不是自己走秒。
 * 作业不存档，也不关心推进量从哪来——缺弹与机构受阻都表现为本 tick 的推进量为 0。
 * <p>
 * 身份（{@link ReloadJob#jobId()}）在构造时定下：机构占用与架预留按它匹配，释放只认这个编号，
 * 因此同一台管先后两次作业不会互相认领。
 */
class ReloadJobTest {

    private static final SupplierRef TARGET = new SupplierRef(UUID.randomUUID(), "turret", "2s38_autoloader");

    @Test
    void startsEmptyAndCompletesAfterExactlyTotalTicks() {
        ReloadJob job = new ReloadJob(7, TARGET, 9);
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
        ReloadJob job = new ReloadJob(7, TARGET, 4);
        job.advance(2);
        job.advance(0);   // 缺弹或机构受阻：本 tick 推不动
        job.advance(-3);  // 负推进量不倒退
        assertEquals(2, job.elapsedTicks());
        assertFalse(job.isComplete());
    }

    @Test
    void elapsedIsClampedAtTotalSoProgressNeverExceedsOne() {
        ReloadJob job = new ReloadJob(7, TARGET, 3);
        job.advance(10);
        assertEquals(3, job.elapsedTicks());
        assertEquals(1f, job.progress());
        assertTrue(job.isComplete());
    }

    @Test
    void totalTicksIsAtLeastOneSoAZeroLengthSegmentStillCompletes() {
        ReloadJob job = new ReloadJob(7, TARGET, 0);
        assertEquals(1, job.totalTicks());
        job.advance(1);
        assertTrue(job.isComplete());
    }

    @Test
    void identityAndTargetAreKeptAsGiven() {
        ReloadJob job = new ReloadJob(7, TARGET, 5);
        assertEquals(7, job.jobId());
        assertEquals(TARGET, job.deliveryTarget());
    }
}
