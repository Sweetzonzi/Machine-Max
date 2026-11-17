package io.github.sweetzonzi.machine_max.network.payload.assembly;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.ItemInteractHandler;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackMemberRef;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 履带闭合请求（客户端 → 服务端）。
 *
 * <p>铺设草稿只在客户端存在（见 {@code client/track/TrackLayingClient}）：开始、追加与撤销都在本地
 * 完成，闭合时把「这一下要哪些轮子」整份交给服务端。服务端不保存草稿，收到请求后重做全部判定
 * （载具与型号可解析、成员按序属于这条载具、有轮半径、未被别的履带占用、无重复、数量合法、
 * 玩家站在车旁），扣料成功才注册成 {@code TrackLink}。</p>
 *
 * <p>成员表按铺设顺序排列，主动轮在首位：该顺序同时是包络的锚定绕行顺序与张力对的配对顺序，
 * 服务端按同一份顺序交给 {@code TrackLink.assemble}，不重排。</p>
 *
 * @param vehicleUuid 草稿所属载具
 * @param trackType   草稿在开始铺设时解析出的履带型号
 * @param members     按铺设顺序排列的成员（主动轮在前），至少 {@code TrackLink.MIN_MEMBERS} 个
 */
public record TrackCloseRequestPayload(
        @NotNull UUID vehicleUuid,
        @NotNull ResourceLocation trackType,
        @NotNull List<TrackMemberRef> members
) implements CustomPacketPayload {
    public static final Type<TrackCloseRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "track_close_request")
    );

    public static final StreamCodec<FriendlyByteBuf, TrackCloseRequestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, @NotNull TrackCloseRequestPayload payload) {
            buffer.writeUUID(payload.vehicleUuid);
            buffer.writeResourceLocation(payload.trackType);
            buffer.writeCollection(payload.members, TrackMemberRef.STREAM_CODEC);
        }

        @Override
        public @NotNull TrackCloseRequestPayload decode(@NotNull FriendlyByteBuf buffer) {
            return new TrackCloseRequestPayload(
                    buffer.readUUID(),
                    buffer.readResourceLocation(),
                    buffer.readList(TrackMemberRef.STREAM_CODEC));
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 服务端处理器（主线程）：交由 {@link ItemInteractHandler#closeTrack} 做权威校验与闭合。 */
    public static void serverHandler(TrackCloseRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ItemInteractHandler.closeTrack(context.player(), payload));
    }
}
