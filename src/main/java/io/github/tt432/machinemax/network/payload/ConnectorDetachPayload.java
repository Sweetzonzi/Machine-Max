package io.github.tt432.machinemax.network.payload;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.VehicleCore;
import io.github.tt432.machinemax.common.vehicle.VehicleManager;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.tt432.machinemax.common.vehicle.data.ConnectionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record ConnectorDetachPayload(
        UUID vehicleUuid,
        ConnectionData connection
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConnectorDetachPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "connector_detach_payload")
    );
    public static final StreamCodec<FriendlyByteBuf, ConnectorDetachPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ConnectorDetachPayload decode(@NotNull FriendlyByteBuf buffer) {
            UUID vehicleUuid = UUID.fromString(buffer.readUtf());
            ConnectionData connection = buffer.readJsonWithCodec(ConnectionData.CODEC);
            return new ConnectorDetachPayload(vehicleUuid, connection);
        }

        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, @NotNull ConnectorDetachPayload value) {
            buffer.writeUtf(value.vehicleUuid.toString());
            buffer.writeJsonWithCodec(ConnectionData.CODEC, value.connection);
        }
    };

    @Override
    public @NotNull CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConnectorDetachPayload payload, IPayloadContext context) {
        VehicleCore vehicle = VehicleManager.clientAllVehicles.get(payload.vehicleUuid);
        if (vehicle == null) throw new IllegalStateException("未找到载具: " + payload.vehicleUuid);
        AbstractConnector connectorA = vehicle.partMap.get(UUID.fromString(payload.connection.PartUuidA)).connectors.get(payload.connection.connectorNameA);
        AbstractConnector connectorB = vehicle.partMap.get(UUID.fromString(payload.connection.PartUuidB)).connectors.get(payload.connection.connectorNameB);
        if (connectorA == null || connectorB == null) throw new IllegalStateException("未找到对接口: " + payload.connection);
        vehicle.detachConnector(connectorA, connectorB);
    }
}
