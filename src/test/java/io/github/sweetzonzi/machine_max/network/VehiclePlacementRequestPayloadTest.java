package io.github.sweetzonzi.machine_max.network;

import io.github.sweetzonzi.machine_max.network.payload.assembly.VehiclePlacementRequestPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 载具放置请求的线格式契约。 */
class VehiclePlacementRequestPayloadTest {
    @Test
    void roundTripsTemplateAndGroundAnchor() {
        roundTrip(new VehiclePlacementRequestPayload(InteractionHand.OFF_HAND,
                ResourceLocation.fromNamespaceAndPath("machine_max", "vehicles/tank"),
                new Vec3(12.25, 64.0, -8.5), 270f));
    }

    @Test
    void roundTripsInlineDataWithoutTemplateId() {
        roundTrip(new VehiclePlacementRequestPayload(InteractionHand.MAIN_HAND, null,
                new Vec3(-3.0, 70.5, 9.125), 90f));
    }

    private static void roundTrip(VehiclePlacementRequestPayload payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            VehiclePlacementRequestPayload.STREAM_CODEC.encode(buffer, payload);
            VehiclePlacementRequestPayload decoded = VehiclePlacementRequestPayload.STREAM_CODEC.decode(buffer);
            assertEquals(payload, decoded);
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }
}
