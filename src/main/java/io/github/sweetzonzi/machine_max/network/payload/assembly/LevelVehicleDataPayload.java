package io.github.sweetzonzi.machine_max.network.payload.assembly;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.vehicle.ObjectManager;
import io.github.sweetzonzi.machine_max.common.vehicle.data.VehicleData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

//TODO:改成Configuration Task或许更好？通过net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket
public record LevelVehicleDataPayload(
        ResourceKey<Level> dimension,//载具数据包的维度 The dimension of the vehicle data packet
        VehicleData vehicle,//有载具信息
        int packetNum//载具数据包分包数量 The number of sub-packets in the vehicle data packet
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LevelVehicleDataPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "level_vehicle_data_payload")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, LevelVehicleDataPayload> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public @NotNull LevelVehicleDataPayload decode(RegistryFriendlyByteBuf buffer) {
            ResourceKey<Level> dimension = buffer.readResourceKey(Registries.DIMENSION);
            VehicleData vehicle = VehicleData.STREAM_CODEC.decode(buffer);
            int packetNum = buffer.readInt();
            return new LevelVehicleDataPayload(dimension, vehicle, packetNum);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, @NotNull LevelVehicleDataPayload value) {
            buffer.writeResourceKey(value.dimension);
            VehicleData.STREAM_CODEC.encode(buffer, value.vehicle);
            buffer.writeInt(value.packetNum);
        }
    };

    public static int receivedPacketCount = 0;
    public static final Set<VehicleData> vehicleDataToLoad = new HashSet<>();

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LevelVehicleDataPayload payload, IPayloadContext context) {
        Level level = context.player().level();
        context.enqueueWork(()-> {
            if (payload.dimension == level.dimension()) {
                MachineMax.LOGGER.info("收到维度载具数据包分包:{}/{}", receivedPacketCount + 1, payload.packetNum);
                vehicleDataToLoad.add(payload.vehicle);
                if (receivedPacketCount >= payload.packetNum - 1) {
                    MachineMax.LOGGER.info("成功接收维度内所有载具数据，载入中...");
                    level.setData(MMAttachments.getLEVEL_VEHICLES(), vehicleDataToLoad);
                    ObjectManager.loadVehicles(context.player().level());
                    receivedPacketCount = 0;
                    vehicleDataToLoad.clear();
                } else {
                    receivedPacketCount++;
                }
            } else {
                MachineMax.LOGGER.error("从维度{}收到载具数据，但玩家不在该维度: ", payload.dimension);
                receivedPacketCount = 0;
            }
        });
    }
}
