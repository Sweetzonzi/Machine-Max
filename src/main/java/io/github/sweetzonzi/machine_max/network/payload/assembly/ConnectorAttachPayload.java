package io.github.sweetzonzi.machine_max.network.payload.assembly;

import io.github.sweetzonzi.machine_max.common.mech.part.PartRestoreMode;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.ConnectionData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.RegularPartData;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
        @Nullable RegularPartData partData
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConnectorAttachPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "connector_attach_payload")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ConnectorAttachPayload> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public @NotNull ConnectorAttachPayload decode(@NotNull RegistryFriendlyByteBuf buffer) {
            UUID vehicleUuid = buffer.readUUID();
            List<ConnectionData> connections = buffer.readList(ConnectionData.STREAM_CODEC);
            boolean hasNewPart = buffer.readBoolean();
            RegularPartData partData = null;
            if (hasNewPart) partData = RegularPartData.STREAM_CODEC.decode(buffer);
            return new ConnectorAttachPayload(vehicleUuid, connections, hasNewPart, partData);
        }

        @Override
        public void encode(@NotNull RegistryFriendlyByteBuf buffer, @NotNull ConnectorAttachPayload value) {
            buffer.writeUUID(value.vehicleUuid);
            buffer.writeCollection(value.connections, ConnectionData.STREAM_CODEC);
            buffer.writeBoolean(value.hasNewPart);
            if (value.hasNewPart && value.partData != null) RegularPartData.STREAM_CODEC.encode(buffer, value.partData);
        }
    };

    @Override
    public @NotNull CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConnectorAttachPayload payload, IPayloadContext context) {
        VehicleCore vehicle = ObjectManager.clientAllVehicles.get(payload.vehicleUuid);
        if (vehicle == null) throw new IllegalStateException("未找到载具: " + payload.vehicleUuid);
        if (payload.hasNewPart) {
            if (payload.partData == null) throw new IllegalStateException("载具" + vehicle.name + "应有新部件，但数据包中没有提供新部件数据");
            RegularPart newPart = PartFactory.restoreRegular(payload.partData, vehicle.level, PartRestoreMode.FULL_STATE);
            vehicle.addPart(newPart);
            vehicle.refreshMaxHp();
            newPart.addToLevel();
        }
        for (ConnectionData connection : payload.connections) {
            vehicle.attachConnector(
                    vehicle.partMap.get(connection.partUuidA).subParts.get(connection.subPartNameA).connectors.get(connection.getAdvConnectorName()),
                    vehicle.partMap.get(connection.partUuidS).subParts.get(connection.subPartNameS).connectors.get(connection.getSimpleConnectorName()),
                    null
            );
        }
    }
}
