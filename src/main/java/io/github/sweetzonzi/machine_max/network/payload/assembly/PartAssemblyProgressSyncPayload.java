package io.github.sweetzonzi.machine_max.network.payload.assembly;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.ObjectManager;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record PartAssemblyProgressSyncPayload(
        UUID vehicleUUID,
        UUID partUUID,
        float assemblingProgress,
        int materialProgress
) implements CustomPacketPayload {
    public static final Type<PartAssemblyProgressSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part_assembly_progress_sync_payload")
    );
    public static final StreamCodec<FriendlyByteBuf, PartAssemblyProgressSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull PartAssemblyProgressSyncPayload decode(@NotNull FriendlyByteBuf buffer) {
            UUID vehicleUUID = buffer.readUUID();
            UUID partUUID = buffer.readUUID();
            float assemblingProgress = buffer.readFloat();
            int materialProgress = buffer.readInt();
            return new PartAssemblyProgressSyncPayload(vehicleUUID, partUUID, assemblingProgress, materialProgress);
        }

        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, @NotNull PartAssemblyProgressSyncPayload value) {
            buffer.writeUUID(value.vehicleUUID);
            buffer.writeUUID(value.partUUID);
            buffer.writeFloat(value.assemblingProgress);
            buffer.writeInt(value.materialProgress);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PartAssemblyProgressSyncPayload payload, IPayloadContext context) {
        VehicleCore vehicle = ObjectManager.clientAllVehicles.get(payload.vehicleUUID);
        if (vehicle != null) {
            Part part = vehicle.partMap.get(payload.partUUID);
            if (part != null) {
                context.enqueueWork(()->{
                   part.setAssemblingProgress(payload.assemblingProgress);
                   part.setMaterialProgress(payload.materialProgress);
                });
            } else MachineMax.LOGGER.error("{}中未找到部件{}，无法切换涂装。", vehicle, payload.partUUID);
        } else MachineMax.LOGGER.error("未找到载具{}，无法切换涂装。", payload.partUUID);
    }
}
