package io.github.sweetzonzi.machine_max.network;

import io.github.sweetzonzi.machine_max.network.payload.assembly.PartAssemblyProgressSyncPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 普通部件进度同步的线格式契约：一个子零件 ID 和三个状态字段。 */
class PartAssemblyProgressSyncPayloadTest {
    /** 明确检查各字段及总长度，防止寻址字段重新膨胀为装配体和部件 UUID。 */
    @Test
    void encodesOneSubPartIdAndManufacturingStateInThirteenBytes() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var payload = new PartAssemblyProgressSyncPayload(0x12345678, 0.625f, 17, true);
            PartAssemblyProgressSyncPayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(13, buffer.readableBytes());
            assertEquals(0x12345678, buffer.readInt());
            assertEquals(0.625f, buffer.readFloat());
            assertEquals(17, buffer.readInt());
            assertTrue(buffer.readBoolean());
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    /** 从明确构造的连续线数据读取两条载荷，验证 ID 位宽和消息边界。 */
    @Test
    void decodesConsecutiveMessagesWithoutConsumingAdjacentFields() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeInt(Integer.MAX_VALUE).writeFloat(0.25f).writeInt(0).writeBoolean(false);
            buffer.writeInt(3).writeFloat(1f).writeInt(99).writeBoolean(true);
            assertEquals(new PartAssemblyProgressSyncPayload(Integer.MAX_VALUE, 0.25f, 0, false),
                    PartAssemblyProgressSyncPayload.STREAM_CODEC.decode(buffer));
            assertEquals(13, buffer.readableBytes());
            assertEquals(new PartAssemblyProgressSyncPayload(3, 1f, 99, true),
                    PartAssemblyProgressSyncPayload.STREAM_CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }
}
