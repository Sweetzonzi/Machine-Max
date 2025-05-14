package io.github.tt432.machinemax.network.payload.assembly;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.registry.MMAttachments;
import io.github.tt432.machinemax.common.vehicle.VehicleManager;
import io.github.tt432.machinemax.common.vehicle.data.VehicleData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.NeoForgeExtraCodecs;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

//TODO:改成Configuration Task或许更好？通过net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket
public record LevelVehicleDataPayload(
        ResourceKey<Level> dimension,//载具数据包的维度 The dimension of the vehicle data packet
        Set<VehicleData> vehicles,//维度内所有载具信息 All vehicle data in the dimension
        int packetNum//载具数据包分包数量 The number of sub-packets in the vehicle data packet
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LevelVehicleDataPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "level_vehicle_data_payload")
    );
    public static final StreamCodec<FriendlyByteBuf, LevelVehicleDataPayload> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public @NotNull LevelVehicleDataPayload decode(FriendlyByteBuf buffer) {
            ResourceKey<Level> dimension = buffer.readResourceKey(Registries.DIMENSION);
            Set<VehicleData> vehicles = buffer.readJsonWithCodec(NeoForgeExtraCodecs.setOf(VehicleData.CODEC));
            int packetNum = buffer.readInt();
            return new LevelVehicleDataPayload(dimension, vehicles, packetNum);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull LevelVehicleDataPayload value) {
            buffer.writeResourceKey(value.dimension);
            buffer.writeJsonWithCodec(NeoForgeExtraCodecs.setOf(VehicleData.CODEC), value.vehicles);
            buffer.writeInt(value.packetNum);
        }
    };

    public static int receivedPacketCount = 0;
    public static Set<VehicleData> vehicleDataToLoad = new HashSet<>();

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LevelVehicleDataPayload payload, IPayloadContext context) {
        if (payload.dimension == context.player().level().dimension()) {
            MachineMax.LOGGER.info("收到维度载具数据包分包:{}/{}", receivedPacketCount + 1, payload.packetNum);
            vehicleDataToLoad.addAll(payload.vehicles);
            if (receivedPacketCount >= payload.packetNum - 1) {
                MachineMax.LOGGER.info("成功接收维度内所有载具数据，载入中...");
                context.player().level().setData(MMAttachments.getLEVEL_VEHICLES(), vehicleDataToLoad);
                VehicleManager.loadVehicles(context.player().level());
                receivedPacketCount = 0;
                vehicleDataToLoad.clear();
            } else {
                receivedPacketCount++;
            }
        } else {
            MachineMax.LOGGER.error("从维度{}收到载具数据，但玩家不在该维度: ", payload.dimension);
            receivedPacketCount = 0;
        }
    }
}
