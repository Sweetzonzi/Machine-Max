package io.github.sweetzonzi.machine_max.track;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.MaterialAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.FittingType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.FittingVisual;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.TrackType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLinkData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackMemberRef;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 履带持久数据与型号属性的 JSON / 线格式往返；不依赖服务端，也不触碰物理。
 */
class TrackDataCodecTest {
    private static final ResourceLocation DEFAULT_TYPE = TrackLinkData.DEFAULT_TYPE;
    private static final ResourceLocation TRACK_TYPE = ResourceLocation.fromNamespaceAndPath("test", "wide_track");

    private static TrackLinkData link(TrackMemberRef sprocket, List<TrackMemberRef> members,
                                      float durability, boolean broken, boolean clockwise) {
        return new TrackLinkData(sprocket, members, durability, broken, clockwise, DEFAULT_TYPE);
    }

    @Test
    void trackLinkDataRoundTrips() {
        TrackMemberRef sprocket = new TrackMemberRef(UUID.randomUUID(), "drive_sprocket");
        TrackMemberRef road = new TrackMemberRef(UUID.randomUUID(), "road_1");
        TrackLinkData data = new TrackLinkData(sprocket,
                List.of(sprocket, road), 321.5f, true, false, TRACK_TYPE);

        var encoded = TrackLinkData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        TrackLinkData decoded = TrackLinkData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(data, decoded);
        assertEquals(TRACK_TYPE, decoded.trackType());
    }

    @Test
    void vehicleDataRoundTripsWithTrackLinks() {
        UUID partUuid = UUID.randomUUID();
        TrackMemberRef sprocket = new TrackMemberRef(partUuid, "sprocket");
        TrackLinkData link = link(sprocket, List.of(sprocket), 600f, false, true);
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
        TrackLinkData damaged = link(sprocket, List.of(sprocket), 12f, true, true);
        VehicleData data = new VehicleData("tracked", UUID.randomUUID().toString(),
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0.5f, Map.of(), List.of(), List.of(damaged),
                io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintMeta.EMPTY);

        VehicleData normalized = data.normalized();
        assertEquals(1, normalized.getTrackLinks().size());
        TrackLinkData link = normalized.getTrackLinks().getFirst();
        assertEquals(TrackLinkData.FULL_DURABILITY, link.durability(), 0f);
        assertFalse(link.broken());
        assertTrue(link.clockwise());
        assertEquals(DEFAULT_TYPE, link.trackType());
    }

    @Test
    void missingTrackDurabilityDefaultsToFullSentinel() {
        TrackMemberRef sprocket = new TrackMemberRef(UUID.randomUUID(), "sprocket");
        TrackLinkData data = link(sprocket, List.of(sprocket), 12f, false, false);

        var encoded = TrackLinkData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsJsonObject();
        encoded.remove("durability");
        TrackLinkData decoded = TrackLinkData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(TrackLinkData.FULL_DURABILITY, decoded.durability(), 0f);
    }

    @Test
    void missingTrackTypeFallsBackToDefaultModel() {
        TrackMemberRef sprocket = new TrackMemberRef(UUID.randomUUID(), "sprocket");
        var encoded = TrackLinkData.CODEC.encodeStart(JsonOps.INSTANCE, link(sprocket,
                List.of(sprocket), 12f, false, false)).getOrThrow().getAsJsonObject();
        encoded.remove("track_type");

        TrackLinkData decoded = TrackLinkData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(DEFAULT_TYPE, decoded.trackType());
    }

    @Test
    void trackAttrCodecUsesDocumentedDefaults() {
        registerMaterial("test:steel");
        var json = JsonParser.parseString("""
                { "material": "test:steel" }
                """);
        TrackAttr attr = TrackAttr.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(20f, attr.durabilityPerLink(), 1.0e-6f);
        assertEquals(0.2f, attr.lengthPerLink(), 1.0e-6f);
        assertEquals(50f, attr.thicknessMm(), 1.0e-6f);
        assertEquals(0.05f, attr.thicknessMeters(), 1.0e-6f);
        assertEquals(TrackAttr.Tension.DEFAULT, attr.tension());
        assertEquals(TrackAttr.TRACK_DEFAULT_SOUNDS, attr.sounds());
        assertEquals(20f, attr.maxDurability(1), 1.0e-6f);
        assertEquals(1200f, attr.maxDurability(60), 1.0e-6f);
    }

