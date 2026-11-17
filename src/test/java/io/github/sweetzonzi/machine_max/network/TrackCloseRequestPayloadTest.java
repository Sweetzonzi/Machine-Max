package io.github.sweetzonzi.machine_max.network;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackMemberRef;
import io.github.sweetzonzi.machine_max.network.payload.assembly.TrackCloseRequestPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 履带闭合请求的线格式契约：载具 UUID、型号 id、按铺设顺序排列的成员表。 */
class TrackCloseRequestPayloadTest {
    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("machine_max", "track_steel");

    /** 成员按铺设顺序往返：主动轮在前，顺序本身就是包络的锚定绕行顺序，不能被重排。 */
    @Test
    void roundTripsMembersInLayingOrder() {
        UUID vehicle = UUID.randomUUID();
        TrackCloseRequestPayload payload = new TrackCloseRequestPayload(vehicle, TYPE, List.of(
                new TrackMemberRef(UUID.randomUUID(), "sprocket_left"),
                new TrackMemberRef(UUID.randomUUID(), "road_wheel_2"),
                new TrackMemberRef(UUID.randomUUID(), "idler_left")));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            TrackCloseRequestPayload.STREAM_CODEC.encode(buffer, payload);
            TrackCloseRequestPayload decoded = TrackCloseRequestPayload.STREAM_CODEC.decode(buffer);
            assertFalse(buffer.isReadable());
            assertEquals(payload, decoded);
            assertEquals(vehicle, decoded.vehicleUuid());
            assertEquals(TYPE, decoded.trackType());
            assertEquals(List.of("sprocket_left", "road_wheel_2", "idler_left"),
                    decoded.members().stream().map(TrackMemberRef::subPartName).toList());

            // 逐字段读一遍，固定线格式的字段顺序与类型：载具 → 型号 → 成员表
            buffer.readerIndex(0);
            assertEquals(vehicle, buffer.readUUID());
            assertEquals(TYPE, buffer.readResourceLocation());
            assertEquals(payload.members(), buffer.readList(TrackMemberRef.STREAM_CODEC));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    /** 空成员表照常编码：拒绝它属于服务端校验，不是线格式的义务。 */
    @Test
    void roundTripsEmptyMemberList() {
        TrackCloseRequestPayload payload =
                new TrackCloseRequestPayload(UUID.randomUUID(), TYPE, List.of());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            TrackCloseRequestPayload.STREAM_CODEC.encode(buffer, payload);
            // 载具 16 字节 + 型号（长度前缀 + 名）+ 成员数 varint 1 字节
            assertEquals(16 + 1 + TYPE.toString().length() + 1, buffer.readableBytes());
            assertEquals(payload, TrackCloseRequestPayload.STREAM_CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }
}
