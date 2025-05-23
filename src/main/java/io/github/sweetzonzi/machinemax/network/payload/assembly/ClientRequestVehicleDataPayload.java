package io.github.sweetzonzi.machinemax.network.payload.assembly;

import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.registry.MMAttachments;
import io.github.sweetzonzi.machinemax.common.vehicle.data.VehicleData;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public record ClientRequestVehicleDataPayload(
        ResourceKey<Level> dimension
) implements CustomPacketPayload {
    public static final Type<ClientRequestVehicleDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "client_request_level_vehicle_data_payload")
    );
    public static final StreamCodec<ByteBuf, ClientRequestVehicleDataPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION),
            ClientRequestVehicleDataPayload::dimension,
            ClientRequestVehicleDataPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClientRequestVehicleDataPayload payload, IPayloadContext context) {
        if (payload.dimension == context.player().level().dimension()) {
            context.enqueueWork(()->{
                Set<VehicleData> dataToSend = context.player().level().getData(MMAttachments.getLEVEL_VEHICLES());
                int packetNum = (dataToSend.size() + 4) / 5;//计算分包数量，5个载具为一包
                Iterator<VehicleData> iterator = dataToSend.iterator();
                for (int i = 0; i < packetNum; i++) {
                    Set<VehicleData> packetData = new HashSet<>();
                    for (int j = 0; j < 5 && iterator.hasNext(); j++) {
                        packetData.add(iterator.next());
                    }
                    PacketDistributor.sendToPlayer((ServerPlayer) context.player(), new LevelVehicleDataPayload(payload.dimension, packetData, packetNum));
                }
            });
        } else
            MachineMax.LOGGER.error("玩家{}客户端请求的维度{}与玩家所在维度{}不一致:", context.player().getName(), payload.dimension, context.player().level().dimension().location());
    }
}
