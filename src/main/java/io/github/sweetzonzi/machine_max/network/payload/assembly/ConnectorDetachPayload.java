package io.github.sweetzonzi.machine_max.network.payload.assembly;

import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.ObjectManager;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AttachPointConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.data.ConnectionData;
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
        public @NotNull ConnectorDetachPayload decode(@NotNull FriendlyByteBuf buffer) {
            UUID vehicleUuid = buffer.readUUID();
            List<ConnectionData> connection = buffer.readList(ConnectionData.STREAM_CODEC);
            Map<UUID, UUID> splitVehicles = buffer.readMap(UUIDUtil.STREAM_CODEC, UUIDUtil.STREAM_CODEC);
            return new ConnectorDetachPayload(vehicleUuid, connection, splitVehicles);
        }

        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, @NotNull ConnectorDetachPayload value) {
            buffer.writeUUID(value.vehicleUuid);
            buffer.writeCollection(value.connections, ConnectionData.STREAM_CODEC);
            buffer.writeMap(value.splitVehicles, UUIDUtil.STREAM_CODEC, UUIDUtil.STREAM_CODEC);
        }
    };

    @Override
    public @NotNull CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConnectorDetachPayload payload, IPayloadContext context) {
        context.enqueueWork(()->{
            try{
                VehicleCore vehicle = ObjectManager.clientAllVehicles.get(payload.vehicleUuid);
                if (vehicle == null) throw new NullPointerException("未找到载具: " + payload.vehicleUuid);
                List<Pair<AbstractConnector, AttachPointConnector>> connections = new ArrayList<>();
                for (ConnectionData connection : payload.connections) {
                    AbstractConnector connectorA = vehicle.partMap.get(UUID.fromString(connection.partUuidS)).externalConnectors.get(Pair.of(connection.subPartNameS,connection.specialConnectorName));
                    AbstractConnector connectorB = vehicle.partMap.get(UUID.fromString(connection.partUuidA)).externalConnectors.get(Pair.of(connection.subPartNameA,connection.attachPointConnectorName));
                    if (connectorA == null || connectorB == null)
                        throw new NullPointerException("未找到对接口: " + payload.connections);
                    connections.add(Pair.of(connectorA, (AttachPointConnector) connectorB));
                }
                vehicle.detachConnections(connections, payload.splitVehicles);
            } catch (NullPointerException e){
                MachineMax.LOGGER.error("处理载具连接断开包时发生错误: " , e);
            }
        });
    }
}
