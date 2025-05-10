package io.github.tt432.machinemax.network.payload;

import com.mojang.datafixers.util.Pair;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.VehicleCore;
import io.github.tt432.machinemax.common.vehicle.VehicleManager;
import io.github.tt432.machinemax.util.data.PosRotVelVel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record SubPartSyncPayload(
        UUID vehicleUUID,//载具UUID
        HashMap<UUID, HashMap<String, Pair<PosRotVelVel, Boolean>>> syncData//部件UUID -> 零件名称 -> 位姿数据
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SubPartSyncPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "phys_sync_payload"));
    public static final StreamCodec<FriendlyByteBuf, SubPartSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SubPartSyncPayload decode(FriendlyByteBuf buffer) {
            UUID vehicleUUID = buffer.readUUID();
            // 读取外层Map大小（使用VarInt优化）
            int outerSize = buffer.readVarInt();
            HashMap<UUID, HashMap<String, Pair<PosRotVelVel, Boolean>>> syncData = new HashMap<>(outerSize);
            for (int i = 0; i < outerSize; i++) {
                // 读取部件UUID
                UUID partUUID = buffer.readUUID();
                // 读取内部Map大小
                int innerSize = buffer.readVarInt();
                HashMap<String, Pair<PosRotVelVel, Boolean>> innerMap = new HashMap<>(innerSize);
                for (int j = 0; j < innerSize; j++) {
                    // 读取子部件名称（使用紧凑UTF编码）
                    String partName = buffer.readUtf();
                    // 读取位姿数据
                    PosRotVelVel data = PosRotVelVel.STREAM_CODEC.decode(buffer);
                    boolean sleep = buffer.readBoolean();
                    innerMap.put(partName, Pair.of(data, sleep));
                }
                syncData.put(partUUID, innerMap);
            }
            return new SubPartSyncPayload(vehicleUUID, syncData);
        }

        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, SubPartSyncPayload value) {
            FriendlyByteBuf.writeUUID(buffer, value.vehicleUUID());
            // 写入外层Map大小
            HashMap<UUID, HashMap<String, Pair<PosRotVelVel, Boolean>>> syncData = value.syncData();
            buffer.writeVarInt(syncData.size());
            for (Map.Entry<UUID, HashMap<String, Pair<PosRotVelVel, Boolean>>> outerEntry : syncData.entrySet()) {
                // 写入部件UUID
                buffer.writeUUID(outerEntry.getKey());
                // 写入内部Map
                HashMap<String, Pair<PosRotVelVel, Boolean>> innerMap = outerEntry.getValue();
                buffer.writeVarInt(innerMap.size());
                for (Map.Entry<String, Pair<PosRotVelVel, Boolean>> innerEntry : innerMap.entrySet()) {
                    // 写入子部件名称（使用紧凑UTF编码）
                    buffer.writeUtf(innerEntry.getKey());
                    // 写入位姿数据
                    PosRotVelVel.STREAM_CODEC.encode(buffer, innerEntry.getValue().getFirst());
                    buffer.writeBoolean(innerEntry.getValue().getSecond());
                }
            }
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handler(final SubPartSyncPayload payload, final IPayloadContext context) {
        //TODO:根据时间戳判定数据包的有效性，并根据延迟情况对客户端位姿进行预测
        VehicleCore vehicle = VehicleManager.clientAllVehicles.get(payload.vehicleUUID);
        if (vehicle != null) vehicle.syncSubParts(payload.syncData);
        else MachineMax.LOGGER.error("收到不存在载具的同步数据包: " + payload.vehicleUUID);
    }
}
