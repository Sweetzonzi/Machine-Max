package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结束结果列表的契约：一轮可以结束不止一条作业，因此结果必须<b>按发生顺序</b>携带，而不是只留最后一条。
 *
 * <p>这些断言只依赖 {@link ReloadJobResults} 的值语义与线格式，不需要服务端或物理世界。</p>
 */
class ReloadJobResultsTest {

    private static ReloadJobResults roundTrip(ReloadJobResults value) {
        ByteBuf buf = Unpooled.buffer();
        try {
            ReloadJobResults.STREAM_CODEC.encode(buf, value);
            ReloadJobResults decoded = ReloadJobResults.STREAM_CODEC.decode(buf);
            assertEquals(0, buf.readableBytes(), "整包读完不应有剩余字节");
            return decoded;
        } finally {
            buf.release();
        }
    }

    /** 同一轮里连续结束的两条作业都必须被携带，且顺序与发生顺序一致。 */
    @Test
    void everyResultOfOneRoundSurvivesInOrder() {
        ReloadJobResults results = new ReloadJobResults(List.of(
                new ReloadJobResults.Entry(7, true),
                new ReloadJobResults.Entry(8, false),
                new ReloadJobResults.Entry(9, true)));

        ReloadJobResults decoded = roundTrip(results);

        assertEquals(results, decoded, "结果列表必须原样往返");
        assertEquals(List.of(7, 8, 9), decoded.entries().stream().map(ReloadJobResults.Entry::round).toList(),
                "顺序必须保持");
        assertEquals(9, decoded.lastRound(), "最近一条结果读列表末尾");
        assertTrue(decoded.lastDelivered(), "最近一条结果读列表末尾的结论");
    }

    /** 还没有结束过作业时列表为空，两个现场核对用的读口都退化为 0／false。 */
    @Test
    void emptyResultsDegradeToNoResult() {
        assertEquals(ReloadJobResults.EMPTY, roundTrip(ReloadJobResults.EMPTY));
        assertEquals(0, ReloadJobResults.EMPTY.lastRound());
        assertFalse(ReloadJobResults.EMPTY.lastDelivered());
        assertEquals(List.of(), ReloadJobResults.EMPTY.entries());
    }

    /** 中止与交付共用一条通道：结论只有"交付成了没有"这一位，顺序不因结论不同而变。 */
    @Test
    void abortedAndDeliveredResultsShareTheSameChannel() {
        ReloadJobResults results = new ReloadJobResults(List.of(
                new ReloadJobResults.Entry(4, false),
                new ReloadJobResults.Entry(5, true)));

        ReloadJobResults decoded = roundTrip(results);

        assertFalse(decoded.entries().getFirst().delivered(), "第一条是中止");
        assertTrue(decoded.entries().getLast().delivered(), "第二条是交付");
    }
}
