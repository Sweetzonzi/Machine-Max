package io.github.sweetzonzi.machinemax.network.payload;

import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleManager;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.ScriptableSubsystem;
import io.github.sweetzonzi.machinemax.external.js.hook.Hook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record ScriptablePayload(UUID vehicleCoreUUID, String from, String to, CompoundTag nbt) implements CustomPacketPayload {
    public static final Type<ScriptablePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "scriptable_payload"));
    public static final StreamCodec<FriendlyByteBuf, ScriptablePayload> STREAM_CODEC = new StreamCodec<FriendlyByteBuf, ScriptablePayload>() {
        @Override
        public ScriptablePayload decode(FriendlyByteBuf buf) {
            return new ScriptablePayload(buf.readUUID(),buf.readUtf(), buf.readUtf(),buf.readNbt());
        }

        @Override
        public void encode(FriendlyByteBuf buf, ScriptablePayload scriptablePayload) {
            buf.writeUUID(scriptablePayload.vehicleCoreUUID);
            buf.writeUtf(scriptablePayload.from);
            buf.writeUtf(scriptablePayload.to);
            buf.writeNbt(scriptablePayload.nbt);
        }
    };
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void clientHandler(final ScriptablePayload payload, final IPayloadContext context) {
        Player player = context.player();
        Level level = player.level();
        receiveNbt(payload, context, player, level);
        Hook.run(payload.from, payload.to, payload.nbt, context, player, level);
    }

    private static void receiveNbt(ScriptablePayload payload, IPayloadContext context, Player player, Level level) {
        for (AbstractSubsystem subsystem : VehicleManager.serverAllVehicles.get(payload.vehicleCoreUUID).getSubSystemController().allSubsystems) {
            if (subsystem instanceof ScriptableSubsystem sc
                    && sc.script.equals(payload.to)
                    && sc.getVehicleCoreUUID().equals(payload.vehicleCoreUUID)
            ) {
                Hook.run(sc, payload.from, payload.nbt, context, player);
            }
        }
    }


    public static void serverHandler(final ScriptablePayload payload, final IPayloadContext context) {
        Player player = context.player();
        Level level = player.level();
        receiveNbt(payload, context, player, level);
        Hook.run(payload.from, payload.to, payload.nbt, context, player, level);
        PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(), payload);
    }


}
