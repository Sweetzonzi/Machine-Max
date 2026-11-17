package io.github.sweetzonzi.machine_max.track;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.MaterialAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintMeta;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.FittingType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.FittingVisual;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.TrackType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLink;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLinkData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackMemberRef;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackSectors;
import io.github.sweetzonzi.machine_max.common.registry.MMTags;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 履带持久数据与型号属性的 JSON / 线格式往返，以及材料扇区的纯函数契约；不依赖服务端，也不触碰物理。
 */
class TrackDataCodecTest {
    private static final ResourceLocation DEFAULT_TYPE = TrackLinkData.DEFAULT_TYPE;
    private static final ResourceLocation TRACK_TYPE = ResourceLocation.fromNamespaceAndPath("test", "wide_track");
    private static final float EPS = 1.0e-4f;

    /** M1A2 的口径：N = 80 节、9 个成员轮，每节 12 点。 */
    private static final int M1A2_LINKS = 80;
    private static final int M1A2_MEMBERS = 9;
    private static final float M1A2_PER_LINK = 12f;

    private static List<TrackMemberRef> members(int count) {
        List<TrackMemberRef> refs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) refs.add(new TrackMemberRef(UUID.randomUUID(), "wheel_" + i));
        return refs;
    }

    private static TrackLinkData built(List<TrackMemberRef> members, int linkCount) {
        TrackSectors sectors = TrackSectors.full(members.size());
        return new TrackLinkData(members.getFirst(), members, sectors.fill(), sectors.torn(),
                0f, true, DEFAULT_TYPE, linkCount);
    }

    private static JsonObject encode(TrackLinkData data) {
        return TrackLinkData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsJsonObject();
    }

    // ==================== 编解码 ====================

    @Test
    void trackLinkDataRoundTrips() {
        List<TrackMemberRef> members = members(3);
        List<Float> fill = List.of(1f, 0.25f, 0f);
        List<Boolean> torn = List.of(false, false, true);
        TrackLinkData data = new TrackLinkData(members.getFirst(), members, fill, torn, 1.5f, false,
                TRACK_TYPE, 82);

        TrackLinkData decoded = TrackLinkData.CODEC.parse(JsonOps.INSTANCE, encode(data)).getOrThrow();
        assertEquals(data, decoded);
        assertEquals(TRACK_TYPE, decoded.trackType());
        assertEquals(82, decoded.linkCount());
        assertEquals(fill, decoded.sectionFill());
        assertEquals(torn, decoded.torn());
        assertEquals(1.5f, decoded.sectorPhase(), EPS);
    }

    @Test
    void vehicleDataRoundTripsWithTrackLinks() {
        TrackMemberRef sprocket = new TrackMemberRef(UUID.randomUUID(), "sprocket");
        TrackLinkData link = built(List.of(sprocket), 41);
        VehicleData data = new VehicleData("tracked", UUID.randomUUID(),
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1f, Map.of(), List.of(), List.of(link),
                BlueprintMeta.EMPTY);

        VehicleData decoded = VehicleData.CODEC.parse(JsonOps.INSTANCE,
                VehicleData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
        assertEquals(1, decoded.getTrackLinks().size());
        assertEquals(link, decoded.getTrackLinks().getFirst());
    }

    @Test
    void missingSectionFillMeansFullBelt() {
        List<TrackMemberRef> members = members(4);
        JsonObject json = encode(built(members, 41));
        json.remove("section_fill");
        json.remove("torn");

        TrackLinkData decoded = TrackLinkData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        // S = 成员数，全部满材料、无断口：只写了 link_count 的模板必须解出满载
        assertEquals(4, decoded.sectionFill().size());
        assertTrue(decoded.isBuilt());
        assertFalse(decoded.isBroken());
        assertEquals(4, decoded.torn().size());
        for (Float ratio : decoded.sectionFill()) assertEquals(1f, ratio, 0f);
        assertEquals(41, decoded.links());
    }

    @Test
    void emptySectionFillMeansUnbuilt() {
        List<TrackMemberRef> members = members(4);
        TrackLinkData plan = new TrackLinkData(members.getFirst(), members,
                List.of(), List.of(), 0f, true, DEFAULT_TYPE, 41);
        TrackLinkData decoded = TrackLinkData.CODEC.parse(JsonOps.INSTANCE, encode(plan)).getOrThrow();

        assertFalse(decoded.isBuilt());
        assertEquals(0, decoded.links());
        assertEquals(List.of(), decoded.sectionFill());
        // 未建造也必须把 [] 写出来，不能靠省略字段表达
        assertTrue(encode(plan).has("section_fill"));
        assertTrue(encode(plan).getAsJsonArray("section_fill").isEmpty());
    }

    @Test
    void sectionFillRatiosAreClampedAtDecode() {
        List<TrackMemberRef> members = members(2);
        JsonObject json = encode(built(members, 20));
        var array = new com.google.gson.JsonArray();
        array.add(3.5);
        array.add(-2.0);
        json.add("section_fill", array);

        TrackLinkData decoded = TrackLinkData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(1f, decoded.sectionFill().get(0), 0f);
        assertEquals(0f, decoded.sectionFill().get(1), 0f);
    }

    @Test
    void tornFlagsAreResampledToSectionFillLength() {
        List<TrackMemberRef> members = members(4);
        JsonObject json = encode(built(members, 40));
        var torn = new com.google.gson.JsonArray();
        torn.add(true);
        json.add("torn", torn);

        TrackLinkData decoded = TrackLinkData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(4, decoded.torn().size());
        // 一条覆盖整圈的断口重采样到 4 段时四段都算断口：战损不凭空消失
        assertTrue(decoded.torn().get(0));
        assertTrue(decoded.torn().get(3));
    }

    @Test
    void sectorPhaseIsWrappedIntoRange() {
        List<TrackMemberRef> members = members(4);
        JsonObject json = encode(built(members, 40));
        json.addProperty("sector_phase", 5.5f);

        TrackLinkData decoded = TrackLinkData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(1.5f, decoded.sectorPhase(), EPS);
    }

    @Test
    void missingLinkCountMeansLegacyData() {
        TrackMemberRef sprocket = new TrackMemberRef(UUID.randomUUID(), "sprocket");
        JsonObject json = encode(built(List.of(sprocket), 41));
        json.remove("link_count");

        TrackLinkData decoded = TrackLinkData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(TrackLinkData.UNKNOWN_LINK_COUNT, decoded.linkCount());
    }

    @Test
    void linkCountIsRejectedAboveTheSafetyBound() {
        TrackMemberRef sprocket = new TrackMemberRef(UUID.randomUUID(), "sprocket");
        JsonObject json = encode(built(List.of(sprocket), 41));
        json.addProperty("link_count", TrackLinkData.MAX_LINK_COUNT + 1);

        assertTrue(TrackLinkData.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void missingTrackTypeFallsBackToDefaultModel() {
        TrackMemberRef sprocket = new TrackMemberRef(UUID.randomUUID(), "sprocket");
        JsonObject json = encode(built(List.of(sprocket), 12));
        json.remove("track_type");

        TrackLinkData decoded = TrackLinkData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(DEFAULT_TYPE, decoded.trackType());
    }

    @Test
    void clockwiseRoundTripsThroughJsonAndWire() {
        List<TrackMemberRef> members = members(3);
        for (boolean clockwise : new boolean[]{false, true}) {
            TrackLinkData data = new TrackLinkData(members.getFirst(), members,
                    List.of(1f, 0.5f, 0f), List.of(false, false, true), 2f, clockwise, TRACK_TYPE, 41);
            assertEquals(data, TrackLinkData.CODEC.parse(JsonOps.INSTANCE, encode(data)).getOrThrow());
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

    // ==================== 方案 / 成品 ====================

    @Test
    void blueprintNormalizationEmptiesTrackMaterial() {
        List<TrackMemberRef> members = members(3);
        TrackLinkData damaged = new TrackLinkData(members.getFirst(), members,
                List.of(1f, 0f, 0.4f), List.of(false, true, false), 2f, true, DEFAULT_TYPE, 55);
        VehicleData data = new VehicleData("tracked", UUID.randomUUID(),
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0.5f, Map.of(), List.of(), List.of(damaged),
                BlueprintMeta.EMPTY);

        VehicleData normalized = data.normalized();
        assertEquals(1, normalized.getTrackLinks().size());
        TrackLinkData link = normalized.getTrackLinks().getFirst();
        assertEquals(List.of(), link.sectionFill());
        assertEquals(List.of(), link.torn());
        assertEquals(0f, link.sectorPhase(), 0f);
        assertFalse(link.isBuilt());
        assertEquals(0, link.links());
        assertTrue(link.clockwise());
        assertEquals(DEFAULT_TYPE, link.trackType());
        // 节数与几何无关：蓝图必须原样带回，放出时才是同样的 N 节
        assertEquals(55, link.linkCount());
    }

    @Test
    void sameTemplateDecodesFullButPlansToZeroLinks() {
        // 同一份模板：FULL_STATE 按解码结果原样恢复（满载），STRUCTURE_ONLY 一律走 asPlan
        List<TrackMemberRef> members = members(6);
        JsonObject template = encode(built(members, 90));
        template.remove("section_fill");
        template.remove("torn");

        TrackLinkData full = TrackLinkData.CODEC.parse(JsonOps.INSTANCE, template).getOrThrow();
        TrackLinkData plan = full.asPlan();
        assertTrue(full.isBuilt());
        assertEquals(90, full.links());
        assertFalse(plan.isBuilt());
        assertEquals(0, plan.links());
        assertEquals(full.members(), plan.members());
        assertEquals(full.linkCount(), plan.linkCount());
    }

    // ==================== 扇区契约 ====================

    @Test
    void sectorMaxAggregatesToPerLinkDurability() {
        int sectors = M1A2_MEMBERS;
        float sectorMax = M1A2_PER_LINK * M1A2_LINKS / sectors;
        // S × sectorMax == durability_per_link × N
        assertEquals(M1A2_PER_LINK * M1A2_LINKS, sectors * sectorMax, EPS);
    }

    @Test
    void eachSectorCarriesAtLeastOneLink() {
        // N >= M 是扇区模型成立的前提：linksPerSector >= 1
        for (int[] pair : new int[][]{{80, 9}, {88, 8}, {97, 8}, {9, 9}}) {
            assertTrue(pair[0] / (float) pair[1] >= 1f, "N=" + pair[0] + " M=" + pair[1]);
        }
        assertEquals(12, TrackLink.normalizeLinkCount(9, 9, 4));
        assertEquals(9, TrackLink.normalizeLinkCount(9, 9, 1));
        assertEquals(88, TrackLink.normalizeLinkCount(88, 8, 8));
        // 节数被抬到周期整数倍时仍不小于成员数
        assertEquals(12, TrackLink.normalizeLinkCount(5, 9, 4));
        assertEquals(0, TrackLink.normalizeLinkCount(5, 9, 4) % 4);
    }

    @Test
    void linksSubtractTornPenaltyOncePerUnpaidBreach() {
        int sectors = M1A2_MEMBERS;
        int tornLinks = TrackSectors.tornLinks(M1A2_LINKS, sectors);
        assertEquals(3, tornLinks);

        TrackSectors pristine = TrackSectors.full(sectors);
        assertEquals(M1A2_LINKS, pristine.links(M1A2_LINKS));

        // 一个已上料的断口（材料非 0）：不扣节
        TrackSectors welding = pristine.withFill(2, 0.4f).withTorn(2, true);
        assertEquals(M1A2_LINKS, welding.links(M1A2_LINKS));

        // 一个未上料的断口（材料为 0）：扣 R 节
        TrackSectors open = pristine.withFill(2, 0f).withTorn(2, true);
        assertEquals(M1A2_LINKS - tornLinks, open.links(M1A2_LINKS));
        // 两个断口：各扣 R 节
        TrackSectors twoBreaches = open.withFill(5, 0f).withTorn(5, true);
        assertEquals(M1A2_LINKS - 2 * tornLinks, twoBreaches.links(M1A2_LINKS));
        // 九段全断：每段各扣 R 节
        TrackSectors allTorn = pristine;
        for (int j = 0; j < sectors; j++) allTorn = allTorn.withFill(j, 0f).withTorn(j, true);
        assertEquals(M1A2_LINKS - sectors * tornLinks, allTorn.links(M1A2_LINKS));
        // 每扇区材料恰好等于 R 节时，全断会把 links 扣到 0 而不是负数
        TrackSectors tiny = TrackSectors.full(3);
        for (int j = 0; j < 3; j++) tiny = tiny.withFill(j, 0f).withTorn(j, true);
        assertEquals(0, tiny.links(3));
    }

    @Test
    void unbuiltBeltHasNoLinks() {
        assertEquals(0, TrackSectors.UNBUILT.links(80));
        assertFalse(TrackSectors.UNBUILT.isBuilt());
        assertFalse(TrackSectors.UNBUILT.isBroken());
    }

    @Test
    void asPlanEmptiesTornAndPhase() {
        List<TrackMemberRef> members = members(2);
        TrackLinkData broken = new TrackLinkData(members.getFirst(), members,
                List.of(0f, 1f), List.of(true, false), 1f, true, DEFAULT_TYPE, 20);
        TrackLinkData plan = broken.asPlan();
        assertTrue(plan.sectionFill().isEmpty());
        assertTrue(plan.torn().isEmpty());
        assertEquals(0f, plan.sectorPhase(), 0f);
    }

    @Test
    void sectorsResampleByMaterialRatio() {
        TrackSectors source = TrackSectors.full(4)
                .withFill(0, 0f).withTorn(0, true)
                .withFill(3, 0.5f);
        TrackSectors target = TrackSectors.resample(source, 8);
        assertEquals(8, target.size());
        // 战损不凭空消失，也不凭空修满
        assertEquals(0f, target.fill(0), 0f);
        assertTrue(target.isTorn(0));
        assertTrue(target.isTorn(1));
        assertEquals(0.5f, target.fill(6), EPS);
        assertEquals(0.5f, target.fill(7), EPS);
        assertEquals(1f, target.fill(3), EPS);
        // 反过来缩回原长度要保留同样的总量级
        TrackSectors back = TrackSectors.resample(target, 4);
        assertEquals(4, back.size());
        assertEquals(0f, back.fill(0), 0f);
    }

    @Test
    void weldingCapsAtOneAndClearsTornAtTheSameStep() {
        // 从 0 焊起：每拍 5 / 106.67 = 0.046875，22 拍累加 1.03125 → 夹到 1 并清 torn
        float sectorMax = M1A2_PER_LINK * M1A2_LINKS / M1A2_MEMBERS;
        assertEquals(106.6667f, sectorMax, 1.0e-3f);
        TrackSectors sectors = TrackSectors.full(M1A2_MEMBERS).withFill(0, 0f).withTorn(0, true);
        for (int tick = 0; tick < 22; tick++) {
            float filled = TrackSectors.clampRatio(sectors.fill(0) + 5f / sectorMax);
            sectors = sectors.withFill(0, filled);
            if (filled >= 1f) sectors = sectors.withTorn(0, false);
        }
        assertEquals(1f, sectors.fill(0), 0f);
        assertFalse(sectors.isTorn(0));
        assertFalse(sectors.isBroken());
        // 用 == 1f 判定会在这个累加序列上恰好成立，但先比较后夹取会停在 1.03125 之前的一档
        assertEquals(1f, TrackSectors.clampRatio(1.03125f), 0f);
    }

    @Test
    void refundMatchesPointConversion() {
        TrackSectors sectors = TrackSectors.full(M1A2_MEMBERS).withFill(2, 0f).withTorn(2, true);
        float sectorMax = M1A2_PER_LINK * M1A2_LINKS / M1A2_MEMBERS;
        float durability = sectors.totalFill() * sectorMax;
        // 扇区求和式 == round(durability / durability_per_link)
        assertEquals(Math.round(durability / M1A2_PER_LINK), sectors.refundLinks(M1A2_LINKS));
        // M1A2 断带：一个扇区被撕掉，其余 8 个完好 → round(8 × 80 / 9) = 71
        assertEquals(71, sectors.refundLinks(M1A2_LINKS));
        assertEquals(M1A2_LINKS, TrackSectors.full(M1A2_MEMBERS).refundLinks(M1A2_LINKS));
        assertEquals(0, TrackSectors.UNBUILT.refundLinks(M1A2_LINKS));
    }

    @Test
    void sectorIndexWrapsAndFollowsThePhase() {
        // N=80、S=9：每扇区 80/9 节；s_w=0（主动轮）在相位 0 时落 0 号扇区
        assertEquals(0, TrackLink.sectorIndex(0f, 0f, M1A2_LINKS, M1A2_MEMBERS));
        // 一个扇区宽度的位移落到 1 号
        float sectorLength = (float) M1A2_LINKS / M1A2_MEMBERS;
        assertEquals(1, TrackLink.sectorIndex(sectorLength, 0f, M1A2_LINKS, M1A2_MEMBERS));
        // 相位推一个扇区：同一材料坐标落到上一段
        assertEquals(M1A2_MEMBERS - 1,
                TrackLink.sectorIndex(0f, 1f, M1A2_LINKS, M1A2_MEMBERS));
        // 负向绕行同样成立
        assertEquals(1, TrackLink.sectorIndex(0f, -1f, M1A2_LINKS, M1A2_MEMBERS));
        // 材料坐标回绕到 [0, N)
        assertEquals(0, TrackLink.sectorIndex(M1A2_LINKS, 0f, M1A2_LINKS, M1A2_MEMBERS));
        // 梯形带：前两个轮挨得近、后面的远，映射只看 s_w
        assertEquals(0, TrackLink.sectorIndex(0.5f, 0f, M1A2_LINKS, M1A2_MEMBERS));
        assertEquals(1, TrackLink.sectorIndex(9.0f, 0f, M1A2_LINKS, M1A2_MEMBERS));
        assertEquals(4, TrackLink.sectorIndex(40f, 0f, M1A2_LINKS, M1A2_MEMBERS));
        // 非法输入不会越界
        assertEquals(0, TrackLink.sectorIndex(Float.NaN, 0f, M1A2_LINKS, M1A2_MEMBERS));
        assertEquals(0, TrackLink.sectorIndex(1f, Float.NaN, M1A2_LINKS, M1A2_MEMBERS));
        assertEquals(0, TrackLink.sectorIndex(1f, 0f, 0, M1A2_MEMBERS));
    }

    @Test
    void tornLinksScaleWithSectorMaterial() {
        // R = clamp(round((N/S)/3), 1, 3)
        assertEquals(1, TrackSectors.tornLinks(9, 9));
        assertEquals(2, TrackSectors.tornLinks(54, 9));
        assertEquals(3, TrackSectors.tornLinks(81, 9));
        assertEquals(3, TrackSectors.tornLinks(97, 8));
    }

    // ==================== 型号 ====================

    @Test
    void trackAttrCodecUsesDocumentedDefaults() {
        registerMaterial("test:steel");
        var json = JsonParser.parseString("""
                { "material": "test:steel" }
                """);
        TrackAttr attr = TrackAttr.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(12f, attr.durabilityPerLink(), 1.0e-6f);
        assertEquals(0.2f, attr.lengthPerLink(), 1.0e-6f);
        assertEquals(50f, attr.thicknessMm(), 1.0e-6f);
        assertEquals(0.05f, attr.thicknessMeters(), 1.0e-6f);
        assertEquals(TrackAttr.Tension.DEFAULT, attr.tension());
        assertTrue(attr.sounds().fmod().isEmpty());
        assertEquals(MaterialAttr.MaterialSoundAttr.DEFAULT, attr.sounds());
        assertEquals(12f, attr.maxDurability(1), 1.0e-6f);
        assertEquals(720f, attr.maxDurability(60), 1.0e-6f);
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
    void fittingTypeCodecDispatchesTrackJson() {
        registerMaterial("machine_max:track_steel");
        var json = JsonParser.parseString("""
                {
                  "type": "track",
                  "material": "machine_max:track_steel",
                  "thickness_mm": 50.0,
                  "durability_per_link": 12.0,
                  "length_per_link": 0.2,
                  "overwrite": {
                    "sounds": { "fmod": { "bank": "test:material_sounds" } }
                  },
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
        assertEquals(ResourceLocation.parse("test:material_sounds"), track.attr().sounds().fmod().orElseThrow().bank());
        var encoded = FittingType.CODEC.encodeStart(JsonOps.INSTANCE, track).getOrThrow();
        TrackType roundTripped = (TrackType) FittingType.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(track.attr().sounds().fmod(), roundTripped.attr().sounds().fmod());
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

    @Test
    void linkBonesListDefinesPeriodAndRoundTrips() {
        registerMaterial("machine_max:track_steel");
        var json = JsonParser.parseString("""
                {
                  "type": "track",
                  "material": "machine_max:track_steel",
                  "length_per_link": 0.15,
                  "link_bones": ["Pad", "Connector"],
                  "visual": {
                    "model": "machine_max:pad_band.geo",
                    "texture": "machine_max:textures/part/track_link.png"
                  }
                }
                """);
        TrackType type = (TrackType) FittingType.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(List.of("Pad", "Connector"), type.linkBones());
        assertEquals(2, type.linkPeriod());
        var encoded = FittingType.CODEC.encodeStart(JsonOps.INSTANCE, type).getOrThrow();
        TrackType roundTripped = (TrackType) FittingType.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(type.linkBones(), roundTripped.linkBones());
    }

    @Test
    void absentLinkBonesMeansSingleLinkModel() {
        registerMaterial("machine_max:track_steel");
        var json = JsonParser.parseString("""
                {
                  "type": "track",
                  "material": "machine_max:track_steel",
                  "visual": {
                    "model": "machine_max:track_link.geo",
                    "texture": "machine_max:textures/part/track_link.png"
                  }
                }
                """);
        TrackType type = (TrackType) FittingType.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertTrue(type.linkBones().isEmpty());
        assertEquals(1, type.linkPeriod());
    }

    @Test
    void linkBonesRejectsBlankAndDuplicateNames() {
        registerMaterial("machine_max:track_steel");
        var blank = JsonParser.parseString("""
                {
                  "type": "track",
                  "material": "machine_max:track_steel",
                  "link_bones": ["Pad", " "],
                  "visual": {
                    "model": "machine_max:pad_band.geo",
                    "texture": "machine_max:textures/part/track_link.png"
                  }
                }
                """);
        assertThrows(RuntimeException.class,
                () -> FittingType.CODEC.parse(JsonOps.INSTANCE, blank).getOrThrow());

        var duplicate = JsonParser.parseString("""
                {
                  "type": "track",
                  "material": "machine_max:track_steel",
                  "link_bones": ["Pad", "Pad"],
                  "visual": {
                    "model": "machine_max:pad_band.geo",
                    "texture": "machine_max:textures/part/track_link.png"
                  }
                }
                """);
        assertThrows(RuntimeException.class,
                () -> FittingType.CODEC.parse(JsonOps.INSTANCE, duplicate).getOrThrow());
    }

    @Test
    void trackTagsAlwaysCarryMobilityAndKeepAuthoredOnes() {
        registerMaterial("machine_max:track_steel");
        var json = JsonParser.parseString("""
                {
                  "type": "track",
                  "material": "machine_max:track_steel",
                  "tags": [
                    "machine_max:series/t90a",
                    "machine_max:category/mobility",
                    "machine_max:category/decoration"
                  ],
                  "visual": {
                    "model": "machine_max:track_link.geo",
                    "texture": "machine_max:textures/part/track_link.png"
                  }
                }
                """);
        TrackType type = (TrackType) FittingType.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        // 机动由种类并入且只留一份；作者写的其他类别原样保留，顺序在并入项之后
        assertEquals(List.of(
                MMTags.PART_CATEGORY_MOBILITY,
                ResourceLocation.parse("machine_max:series/t90a"),
                ResourceLocation.parse("machine_max:category/decoration")
        ), type.tags());

        var encoded = FittingType.CODEC.encodeStart(JsonOps.INSTANCE, type).getOrThrow();
        TrackType roundTripped = (TrackType) FittingType.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(type.tags(), roundTripped.tags());
    }

    @Test
    void absentTrackTagsMeanMobilityOnly() {
        registerMaterial("machine_max:track_steel");
        var json = JsonParser.parseString("""
                {
                  "type": "track",
                  "material": "machine_max:track_steel",
                  "visual": {
                    "model": "machine_max:track_link.geo",
                    "texture": "machine_max:textures/part/track_link.png"
                  }
                }
                """);
        TrackType type = (TrackType) FittingType.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(List.of(MMTags.PART_CATEGORY_MOBILITY), type.tags());
    }

    @Test
    void malformedTagIdFailsTheWholeType() {
        registerMaterial("machine_max:track_steel");
        var json = JsonParser.parseString("""
                {
                  "type": "track",
                  "material": "machine_max:track_steel",
                  "tags": ["not a resource location"],
                  "visual": {
                    "model": "machine_max:track_link.geo",
                    "texture": "machine_max:textures/part/track_link.png"
                  }
                }
                """);
        assertThrows(RuntimeException.class,
                () -> FittingType.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void twoItemsWithDifferentMaterialAreNotEqual() {
        List<TrackMemberRef> members = members(2);
        TrackLinkData full = built(members, 20);
        assertNotEquals(full, full.asPlan());
    }

    private static void registerMaterial(String id) {
        MMDynamicRes.MATERIALS.putIfAbsent(ResourceLocation.parse(id), new MaterialAttr(
                MaterialAttr.PhysicsAttr.DEFAULT,
                MaterialAttr.ArmorAttr.DEFAULT,
                MaterialAttr.MaterialSoundAttr.DEFAULT,
                MaterialAttr.MaterialParticleAttr.EMPTY
        ));
    }
}
