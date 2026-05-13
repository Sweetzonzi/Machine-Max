package io.github.sweetzonzi.machine_max.network.payload.assembly;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record VehicleRemovePayload(
        ResourceKey<Level> dimension,
        UUID vehicleUUID
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VehicleRemovePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "vehicle_remove_payload")
    );
    public static final StreamCodec<ByteBuf, VehicleRemovePayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION),
            VehicleRemovePayload::dimension,
            UUIDUtil.STREAM_CODEC,
            VehicleRemovePayload::vehicleUUID,
            VehicleRemovePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VehicleRemovePayload payload, IPayloadContext context) {
        if(payload.dimension == context.player().level().dimension()){
            VehicleCore vehicle = ObjectManager.clientAllVehicles.get(payload.vehicleUUID);
            if(vehicle!= null){
                context.enqueueWork(()-> ObjectManager.removeVehicle(vehicle));
            } else MachineMax.LOGGER.error("收到移除不存在的载具的请求: " + payload.vehicleUUID);
        } else MachineMax.LOGGER.error("从错误维度收到载具移除请求: " + payload.dimension);
    }
}
