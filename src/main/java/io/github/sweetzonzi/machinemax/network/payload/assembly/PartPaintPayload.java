package io.github.sweetzonzi.machinemax.network.payload.assembly;

import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record PartPaintPayload(
        UUID vehicleUUID,
        UUID partUUID,
        int textureIndex
) implements CustomPacketPayload {
    public static final Type<PartPaintPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part_paint_payload")
    );
    public static final StreamCodec<FriendlyByteBuf, PartPaintPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull PartPaintPayload decode(@NotNull FriendlyByteBuf buffer) {
            UUID vehicleUUID = buffer.readUUID();
            UUID partUUID = buffer.readUUID();
            int textureIndex = buffer.readInt();
            return new PartPaintPayload(vehicleUUID, partUUID, textureIndex);
        }

        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, @NotNull PartPaintPayload value) {
            buffer.writeUUID(value.vehicleUUID);
            buffer.writeUUID(value.partUUID);
            buffer.writeInt(value.textureIndex);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PartPaintPayload payload, IPayloadContext context) {
        VehicleCore vehicle = VehicleManager.clientAllVehicles.get(payload.vehicleUUID);
        if (vehicle != null) {
            Part part = vehicle.partMap.get(payload.partUUID);
            if (part != null) part.switchTexture(payload.textureIndex);
            else MachineMax.LOGGER.error("{}中未找到部件{}，无法切换涂装。", vehicle, payload.partUUID);
        } else MachineMax.LOGGER.error("未找到载具{}，无法切换涂装。", payload.partUUID);
    }
}
