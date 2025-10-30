package io.github.sweetzonzi.machine_max.network.payload.assembly;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.ObjectManager;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.data.VehicleData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record VehicleCreatePayload(
        ResourceKey<Level> dimension,
        VehicleData vehicle
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VehicleCreatePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "vehicle_create_payload")
    );
    public static final StreamCodec<? super RegistryFriendlyByteBuf, VehicleCreatePayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION),
            VehicleCreatePayload::dimension,
            VehicleData.STREAM_CODEC,
            VehicleCreatePayload::vehicle,
            VehicleCreatePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VehicleCreatePayload payload, IPayloadContext context) {
        if(payload.dimension == context.player().level().dimension()){
            ObjectManager.addVehicle(new VehicleCore(context.player().level(), payload.vehicle, true));
        } else MachineMax.LOGGER.error("从错误的维度收到载具创建请求: {}", payload.dimension);
    }
}
