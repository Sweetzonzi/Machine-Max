package io.github.sweetzonzi.machinemax.network.payload;

import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.vehicle.*;
import io.netty.buffer.ByteBuf;
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

import java.util.UUID;

public record SubsystemInteractPayload(
        String vehicleUUID,
        String partUUID,
        String subPartName,
        String interactBoxName
) implements CustomPacketPayload {
    public static final Type<SubsystemInteractPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subsystem_interact_payload")
    );
    public static final StreamCodec<ByteBuf, SubsystemInteractPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            SubsystemInteractPayload::vehicleUUID,
            ByteBufCodecs.STRING_UTF8,
            SubsystemInteractPayload::partUUID,
            ByteBufCodecs.STRING_UTF8,
            SubsystemInteractPayload::subPartName,
            ByteBufCodecs.STRING_UTF8,
            SubsystemInteractPayload::interactBoxName,
            SubsystemInteractPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    //将其他玩家的输入同步至本机，以在客户端模拟其他玩家的操作
    public static void clientHandler(final SubsystemInteractPayload payload, final IPayloadContext context) {
        handle(payload, context);
    }

    public static void serverHandler(final SubsystemInteractPayload payload, final IPayloadContext context) {
        handle(payload, context);
        //将玩家输入转发给其他玩家，以在其他玩家客户端模拟自己的操作
        Player player = context.player();
        PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(), payload);
    }

    public static void handle(SubsystemInteractPayload payload, IPayloadContext context) {
        VehicleCore vehicle;
        Level level = context.player().level();
        if (level.isClientSide) vehicle = VehicleManager.clientAllVehicles.get(UUID.fromString(payload.vehicleUUID));
        else vehicle = VehicleManager.serverAllVehicles.get(UUID.fromString(payload.vehicleUUID));
        if (vehicle != null) {
            Part part = vehicle.partMap.get(UUID.fromString(payload.partUUID));
            if (part != null) {
                SubPart subPart = part.subParts.get(payload.subPartName);
                if (subPart != null) {
                    InteractBox interactBox = subPart.interactBoxes.get(payload.interactBoxName);
                    if (interactBox != null) context.enqueueWork(() -> interactBox.interact(context.player()));
                } else MachineMax.LOGGER.error("{}中未找到子部件{}，无法互动。", part, payload.subPartName);
            } else MachineMax.LOGGER.error("{}中未找到部件{}，无法互动。", vehicle, payload.partUUID);
        } else MachineMax.LOGGER.error("未找到载具{}，无法互动。", payload.partUUID);
    }
}
