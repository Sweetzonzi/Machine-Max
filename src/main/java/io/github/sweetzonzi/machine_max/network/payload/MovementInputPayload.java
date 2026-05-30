package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 载具移动控制信号数据包，包含六向输入信号和冲突输入信号(TODO:视角朝向)
 *
 * @param subPartId      子零件全局ID
 * @param subSystemName  控制的子系统名称
 * @param input          六向输入信号
 * @param inputConflict  输入冲突信号
 */
public record MovementInputPayload(
        int subPartId,
        String subSystemName,
        byte[] input,
        byte[] inputConflict) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MovementInputPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "move_input_payload"));
    public static final StreamCodec<FriendlyByteBuf, MovementInputPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull MovementInputPayload decode(FriendlyByteBuf buf) {
            int subPartId = buf.readInt();
            String subSystemName = buf.readUtf();
            byte[] input = buf.readByteArray();
            byte[] inputConflict = buf.readByteArray();
            return new MovementInputPayload(subPartId, subSystemName, input, inputConflict);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull MovementInputPayload payload) {
            buffer.writeInt(payload.subPartId());
            buffer.writeUtf(payload.subSystemName());
            buffer.writeByteArray(payload.input());
            buffer.writeByteArray(payload.inputConflict());
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void clientHandler(final MovementInputPayload payload, final IPayloadContext context) {
        DestroyableObject object = ObjectManager.getDestroyableObject(context.player().level(), payload.subPartId());
        if (object instanceof SubPart subPart) {
            handler(subPart, payload);
        }
    }

    public static void serverHandler(final MovementInputPayload payload, final IPayloadContext context) {
        Player player = context.player();
        DestroyableObject object = ObjectManager.getDestroyableObject(player.level(), payload.subPartId());
        boolean success = false;
        if (object instanceof SubPart subPart) {
            success = handler(subPart, payload);
        }
        if (success)
            PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(), payload);
    }

    public static boolean handler(SubPart subPart, final MovementInputPayload payload) {
        if (subPart.subsystems.get(payload.subSystemName()) instanceof AbstractControllableSubsystem subSystem) {
            subSystem.setMoveInputSignal(payload.input(), payload.inputConflict());
            return true;
        } else {
            MachineMax.LOGGER.warn("收到移动输入但子系统 {} 不存在于 SubPart(id={})", payload.subSystemName(), payload.subPartId());
            return false;
        }
    }
}
