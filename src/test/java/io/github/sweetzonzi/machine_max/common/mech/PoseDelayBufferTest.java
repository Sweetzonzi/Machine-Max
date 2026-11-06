package io.github.sweetzonzi.machine_max.common.mech;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PoseDelayBuffer} 的播放规则测试。
 *
 * <p>这一段是纯逻辑，但它决定了画面抖不抖：错了的表现是「周期性冻结 + 下一帧速度翻倍」，
 * 与网络同步本身出错的表现（漏包、跳变、延迟漂移）几乎一样，肉眼在游戏里区分不出来。
 * 因此这里把三条不变量逐条钉住：样对永远是相邻样本、余量耗尽时不倒退、积压时延迟不漂移。</p>
 *
 * @author Sweetzonzi
 */
class PoseDelayBufferTest {

    private static final float EPS = 1e-5f;

    private static final Quaternion IDENTITY = new Quaternion();

    /** 稳态余量取 1（产品取值，见 {@code DestroyableObject#PRESENTATION_SPARE}） */
    private static final int SPARE = 1;

    private static PoseDelayBuffer newBuffer() {
        return new PoseDelayBuffer(SPARE, PoseDelayBuffer.DEFAULT_SNAP_DISTANCE);
    }

    /** 沿 +X 摆一个样本，位移即样本序号，便于断言 */
    private static Vector3f at(float x) {
        return new Vector3f(x, 0f, 0f);
    }

    private static void assertPair(PoseDelayBuffer buffer, float left, float right) {
        assertEquals(left, buffer.left().getTranslation().x, EPS, "左端");
        assertEquals(right, buffer.right().getTranslation().x, EPS, "右端");
    }

    @Test
    void singleSampleHoldsAndNeverMoves() {
        PoseDelayBuffer buffer = newBuffer();
        assertFalse(buffer.hasSample());

        buffer.push(at(7f), IDENTITY);
        assertTrue(buffer.hasSample());
        for (int i = 0; i < 5; i++) {
            assertFalse(buffer.advance(), "样本不足时不应开始插值");
            assertPair(buffer, 7f, 7f);
        }
    }

    @Test
    void startsInterpolatingOnlyAfterTheReserveIsFilled() {
        PoseDelayBuffer buffer = newBuffer();
        buffer.push(at(0f), IDENTITY);
        buffer.advance();
        assertPair(buffer, 0f, 0f);
        buffer.push(at(1f), IDENTITY);
        buffer.advance();
        // spare + 1 = 2 份样本才够建立余量：此刻 newest = 1，还差一份
        assertPair(buffer, 1f, 1f);

        buffer.push(at(2f), IDENTITY);
        buffer.advance();
        // 建立之后显示落在 (0, 1)，落后最新样本(2)两 tick
        assertPair(buffer, 0f, 1f);
        assertTrue(buffer.isInterpolating());
        assertEquals(1L, buffer.reserve());
    }

    @Test
    void oneEmptyTickStillAdvancesOneSample() {
        PoseDelayBuffer buffer = newBuffer();
        buffer.push(at(0f), IDENTITY);
        buffer.advance();
        buffer.push(at(1f), IDENTITY);
        buffer.advance();
        buffer.push(at(2f), IDENTITY);
        buffer.advance();
        assertPair(buffer, 0f, 1f);

        // 这个 tick 一个包都没有：余量被吃掉，显示仍旧前进一个样本（不冻结）
        assertTrue(buffer.advance(), "有 1 份余量时应当照常前进");
        assertPair(buffer, 1f, 2f);

        // 下一个 tick 补上一个包，仍是「每 tick 一个样本」的速度
        buffer.push(at(3f), IDENTITY);
        buffer.advance();
        assertPair(buffer, 2f, 3f);
        // 那一份余量已经被吃掉，本次没有停帧；余量由后续的补包 tick 慢慢还回来
        assertEquals(0L, buffer.starvedTicks());
    }

    @Test
    void twoEmptyTicksInARowFreezeWithoutRewinding() {
        PoseDelayBuffer buffer = newBuffer();
        buffer.push(at(0f), IDENTITY);
        buffer.advance();
        buffer.push(at(1f), IDENTITY);
        buffer.advance();
        buffer.push(at(2f), IDENTITY);
        buffer.advance();
        buffer.push(at(3f), IDENTITY);
        buffer.advance();
        assertPair(buffer, 1f, 2f);

        // 余量只有 1：这一 tick 用掉，显示继续走
        buffer.advance();
        assertPair(buffer, 2f, 3f);

        // 余量已耗尽：停住。关键是左端等于上一 tick 的右端（3），不倒退也不跳
        assertFalse(buffer.advance());
        assertPair(buffer, 3f, 3f);
        assertEquals(1L, buffer.starvedTicks());

        // 恢复后从停住的位置继续：不跳跃
        buffer.push(at(4f), IDENTITY);
        buffer.advance();
        assertPair(buffer, 3f, 4f);
    }

