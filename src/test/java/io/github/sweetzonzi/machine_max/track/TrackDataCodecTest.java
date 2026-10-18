package io.github.sweetzonzi.machine_max.track;

import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLinkData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackMemberRef;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

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
                List.of(sprocket, road), 321.5f, true);

        var encoded = TrackLinkData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        TrackLinkData decoded = TrackLinkData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(data, decoded);
    }

    @Test
    void legacyTrackNameFieldIsIgnored() {
        TrackMemberRef sprocket = new TrackMemberRef(UUID.randomUUID(), "sprocket");
        TrackLinkData data = new TrackLinkData(sprocket, List.of(sprocket), 100f, false);

        var encoded = TrackLinkData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsJsonObject();
        encoded.addProperty("name", "legacy_track");
        TrackLinkData decoded = TrackLinkData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(data, decoded);
    }

    @Test
    void vehicleDataRoundTripsWithTrackLinksAndKeepsBackwardCompatibility() {
        UUID partUuid = UUID.randomUUID();
        TrackMemberRef sprocket = new TrackMemberRef(partUuid, "sprocket");
        TrackLinkData link = new TrackLinkData(sprocket,
                List.of(sprocket), 600f, false);
        VehicleData data = new VehicleData("tracked", UUID.randomUUID().toString(),
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1f, Map.of(), List.of(), List.of(link),
                io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintMeta.EMPTY);

        var encoded = VehicleData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        VehicleData decoded = VehicleData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(1, decoded.getTrackLinks().size());
        assertEquals(link, decoded.getTrackLinks().getFirst());

        // 旧存档没有 track_links 字段时应解析为空列表
        var legacy = encoded.getAsJsonObject();
        legacy.remove("track_links");
        VehicleData legacyDecoded = VehicleData.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertTrue(legacyDecoded.getTrackLinks().isEmpty());
    }

    @Test
    void blueprintNormalizationResetsTrackDurabilityAndBrokenFlag() {
        UUID partUuid = UUID.randomUUID();
        TrackMemberRef sprocket = new TrackMemberRef(partUuid, "sprocket");
        TrackLinkData damaged = new TrackLinkData(sprocket,
                List.of(sprocket), 12f, true);
        VehicleData data = new VehicleData("tracked", UUID.randomUUID().toString(),
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0.5f, Map.of(), List.of(), List.of(damaged),
                io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintMeta.EMPTY);

        VehicleData normalized = data.normalized();
        assertEquals(1, normalized.getTrackLinks().size());
        TrackLinkData link = normalized.getTrackLinks().getFirst();
        assertEquals(TrackAttr.DEFAULT.durability(), link.durability(), 1.0e-6f);
        assertTrue(!link.broken());
    }
}
