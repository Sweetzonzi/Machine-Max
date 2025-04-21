package io.github.tt432.machinemax.network.payload;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.VehicleCore;
import io.github.tt432.machinemax.common.vehicle.VehicleManager;
import io.github.tt432.machinemax.common.vehicle.subsystem.SeatSubsystem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 载具移动控制信号数据包，包含六向输入信号和冲突输入信号(TODO:视角朝向)
 *
 * @param vehicleUUID   控制的载具UUID
 * @param partUUID      控制的载具部件UUID
 * @param subSystemName 控制的子系统名称
 * @param input         六向输入信号
 * @param inputConflict 输入冲突信号
 */
public record MovementInputPayload(
        UUID vehicleUUID,
        UUID partUUID,
        String subSystemName,
        byte[] input,
        byte[] inputConflict) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MovementInputPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "move_input_payload"));
    public static final StreamCodec<FriendlyByteBuf, MovementInputPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull MovementInputPayload decode(FriendlyByteBuf buf) {
            UUID vehicleUUID = buf.readUUID();
            UUID partUUID = buf.readUUID();
            String subSystemName = buf.readUtf();
            byte[] input = buf.readByteArray();
            byte[] inputConflict = buf.readByteArray();
            return new MovementInputPayload(vehicleUUID, partUUID, subSystemName, input, inputConflict);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull MovementInputPayload context) {
            buffer.writeUUID(context.vehicleUUID());
            buffer.writeUUID(context.partUUID());
            buffer.writeUtf(context.subSystemName());
            buffer.writeByteArray(context.input());
            buffer.writeByteArray(context.inputConflict());
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void clientHandler(final MovementInputPayload payload, final IPayloadContext context) {
        //将其他玩家的输入同步至本机，以在客户端模拟其他玩家的操作
        //TODO:测试操作延迟情况
        VehicleCore vehicle = VehicleManager.clientAllVehicles.get(payload.vehicleUUID);
        handler(vehicle, payload);
    }

    public static void serverHandler(final MovementInputPayload payload, final IPayloadContext context) {
        Player player = context.player();
        VehicleCore vehicle = VehicleManager.serverAllVehicles.get(payload.vehicleUUID);
        boolean success = handler(vehicle, payload);
        //将玩家输入转发给其他玩家，以在其他玩家客户端模拟自己的操作
        if (success)
            PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(), payload);
    }

    public static boolean handler(VehicleCore vehicle, final MovementInputPayload payload) {
        if (vehicle != null) {
            if (vehicle.partMap.get(payload.partUUID()) instanceof Part part) {
                if (part.subsystems.get(payload.subSystemName()) instanceof SeatSubsystem subSystem) {
                    subSystem.setMoveInputSignal(payload.input(), payload.inputConflict());
                    return true;
                } else {
                    MachineMax.LOGGER.warn("Received movement input for non-existent sub-system: {}", payload.subSystemName());
                    return false;
                }
            } else {
                MachineMax.LOGGER.warn("Received movement input for non-existent part: {}", payload.partUUID());
                return false;
            }
        } else {
            MachineMax.LOGGER.warn("Received movement input for non-existent vehicle: {}", payload.vehicleUUID());
            return false;
        }

    }
}