    @Test
    void trackAttrCodecRejectsMissingMaterial() {
        var json = JsonParser.parseString("""
                { "material": "test:missing_material" }
                """);
        assertThrows(RuntimeException.class,
                () -> TrackAttr.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void clockwiseRoundTripsThroughJsonAndWire() {
        TrackMemberRef sprocket = new TrackMemberRef(UUID.randomUUID(), "sprocket");
        for (boolean clockwise : new boolean[]{false, true}) {
            TrackLinkData data = new TrackLinkData(sprocket, List.of(sprocket), 123f, false, clockwise,
                    TRACK_TYPE);
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

    @Test
    void fittingTypeCodecDispatchesTrackJson() {
        registerMaterial("machine_max:track_steel");
        var json = JsonParser.parseString("""
                {
                  "type": "track",
                  "material": "machine_max:track_steel",
                  "thickness_mm": 50.0,
                  "durability_per_link": 20.0,
                  "length_per_link": 0.2,
                  "visual": {
                    "model": "machine_max:track_link.geo",
                    "texture": "machine_max:textures/part/track_link.png"
                  }
                }
                """);
        var type = FittingType.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertInstanceOf(TrackType.class, type);
        TrackType track = (TrackType) type;
        assertEquals("track", track.getSerializedName());
        assertEquals(0.2f, track.attr().lengthPerLink(), 1.0e-6f);
        assertEquals(ResourceLocation.parse("machine_max:track_link.geo"), track.visual().model());
        assertEquals(ResourceLocation.parse("machine_max:textures/part/track_link.png"),
                track.visual().texture());
        assertEquals(FittingVisual.NO_ICON, track.visual().icon());
        assertFalse(track.visual().hasIcon());
    }

    @Test
    void fittingVisualIconIsOptionalAndRoundTrips() {
        var withoutIcon = JsonParser.parseString("""
                {
                  "model": "machine_max:track_link.geo",
                  "texture": "machine_max:textures/part/track_link.png"
                }
                """);
        FittingVisual plain = FittingVisual.CODEC.parse(JsonOps.INSTANCE, withoutIcon).getOrThrow();
        assertEquals(FittingVisual.NO_ICON, plain.icon());
        assertFalse(plain.hasIcon());

        FittingVisual icon = new FittingVisual(
                ResourceLocation.parse("machine_max:track_link.geo"),
                ResourceLocation.parse("machine_max:textures/part/track_link.png"),
                ResourceLocation.parse("machine_max:textures/icon/track_link.png"));
        assertTrue(icon.hasIcon());
        var encoded = FittingVisual.CODEC.encodeStart(JsonOps.INSTANCE, icon).getOrThrow();
        assertEquals(icon, FittingVisual.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void fittingNameKeyUsesNamespaceAndFittingSegment() {
        assertEquals("machine_max.fitting.2s38_track",
                FittingType.nameKey(ResourceLocation.parse("machine_max:2s38_track")));
        assertEquals("machine_max.fitting.mtlb_track",
                FittingType.nameKey(ResourceLocation.parse("machine_max:mtlb_track")));
        assertEquals("machine_max.fitting.tracks.wide",
                FittingType.nameKey(ResourceLocation.parse("machine_max:tracks/wide")));
    }

    @Test
    void fittingTypeCodecRejectsUnknownKind() {
        var json = JsonParser.parseString("""
                { "type": "prop" }
                """);
        assertThrows(RuntimeException.class,
                () -> FittingType.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    private static void registerMaterial(String id) {
        MMDynamicRes.MATERIALS.putIfAbsent(ResourceLocation.parse(id), new MaterialAttr(
                MaterialAttr.PhysicsAttr.DEFAULT,
                MaterialAttr.ArmorAttr.DEFAULT,
                MaterialAttr.MaterialSoundAttr.DEFAULT
        ));
    }
}
