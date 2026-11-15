package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.SupplierRef;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReloadJobSync} 的契约：它是这条作业在两端之间唯一的一份载体，因此
 * 一、线格式必须原样往返（字段顺序、阶段编码与时长 0）；
 * 二、快照必须与作业脱钩（规格是副本，不是正在被消耗的那个实例）；
 * 三、按快照建出的本地作业与原作业同名同分配，进度落在基线上。
 */
class ReloadJobSyncTest {

    private static final SupplierRef TARGET = new SupplierRef(4, "m1a2_sep.ammo_autoloader");
    private static final SupplierRef ORIGIN = new SupplierRef(5, "m1a2_sep.ammo_loader");
    private static final ReloadJobKey KEY = new ReloadJobKey(3, "m1a2_sep.launcher", 7);

    private static AmmoBelt spec(int remaining) {
        return new AmmoBelt(null, "test_belt",
                List.of(new AmmoBelt.PatternEntry(ResourceLocation.parse("machine_max:test_round"), 5)),
                5, remaining, null);
    }

    private static ReloadJobSync roundTrip(ReloadJobSync value) {
        ByteBuf buf = Unpooled.buffer();
        try {
            ReloadJobSync.STREAM_CODEC.encode(buf, value);
            return ReloadJobSync.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }

    @Test
    void wireFormatKeepsEveryFieldInOrder() {
        ReloadJobSync value = new ReloadJobSync(
                7, TARGET, ORIGIN, spec(5), 4, 12, 40, ReloadJob.Stage.MACHINE);
        assertEquals(value, roundTrip(value));
    }

    @Test
    void zeroLengthStagesAndEmptyBaselinesSurviveTheWire() {
        ReloadJobSync value = new ReloadJobSync(
                1, TARGET, ORIGIN, spec(1), 1, 0, 0, ReloadJob.Stage.HUMAN);
        ReloadJobSync decoded = roundTrip(value);
        assertEquals(0, decoded.total(), "0 时长在线格式里是原值，不是缺失");
        assertEquals(0, decoded.elapsed());
        assertEquals(ReloadJob.Stage.HUMAN, decoded.stage());
        assertEquals(1, decoded.jobId());
    }

    @Test
    void theSnapshotCopiesTheSpecInsteadOfSharingTheConsumedInstance() {
        ReloadJob job = new ReloadJob(KEY, TARGET, ORIGIN, spec(5), 5, 40);
        ReloadJobSync snapshot = ReloadJobSync.of(job);
        assertNotSame(job.spec(), snapshot.spec(), "线上传输的必须是副本，否则两端会共享一个可变的弹链");
        assertEquals(5, snapshot.spec().length());
        assertEquals(job.jobId(), snapshot.jobId());
        assertEquals(job.reservedCount(), snapshot.reserved());
        assertEquals(job.totalTicks(), snapshot.total());
    }

    @Test
    void withElapsedOnlyMovesTheProgressCoordinate() {
        ReloadJobSync snapshot = new ReloadJobSync(
                7, TARGET, ORIGIN, spec(5), 4, 0, 40, ReloadJob.Stage.MACHINE);
        ReloadJobSync moved = snapshot.withElapsed(13);
        assertEquals(13, moved.elapsed());
        assertSame(snapshot.spec(), moved.spec(), "只换基线时不重新复制规格");
        assertEquals(snapshot.jobId(), moved.jobId());
        assertEquals(snapshot.total(), moved.total());
        assertEquals(snapshot.stage(), moved.stage());
    }

    @Test
    void aSnapshotRebuildsAJobOnTheSameBaseline() {
        ReloadJobSync snapshot = new ReloadJobSync(
                7, TARGET, ORIGIN, spec(5), 4, 10, 40, ReloadJob.Stage.MACHINE);
        ReloadJob job = snapshot.toLocalJob(KEY);
        assertEquals(KEY, job.key());
        assertEquals(7, job.jobId());
        assertEquals(TARGET, job.deliveryTarget());
        assertEquals(ORIGIN, job.upstreamOrigin());
        assertEquals(4, job.reservedCount());
        assertEquals(40, job.totalTicks());
        assertEquals(10, job.elapsedTicks(), "进度落在权威基线上");
        assertEquals(ReloadJob.Stage.MACHINE, job.stage());
        assertTrue(job.progress() > 0f);
    }
}
