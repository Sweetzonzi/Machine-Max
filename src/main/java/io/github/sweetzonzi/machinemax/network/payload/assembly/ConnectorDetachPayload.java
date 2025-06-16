package io.github.sweetzonzi.machinemax.network.payload.assembly;

import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleManager;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.AttachPointConnector;
import io.github.sweetzonzi.machinemax.common.vehicle.data.ConnectionData;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ConnectorDetachPayload(
        UUID vehicleUuid,
        List<ConnectionData> connections,
        Map<UUID, UUID> splitVehicles
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConnectorDetachPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "connector_detach_payload")
    );
    public static final StreamCodec<FriendlyByteBuf, ConnectorDetachPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ConnectorDetachPayload decode(@NotNull FriendlyByteBuf buffer) {
            UUID vehicleUuid = buffer.readUUID();
            List<ConnectionData> connection = buffer.readJsonWithCodec(ConnectionData.CODEC.listOf());
            Map<UUID, UUID> splitVehicles = buffer.readMap(UUIDUtil.STREAM_CODEC, UUIDUtil.STREAM_CODEC);
            return new ConnectorDetachPayload(vehicleUuid, connection, splitVehicles);
        }

        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, @NotNull ConnectorDetachPayload value) {
            buffer.writeUUID(value.vehicleUuid);
            buffer.writeJsonWithCodec(ConnectionData.CODEC.listOf(), value.connections);
            buffer.writeMap(value.splitVehicles, UUIDUtil.STREAM_CODEC, UUIDUtil.STREAM_CODEC);
        }
    };

    @Override
    public @NotNull CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConnectorDetachPayload payload, IPayloadContext context) {
        VehicleCore vehicle = VehicleManager.clientAllVehicles.get(payload.vehicleUuid);
        if (vehicle == null) throw new IllegalStateException("未找到载具: " + payload.vehicleUuid);
        List<Pair<AbstractConnector, AttachPointConnector>> connections = new ArrayList<>();
        for (ConnectionData connection : payload.connections) {
            AbstractConnector connectorA = vehicle.partMap.get(UUID.fromString(connection.PartUuidS)).externalConnectors.get(connection.SpecialConnectorName);
            AbstractConnector connectorB = vehicle.partMap.get(UUID.fromString(connection.PartUuidA)).externalConnectors.get(connection.AttachPointConnectorName);
            if (connectorA == null || connectorB == null)
                throw new IllegalStateException("未找到对接口: " + payload.connections);
            connections.add(Pair.of(connectorA, (AttachPointConnector) connectorB));
        }
        vehicle.detachConnections(connections, payload.splitVehicles);
    }
}
