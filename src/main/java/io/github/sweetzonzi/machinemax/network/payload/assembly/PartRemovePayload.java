package io.github.sweetzonzi.machinemax.network.payload.assembly;

import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleManager;
import io.github.sweetzonzi.machinemax.common.vehicle.data.ConnectionData;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PartRemovePayload(
        UUID vehicleUUID,
        UUID partUUID,
        Map<UUID, UUID> spiltVehicles
) implements CustomPacketPayload {
    public static final Type<PartRemovePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part_remove_payload")
    );
    public static final StreamCodec<FriendlyByteBuf, PartRemovePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull PartRemovePayload decode(@NotNull FriendlyByteBuf buffer) {
            UUID vehicleUUID = buffer.readUUID();
            UUID partUUID = buffer.readUUID();
            Map<UUID, UUID> splitVehicles = buffer.readMap(UUIDUtil.STREAM_CODEC, UUIDUtil.STREAM_CODEC);
            return new PartRemovePayload(vehicleUUID, partUUID, splitVehicles);
        }

        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, @NotNull PartRemovePayload value) {
            buffer.writeUUID(value.vehicleUUID);
            buffer.writeUUID(value.partUUID);
            buffer.writeMap(value.spiltVehicles, UUIDUtil.STREAM_CODEC, UUIDUtil.STREAM_CODEC);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PartRemovePayload payload, IPayloadContext context) {
        VehicleCore vehicle = VehicleManager.clientAllVehicles.get(payload.vehicleUUID);
        if (vehicle != null) {
            UUID partUUID = payload.partUUID;
            context.enqueueWork(() -> vehicle.removePart(partUUID, payload.spiltVehicles));
        } else MachineMax.LOGGER.error("未找到载具: " + payload.partUUID);
    }
}
