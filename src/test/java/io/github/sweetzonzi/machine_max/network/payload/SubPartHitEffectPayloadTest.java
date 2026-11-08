package io.github.sweetzonzi.machine_max.network.payload;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 命中表现包的线格式契约。
 *
 * <p>{@link SubPartHitEffectPayload#MAX_ENTRIES} 是解码端的硬边界：解码端超限直接判畸形包，
 * 客户端随即断开连接。因此生产侧（{@code SubPart#collectHitEffect}）在收集时封顶，
 * 这里固定"上限本身可以通过、上限加一条必须失败"。</p>
 */
class SubPartHitEffectPayloadTest {

    private static SubPartHitEffectPayload.Entry entry(String hitBoxKey, boolean penetrated, float offset) {
        return new SubPartHitEffectPayload.Entry(hitBoxKey,
                offset, offset + 0.5f, offset - 0.5f,
                0f, 0f, -1f, penetrated, false);
    }

    /** 构造 n 条互不相同的条目，模拟一次霰弹或一次殉爆在同一子部件上累积的命中点。 */
    private static List<SubPartHitEffectPayload.Entry> entries(int count) {
        List<SubPartHitEffectPayload.Entry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(entry("HullPlate" + i, (i & 1) == 0, i));
        }
        return list;
    }

    /** 一轮编码解码后字段原样还原，击穿与履带吸收两个标志各自独立。 */
    @Test
    void roundTripsEveryEntryField() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SubPartHitEffectPayload payload = new SubPartHitEffectPayload(0x2A1,
                    List.of(entry("HullFrontUpperArmor", true, 1.25f),
                            new SubPartHitEffectPayload.Entry("TrackLink",
                                    -3f, 0f, 7.5f, 0f, 1f, 0f, false, true)));
            SubPartHitEffectPayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(payload, SubPartHitEffectPayload.STREAM_CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    /** 装满一整批的包仍能往返：上限是包含边界。 */
    @Test
    void roundTripsAFullBatchAtTheBound() {
        List<SubPartHitEffectPayload.Entry> full = entries(SubPartHitEffectPayload.MAX_ENTRIES);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SubPartHitEffectPayload.STREAM_CODEC.encode(buffer, new SubPartHitEffectPayload(7, full));
            assertEquals(full, SubPartHitEffectPayload.STREAM_CODEC.decode(buffer).entries());
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    /** 解码端的条目上限是硬边界：超一条即判畸形包，不静默截断。 */
    @Test
    void decoderRejectsCountAboveTheBound() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(7);
            buffer.writeVarInt(SubPartHitEffectPayload.MAX_ENTRIES + 1);
            assertThrows(DecoderException.class,
                    () -> SubPartHitEffectPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
