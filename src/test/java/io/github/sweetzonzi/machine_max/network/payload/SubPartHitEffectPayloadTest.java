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
                0f, 0f, -1f, penetrated, false, 0);
    }

    /** 构造 n 条互不相同的条目，模拟一次霰弹或一次殉爆在同一子部件上累积的命中点。 */
    private static List<SubPartHitEffectPayload.Entry> entries(int count) {
        List<SubPartHitEffectPayload.Entry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(entry("HullPlate" + i, (i & 1) == 0, i));
        }
        return list;
    }

    /**
     * 一轮编码解码后字段原样还原，击穿、履带吸收与投射物身份三者各自独立。
     *
     * <p>投射物 id 只在击穿那一条上出现，正是为了固定"标志位与后随字段成对"：漏写后随的
     * {@code writeVarInt} 会让后面的条目整体错位，而这种错位在单条条目上看不出来。</p>
     */
    @Test
    void roundTripsEveryEntryField() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SubPartHitEffectPayload payload = new SubPartHitEffectPayload(0x2A1,
                    List.of(new SubPartHitEffectPayload.Entry("HullFrontUpperArmor",
                                    1.25f, 1.75f, 0.75f, 0f, 0f, -1f, true, false, 0x5EC7),
                            new SubPartHitEffectPayload.Entry("TrackLink",
                                    -3f, 0f, 7.5f, 0f, 1f, 0f, false, true, 0),
                            // 击穿但没有投射物身份：没有可播的弹种命中音，客户端按材质取音
                            entry("HullSide", true, 4f)));
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
