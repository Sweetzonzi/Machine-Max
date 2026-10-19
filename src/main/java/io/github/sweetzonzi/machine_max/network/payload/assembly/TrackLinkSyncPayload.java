package io.github.sweetzonzi.machine_max.network.payload.assembly;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLinkData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 履带状态全量同步（服务端 → 客户端）。
 *
 * <p>测试物品阶段只需要「履带列表 + 耐久 / 断带」；全量列表很小，成员变更、
 * 断带与修复都由这一条载荷覆盖。客户端收到后在主线程重建 TrackLink，
 * 不建约束、不施张力。</p>
 */
public record TrackLinkSyncPayload(
        UUID vehicleUuid,
        List<TrackLinkData> trackLinks
) implements CustomPacketPayload {
    public static final Type<TrackLinkSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "track_link_sync")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TrackLinkSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull TrackLinkSyncPayload decode(RegistryFriendlyByteBuf buffer) {
            UUID vehicleUuid = buffer.readUUID();
            List<TrackLinkData> trackLinks = buffer.readList(TrackLinkData.STREAM_CODEC);
            return new TrackLinkSyncPayload(vehicleUuid, trackLinks);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, TrackLinkSyncPayload value) {
            buffer.writeUUID(value.vehicleUuid());
            buffer.writeCollection(value.trackLinks(), TrackLinkData.STREAM_CODEC);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TrackLinkSyncPayload payload, IPayloadContext context) {
        VehicleCore vehicle = ObjectManager.getVehicle(context.player().level(), payload.vehicleUuid());
        if (vehicle == null) {
            MachineMax.LOGGER.error("未找到履带同步目标载具: {}", payload.vehicleUuid());
            return;
        }
        context.enqueueWork(() -> vehicle.applyTrackLinkData(payload.trackLinks()));
    }
}
