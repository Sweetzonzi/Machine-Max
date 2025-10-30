package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleManager;
import io.github.sweetzonzi.machine_max.util.data.PosRotVelVel;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public record SubPartDataSyncPayload(
        UUID vehicleUUID,
        UUID partUUID,
        String subPartName,
        List<SynchedEntityData.DataValue<?>> syncData //发生变化的数据
) implements CustomPacketPayload {
    public static final Type<SubPartDataSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subpart_data_sync_payload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SubPartDataSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SubPartDataSyncPayload decode(RegistryFriendlyByteBuf buffer) {
            UUID vehicleUUID = buffer.readUUID();
            UUID partUUID = buffer.readUUID();
            String subPartName = buffer.readUtf();
            List<SynchedEntityData.DataValue<?>> syncData = new ArrayList<>();
            int i;
            while ((i = buffer.readUnsignedByte()) != 255) {
                syncData.add(SynchedEntityData.DataValue.read(buffer, i));
            }
            return new SubPartDataSyncPayload(vehicleUUID, partUUID, subPartName, syncData);
        }

        @Override
        public void encode(@NotNull RegistryFriendlyByteBuf buffer, SubPartDataSyncPayload value) {
            buffer.writeUUID(value.vehicleUUID());
            buffer.writeUUID(value.partUUID());
            buffer.writeUtf(value.subPartName());
            for (SynchedEntityData.DataValue<?> datavalue : value.syncData()) {
                datavalue.write(buffer);
            }
            buffer.writeByte(255);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handler(final SubPartDataSyncPayload payload, final IPayloadContext context) {
        //TODO:根据时间戳判定数据包的有效性，并根据延迟情况对客户端位姿进行预测
        VehicleCore vehicle = VehicleManager.clientAllVehicles.get(payload.vehicleUUID);
        if (vehicle != null) {
            Part part = vehicle.partMap.get(payload.partUUID);
            if (part != null) {
                SubPart subPart = part.subParts.get(payload.subPartName());
                if (subPart != null) {
                    subPart.getSynchedData().assignValues(payload.syncData());
                } else MachineMax.LOGGER.error("收到不存在子部件的同步数据包: " + payload.subPartName());
            }else MachineMax.LOGGER.error("收到不存在部件的同步数据包: " + payload.partUUID);
        }
        else MachineMax.LOGGER.error("收到不存在载具的同步数据包: " + payload.vehicleUUID);
    }
}
