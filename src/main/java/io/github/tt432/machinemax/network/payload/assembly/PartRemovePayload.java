package io.github.tt432.machinemax.network.payload.assembly;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.VehicleCore;
import io.github.tt432.machinemax.common.vehicle.VehicleManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record PartRemovePayload(
        String vehicleUUID,
        String partUUID
) implements CustomPacketPayload {
    public static final Type<PartRemovePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part_remove_payload")
    );
    public static final StreamCodec<ByteBuf, PartRemovePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            PartRemovePayload::vehicleUUID,
            ByteBufCodecs.STRING_UTF8,
            PartRemovePayload::partUUID,
            PartRemovePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PartRemovePayload payload, IPayloadContext context) {
        VehicleCore vehicle = VehicleManager.clientAllVehicles.get(UUID.fromString(payload.vehicleUUID));
        if(vehicle!= null){
            UUID partUUID = UUID.fromString(payload.partUUID);
            context.enqueueWork(() -> vehicle.removePart(partUUID));
        } else MachineMax.LOGGER.error("未找到载具: " + payload.partUUID);
    }
}
