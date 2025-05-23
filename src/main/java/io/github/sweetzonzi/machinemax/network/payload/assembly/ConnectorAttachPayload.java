package io.github.sweetzonzi.machinemax.network.payload.assembly;

import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleManager;
import io.github.sweetzonzi.machinemax.common.vehicle.data.ConnectionData;
import io.github.sweetzonzi.machinemax.common.vehicle.data.PartData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public record ConnectorAttachPayload(
        UUID vehicleUuid,
        List<ConnectionData> connections,
        boolean hasNewPart,
        @Nullable PartData partData
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConnectorAttachPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "connector_attach_payload")
    );
    public static final StreamCodec<FriendlyByteBuf, ConnectorAttachPayload> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public @NotNull ConnectorAttachPayload decode(@NotNull FriendlyByteBuf buffer) {
            UUID vehicleUuid = UUID.fromString(buffer.readUtf());
            List<ConnectionData> connections = buffer.readJsonWithCodec(ConnectionData.CODEC.listOf());
            boolean hasNewPart = buffer.readBoolean();
            PartData partData = null;
            if (hasNewPart) partData = buffer.readJsonWithCodec(PartData.CODEC);
            return new ConnectorAttachPayload(vehicleUuid, connections, hasNewPart, partData);
        }

        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, @NotNull ConnectorAttachPayload value) {
            buffer.writeUtf(value.vehicleUuid.toString());
            buffer.writeJsonWithCodec(ConnectionData.CODEC.listOf(), value.connections);
            buffer.writeBoolean(value.hasNewPart);
            if (value.hasNewPart && value.partData != null) buffer.writeJsonWithCodec(PartData.CODEC, value.partData);
        }
    };

    @Override
    public @NotNull CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConnectorAttachPayload payload, IPayloadContext context) {
        VehicleCore vehicle = VehicleManager.clientAllVehicles.get(payload.vehicleUuid);
        if (vehicle == null) throw new IllegalStateException("未找到载具: " + payload.vehicleUuid);
        if (payload.hasNewPart) {
            if (payload.partData == null) throw new IllegalStateException("载具" + vehicle.name + "应有新部件，但数据包中没有提供新部件数据");
            Part newPart = new Part(payload.partData, vehicle.level);
            vehicle.addPart(newPart);
            newPart.addToLevel();
        }
        for (ConnectionData connection : payload.connections) {
            vehicle.attachConnector(
                    vehicle.partMap.get(UUID.fromString(connection.PartUuidS)).subParts.get(connection.SubPartNameS).connectors.get(connection.SpecialConnectorName),
                    vehicle.partMap.get(UUID.fromString(connection.PartUuidA)).subParts.get(connection.SubPartNameA).connectors.get(connection.AttachPointConnectorName),
                    null
            );
        }
    }
}
