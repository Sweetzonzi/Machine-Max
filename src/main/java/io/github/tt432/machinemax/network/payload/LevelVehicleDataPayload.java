package io.github.tt432.machinemax.network.payload;

import com.mojang.serialization.Codec;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.registry.MMAttachments;
import io.github.tt432.machinemax.common.vehicle.VehicleCore;
import io.github.tt432.machinemax.common.vehicle.VehicleManager;
import io.github.tt432.machinemax.common.vehicle.data.PartData;
import io.github.tt432.machinemax.common.vehicle.data.VehicleData;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.NeoForgeExtraCodecs;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public record LevelVehicleDataPayload(
        ResourceKey<Level> dimension,
        Set<VehicleData> vehicles
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LevelVehicleDataPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "level_vehicle_data_payload")
    );
    public static final StreamCodec<FriendlyByteBuf, LevelVehicleDataPayload> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public @NotNull LevelVehicleDataPayload decode(FriendlyByteBuf buffer) {
            ResourceKey<Level> dimension = buffer.readResourceKey(Registries.DIMENSION);
            Set<VehicleData> vehicles = buffer.readJsonWithCodec(NeoForgeExtraCodecs.setOf(VehicleData.CODEC));
            return new LevelVehicleDataPayload(dimension, vehicles);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull LevelVehicleDataPayload value) {
            buffer.writeResourceKey(value.dimension);
            buffer.writeJsonWithCodec(NeoForgeExtraCodecs.setOf(VehicleData.CODEC), value.vehicles);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LevelVehicleDataPayload payload, IPayloadContext context) {
        if (payload.dimension == context.player().level().dimension()) {
            context.player().level().setData(MMAttachments.getLEVEL_VEHICLES(), payload.vehicles);
            VehicleManager.loadVehicles(context.player().level());
        } else MachineMax.LOGGER.error("从维度{}收到载具数据，但玩家不在该维度: ", payload.dimension);
    }
}
