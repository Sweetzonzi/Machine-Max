package io.github.sweetzonzi.machine_max.network.payload.assembly;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.ObjectManager;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.data.ConnectionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public record VehicleMergePayload(
        UUID retainedVehicleUuid,
        UUID removedVehicleUuid,
        List<ConnectionData> newConnections
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VehicleMergePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "vehicle_merge_payload")
    );
    public static final StreamCodec<FriendlyByteBuf, VehicleMergePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull VehicleMergePayload decode(@NotNull FriendlyByteBuf buffer) {
            UUID retainedVehicleUuid = buffer.readUUID();
            UUID removedVehicleUuid = buffer.readUUID();
            List<ConnectionData> newConnections = buffer.readList(ConnectionData.STREAM_CODEC);
            return new VehicleMergePayload(retainedVehicleUuid, removedVehicleUuid, newConnections);
        }

        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, @NotNull VehicleMergePayload value) {
            buffer.writeUUID(value.retainedVehicleUuid);
            buffer.writeUUID(value.removedVehicleUuid);
            buffer.writeCollection(value.newConnections, ConnectionData.STREAM_CODEC);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VehicleMergePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            VehicleCore retainedVehicle = ObjectManager.getVehicle(context.player().level(), payload.retainedVehicleUuid);
            VehicleCore removedVehicle = ObjectManager.getVehicle(context.player().level(), payload.removedVehicleUuid);
            if (retainedVehicle == null || removedVehicle == null) {
                MachineMax.LOGGER.error("处理载具合并包失败：未找到载具 retained={}, removed={}",
                        payload.retainedVehicleUuid, payload.removedVehicleUuid);
                return;
            }
            retainedVehicle.clientHandleMerge(removedVehicle, payload.newConnections);
        });
    }
}
