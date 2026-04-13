package io.github.sweetzonzi.machine_max.network.payload.assembly;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.ObjectManager;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record VehicleStatusSyncPayload(
        UUID vehicleUuid,
        List<SynchedEntityData.DataValue<?>> syncData
) implements CustomPacketPayload {
    public static final Type<VehicleStatusSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "vehicle_status_sync_payload")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleStatusSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull VehicleStatusSyncPayload decode(RegistryFriendlyByteBuf buffer) {
            UUID vehicleUuid = buffer.readUUID();
            List<SynchedEntityData.DataValue<?>> syncData = new ArrayList<>();
            int i;
            while ((i = buffer.readUnsignedByte()) != 255) {
                syncData.add(SynchedEntityData.DataValue.read(buffer, i));
            }
            return new VehicleStatusSyncPayload(vehicleUuid, syncData);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, VehicleStatusSyncPayload value) {
            buffer.writeUUID(value.vehicleUuid());
            for (SynchedEntityData.DataValue<?> dataValue : value.syncData()) {
                dataValue.write(buffer);
            }
            buffer.writeByte(255);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VehicleStatusSyncPayload payload, IPayloadContext context) {
        VehicleCore vehicle = ObjectManager.getVehicle(context.player().level(), payload.vehicleUuid);
        if (vehicle != null) {
            context.enqueueWork(() -> vehicle.getSyncedData().assignValues(payload.syncData));
        } else {
            MachineMax.LOGGER.error("未找到载具状态同步目标: {}", payload.vehicleUuid);
        }
    }
}
