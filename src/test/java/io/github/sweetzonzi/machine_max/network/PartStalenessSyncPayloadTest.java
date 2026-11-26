package io.github.sweetzonzi.machine_max.network;

import io.github.sweetzonzi.machine_max.network.payload.assembly.PartStalenessSyncPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PartStalenessSyncPayloadTest {
    @Test
    void encodesSubPartAddressAndContinuousProgressInEightBytes() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var payload = new PartStalenessSyncPayload(0x12345678, 0.4375f);
            PartStalenessSyncPayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(8, buffer.readableBytes());
            assertEquals(payload, PartStalenessSyncPayload.STREAM_CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }
}
