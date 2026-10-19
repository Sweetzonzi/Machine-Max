package io.github.sweetzonzi.machine_max.track;

import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLinkData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackMemberRef;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 履带持久数据的 JSON 往返；不依赖服务端，也不触碰物理。
 */
class TrackDataCodecTest {
    @Test
    void trackLinkDataRoundTrips() {
        TrackMemberRef sprocket = new TrackMemberRef(UUID.randomUUID(), "drive_sprocket");
        TrackMemberRef road = new TrackMemberRef(UUID.randomUUID(), "road_1");
        TrackLinkData data = new TrackLinkData(sprocket,
                List.of(sprocket, road), 321.5f, true, false);

        var encoded = TrackLinkData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        TrackLinkData decoded = TrackLinkData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(data, decoded);
    }

    @Test
    void vehicleDataRoundTripsWithTrackLinks() {
        UUID partUuid = UUID.randomUUID();
        TrackMemberRef sprocket = new TrackMemberRef(partUuid, "sprocket");
        TrackLinkData link = new TrackLinkData(sprocket,
                List.of(sprocket), 600f, false, true);
        VehicleData data = new VehicleData("tracked", UUID.randomUUID().toString(),
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1f, Map.of(), List.of(), List.of(link),
                io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintMeta.EMPTY);

        var encoded = VehicleData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        VehicleData decoded = VehicleData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(1, decoded.getTrackLinks().size());
        assertEquals(link, decoded.getTrackLinks().getFirst());

    }

    @Test
    void blueprintNormalizationResetsTrackDurabilityAndBrokenFlag() {
        UUID partUuid = UUID.randomUUID();
        TrackMemberRef sprocket = new TrackMemberRef(partUuid, "sprocket");
        TrackLinkData damaged = new TrackLinkData(sprocket,
                List.of(sprocket), 12f, true, true);
        VehicleData data = new VehicleData("tracked", UUID.randomUUID().toString(),
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0.5f, Map.of(), List.of(), List.of(damaged),
                io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintMeta.EMPTY);

        VehicleData normalized = data.normalized();
        assertEquals(1, normalized.getTrackLinks().size());
        TrackLinkData link = normalized.getTrackLinks().getFirst();
        assertEquals(TrackLinkData.FULL_DURABILITY, link.durability(), 0f);
        assertTrue(!link.broken());
        assertTrue(link.clockwise());
    }

    @Test
    void missingTrackDurabilityDefaultsToFullSentinel() {
        TrackMemberRef sprocket = new TrackMemberRef(UUID.randomUUID(), "sprocket");
        TrackLinkData data = new TrackLinkData(sprocket, List.of(sprocket), 12f, false, false);

        var encoded = TrackLinkData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsJsonObject();
        encoded.remove("durability");
        TrackLinkData decoded = TrackLinkData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(TrackLinkData.FULL_DURABILITY, decoded.durability(), 0f);
    }

    @Test
    void trackAttrDerivesMaxDurabilityFromLinkCount() {
        assertEquals(20f, TrackAttr.DEFAULT.maxDurability(1), 1.0e-6f);
        assertEquals(1200f, TrackAttr.DEFAULT.maxDurability(60), 1.0e-6f);
    }

    @Test
    void clockwiseRoundTripsThroughJsonAndWire() {
        TrackMemberRef sprocket = new TrackMemberRef(UUID.randomUUID(), "sprocket");
        for (boolean clockwise : new boolean[]{false, true}) {
            TrackLinkData data = new TrackLinkData(sprocket, List.of(sprocket), 123f, false, clockwise);
            var json = TrackLinkData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
            assertEquals(data, TrackLinkData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                TrackLinkData.STREAM_CODEC.encode(buffer, data);
                assertEquals(data, TrackLinkData.STREAM_CODEC.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }
}