    @Test
    void burstOfTwoPacketsDoesNotDoubleTheSpeed() {
        PoseDelayBuffer buffer = newBuffer();
        buffer.push(at(0f), IDENTITY);
        buffer.advance();
        buffer.push(at(1f), IDENTITY);
        buffer.advance();
        buffer.push(at(2f), IDENTITY);
        buffer.advance();
        assertPair(buffer, 0f, 1f);

        // 一个 tick 里挤进两个包：游标只前进一个样本，多出来的一份进余量
        buffer.push(at(3f), IDENTITY);
        buffer.push(at(4f), IDENTITY);
        buffer.advance();
        assertPair(buffer, 1f, 2f);
        assertEquals(2L, buffer.reserve());

        // 下一个 tick：仍只前进一个样本，速度不翻倍
        buffer.push(at(5f), IDENTITY);
        buffer.advance();
        assertPair(buffer, 2f, 3f);
    }

    @Test
    void sustainedBacklogDropsSamplesInsteadOfGrowingLatency() {
        PoseDelayBuffer buffer = newBuffer();
        buffer.push(at(0f), IDENTITY);
        buffer.advance();
        buffer.push(at(1f), IDENTITY);
        buffer.advance();
        buffer.push(at(2f), IDENTITY);
        buffer.advance();
        assertPair(buffer, 0f, 1f);

        // 客户端卡了一段：一次进来 8 个样本，余量远超 spare + SKIP_SLACK
        for (int i = 3; i <= 10; i++) {
            buffer.push(at(i), IDENTITY);
        }
        buffer.advance();

        // 延迟被限回 spare：呈现区间右端距离最新样本(10)不超过 spare + 1
        assertEquals(SPARE, buffer.reserve());
        assertEquals(10f - SPARE, buffer.right().getTranslation().x, EPS);
        assertTrue(buffer.skippedSamples() > 0L, "积压应当被丢弃而不是无限延迟");
    }

    @Test
    void teleportResetsTheHistoryAndHoldsAtTheNewPose() {
        PoseDelayBuffer buffer = newBuffer();
        buffer.push(at(0f), IDENTITY);
        buffer.advance();
        buffer.push(at(1f), IDENTITY);
        buffer.advance();
        buffer.push(at(2f), IDENTITY);
        buffer.advance();
        assertPair(buffer, 0f, 1f);

        // 位移远超判定阈值：判为传送，历史作废
        buffer.push(at(500f), IDENTITY);
        assertEquals(500f, buffer.right().getTranslation().x, EPS);
        assertEquals(500f, buffer.left().getTranslation().x, EPS);
        assertEquals(0L, buffer.newestIndex());

        // 传送后按「重新攒余量」处理：不足 spare + 1 份样本时停在新的位姿上
        buffer.advance();
        assertPair(buffer, 500f, 500f);
        buffer.push(at(501f), IDENTITY);
        buffer.advance();
        assertPair(buffer, 501f, 501f);
        buffer.push(at(502f), IDENTITY);
        buffer.advance();
        assertPair(buffer, 500f, 501f);
    }

    @Test
    void samplesAreCopiedNotShared() {
        PoseDelayBuffer buffer = newBuffer();
        Vector3f position = at(3f);
        Quaternion rotation = new Quaternion();
        buffer.push(position, rotation);

        // 调用方复用自己的缓冲不该影响已入队的样本
        position.set(99f, 99f, 99f);
        rotation.set(0f, 1f, 0f, 0f);
        assertEquals(3f, buffer.right().getTranslation().x, EPS);
        assertEquals(0f, buffer.right().getRotation().getY(), EPS);
    }

    @Test
    void spareZeroHoldsOnEveryEmptyTick() {
        PoseDelayBuffer buffer = new PoseDelayBuffer(0, PoseDelayBuffer.DEFAULT_SNAP_DISTANCE);
        buffer.push(at(0f), IDENTITY);
        buffer.advance();
        buffer.push(at(1f), IDENTITY);
        buffer.advance();
        // spare = 0：一份空白 tick 就停住（这是「关闭缓冲」的那一档）
        buffer.advance();
        assertPair(buffer, 1f, 1f);
        assertEquals(1L, buffer.starvedTicks());
    }
}
