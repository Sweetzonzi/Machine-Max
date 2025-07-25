package io.github.sweetzonzi.machinemax.network.payload;

import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleManager;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

public record PartSyncPayload(
        UUID vehicleUUID,//载具UUID
        UUID partUUID,//部件UUID
        float durability,//部件耐久度
        float integrity,//部件完整度
        Map<String, Float> subsystemDurability//子系统耐久度
) implements CustomPacketPayload {
    public static final Type<PartSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part_sync_payload"));
    public static final StreamCodec<FriendlyByteBuf, PartSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull PartSyncPayload decode(FriendlyByteBuf buffer) {
            UUID vehicleUUID = buffer.readUUID();
            UUID partUUID = buffer.readUUID();
            float durability = buffer.readFloat();
            float integrity = buffer.readFloat();
            Map<String, Float> subsystemDurability = buffer.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readFloat);
            return new PartSyncPayload(vehicleUUID, partUUID, durability, integrity, subsystemDurability);
        }

        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, PartSyncPayload value) {
            FriendlyByteBuf.writeUUID(buffer, value.vehicleUUID());
            FriendlyByteBuf.writeUUID(buffer, value.partUUID());
            buffer.writeFloat(value.durability());
            buffer.writeFloat(value.integrity());
            buffer.writeMap(value.subsystemDurability(), FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeFloat);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handler(final PartSyncPayload payload, final IPayloadContext context) {
        //TODO:根据时间戳判定数据包的有效性，并根据延迟情况对客户端位姿进行预测
        VehicleCore vehicle = VehicleManager.clientAllVehicles.get(payload.vehicleUUID);
        if (vehicle != null) {
            Part part = vehicle.partMap.get(payload.partUUID);
            if (part != null) {
                context.enqueueWork(() -> {
                    if (part.durability > payload.durability) {
                        //标记受伤
                        part.hurtMarked = true;
                        part.oHurtMarked = true;
                    }
                    for (Map.Entry<String, Float> entry : payload.subsystemDurability().entrySet()){
                        String subsystemName = entry.getKey();
                        float subsystemDurability = entry.getValue();
                        AbstractSubsystem subsystem = part.subsystems.get(subsystemName);
                        subsystem.durability = subsystemDurability;
                    }
                    part.durability = payload.durability;
                    part.integrity = payload.integrity;
                });
            } else MachineMax.LOGGER.error("载具{}收到不存在部件的同步数据包", vehicle.name);
        } else MachineMax.LOGGER.error("收到不存在载具的同步数据包: {}", payload.vehicleUUID);
    }
}
