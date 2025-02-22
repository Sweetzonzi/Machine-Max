package io.github.tt432.machinemax.network.payload;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.Part;
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

public record PartPaintPayload(
        String vehicleUUID,
        String partUUID,
        int textureIndex
) implements CustomPacketPayload {
    public static final Type<PartPaintPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part_paint_payload")
    );
    public static final StreamCodec<ByteBuf, PartPaintPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            PartPaintPayload::vehicleUUID,
            ByteBufCodecs.STRING_UTF8,
            PartPaintPayload::partUUID,
            ByteBufCodecs.INT,
            PartPaintPayload::textureIndex,
            PartPaintPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PartPaintPayload payload, IPayloadContext context) {
        VehicleCore vehicle = VehicleManager.clientAllVehicles.get(UUID.fromString(payload.vehicleUUID));
        if (vehicle != null) {
            Part part = vehicle.partMap.get(UUID.fromString(payload.partUUID));
            if (part != null) part.switchTexture(payload.textureIndex);
            else MachineMax.LOGGER.error("{}中未找到部件{}，无法切换涂装。", vehicle, payload.partUUID);
        } else MachineMax.LOGGER.error("未找到载具{}，无法切换涂装。", payload.partUUID);
    }
}
