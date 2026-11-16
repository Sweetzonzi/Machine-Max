package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
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
 * 一条作业的两支同步样本：{@link ReloadJobSpec}（冻结分配）与 {@link ReloadJobProgress}（动态样本）。
 *
 * <p>两者的契约分别是：线格式必须原样往返；规格必须与作业脱钩（是副本，不是正在被消耗的那个实例）；
 * 换基线只动进度坐标；两支样本能拼回一条与原作业同轮次、同分配、同基线的本地作业。</p>
 */
class ReloadJobSampleTest {

    private static final AmmoRef TRANSPORTER = new AmmoRef(4, 3);
    private static final AmmoRef PROVIDER = new AmmoRef(5, 1);
    private static final ReloadJobKey KEY = new ReloadJobKey(3, 2, TRANSPORTER, PROVIDER);

    private static AmmoBelt spec(int remaining) {
        return new AmmoBelt(null, "test_belt",
                List.of(new AmmoBelt.PatternEntry(ResourceLocation.parse("machine_max:test_round"), 5)),
                5, remaining, null);
    }

    private static ReloadJobSpec newSpec() {
        return new ReloadJobSpec(7, TRANSPORTER, PROVIDER, spec(5), 4);
    }

    private static ReloadJobProgress newProgress() {
        return new ReloadJobProgress(7, 12, 40, ReloadJob.Stage.MACHINE);
    }

    private static <T> T roundTrip(net.minecraft.network.codec.StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buf = Unpooled.buffer();
        try {
            codec.encode(buf, value);
            T decoded = codec.decode(buf);
            assertEquals(0, buf.readableBytes(), "整包读完不应有剩余字节");
            return decoded;
        } finally {
            buf.release();
        }
    }

    @Test
    void allocationKeepsEveryFieldInOrder() {
        assertEquals(newSpec(), roundTrip(ReloadJobSpec.STREAM_CODEC, newSpec()));
    }

    @Test
    void progressKeepsZeroLengthAndStage() {
        ReloadJobProgress zeroLength = new ReloadJobProgress(1, 0, 0, ReloadJob.Stage.HUMAN);
        ReloadJobProgress decoded = roundTrip(ReloadJobProgress.STREAM_CODEC, zeroLength);
        assertEquals(0, decoded.total(), "0 时长在线格式里是原值，不是缺失");
        assertEquals(0, decoded.elapsed());
        assertEquals(ReloadJob.Stage.HUMAN, decoded.stage());
        assertEquals(1, decoded.round());
        assertEquals(newProgress(), roundTrip(ReloadJobProgress.STREAM_CODEC, newProgress()));
    }

    @Test
    void theAllocationCopiesTheSpecInsteadOfSharingTheConsumedInstance() {
        ReloadJob job = new ReloadJob(KEY, 7, spec(5), 5, 40);
        ReloadJobSpec allocation = ReloadJobSpec.of(job);
        assertNotSame(job.spec(), allocation.spec(), "线上传输的必须是副本，否则两端会共享一个可变的弹链");
        assertEquals(5, allocation.spec().length());
        assertEquals(job.round(), allocation.round());
        assertEquals(job.reservedCount(), allocation.reserved());
    }

    @Test
    void withElapsedOnlyMovesTheProgressCoordinate() {
        ReloadJobProgress progress = new ReloadJobProgress(7, 0, 40, ReloadJob.Stage.MACHINE);
        ReloadJobProgress moved = progress.withElapsed(13);
        assertEquals(13, moved.elapsed());
        assertEquals(progress.round(), moved.round(), "换基线不改轮次");
        assertEquals(progress.total(), moved.total(), "换基线不改本阶段时长");
        assertEquals(progress.stage(), moved.stage(), "换基线不改阶段");
        assertSame(ReloadJob.Stage.MACHINE, moved.stage());
    }

    @Test
    void bothSamplesRebuildAJobOnTheSameBaseline() {
        ReloadJobSpec allocation = newSpec();
        ReloadJobProgress progress = new ReloadJobProgress(7, 10, 40, ReloadJob.Stage.MACHINE);
        ReloadJob job = allocation.toLocalJob(KEY, progress);
        assertEquals(KEY, job.key());
        assertEquals(7, job.round());
        assertEquals(TRANSPORTER, job.transporter());
        assertEquals(PROVIDER, job.provider());
        assertEquals(4, job.reservedCount());
        assertEquals(40, job.totalTicks());
        assertEquals(10, job.elapsedTicks(), "进度落在权威基线上");
        assertEquals(ReloadJob.Stage.MACHINE, job.stage());
        assertTrue(job.progress() > 0f);
        assertTrue(job.allocationMatches(allocation), "重建出来的作业与分配判据自洽");
    }
}
