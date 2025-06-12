package io.github.sweetzonzi.machinemax.network.payload;

import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleManager;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.ScriptableSubsystem;
import io.github.sweetzonzi.machinemax.external.js.hook.Hook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ScriptablePayload(String from, String to, CompoundTag nbt) implements CustomPacketPayload {
    public static final Type<ScriptablePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "scriptable_payload"));
    public static final StreamCodec<FriendlyByteBuf, ScriptablePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            ScriptablePayload::from,
            ByteBufCodecs.STRING_UTF8,
            ScriptablePayload::to,
            ByteBufCodecs.TRUSTED_COMPOUND_TAG,
            ScriptablePayload::nbt,
            ScriptablePayload::new
    );
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void clientHandler(final ScriptablePayload payload, final IPayloadContext context) {
        Player player = context.player();
        Level level = player.level();
        receive(payload, level);
        Hook.run(payload.from, payload.to, payload.nbt, context, player, level);
    }

    private static void receive(ScriptablePayload payload, Level level) {
        for (VehicleCore core : VehicleManager.levelVehicles.get(level)) {
            for (AbstractSubsystem subsystem : core.getSubSystemController().getAllSubsystems()) {
                if (subsystem instanceof ScriptableSubsystem sc && sc.script.equals(payload.to)) {
                    sc.receiveNbt(payload.from, payload.nbt);
                }
            }
        }
    }

    public static void serverHandler(final ScriptablePayload payload, final IPayloadContext context) {
        Player player = context.player();
        Level level = player.level();
        receive(payload, level);
        Hook.run(payload.from, payload.to, payload.nbt, context, player, level);
        PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(), payload);
    }


}
