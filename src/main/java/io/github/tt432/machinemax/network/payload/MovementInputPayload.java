package io.github.tt432.machinemax.network.payload;

import io.github.tt432.machinemax.MachineMax;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record MovementInputPayload(int id, byte[] input, byte[] inputConflict) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MovementInputPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "move_input_payload"));
    public static final StreamCodec<ByteBuf, MovementInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            MovementInputPayload::id,//按下的按键
            ByteBufCodecs.BYTE_ARRAY,
            MovementInputPayload::input,//各轴向的输入轴量
            ByteBufCodecs.BYTE_ARRAY,
            MovementInputPayload::inputConflict,//0为无冲突，1为有冲突输入，如前进与后退键被同时按下
            MovementInputPayload::new
    );
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void clientHandler(final MovementInputPayload payload, final IPayloadContext context) {
        //将其他玩家的输入同步至本机，以在客户端模拟其他玩家的操作
        //TODO:测试操作延迟情况
        Player player = context.player();
//        if (player.getVEHICLE_BUS() instanceof OldPartEntity e && e.getController() != null) {
//            e.getController().setRawMoveInput(payload.input());
//            e.getController().setMoveInputConflict(payload.inputConflict());
//        }
    }

    public static void serverHandler(final MovementInputPayload payload, final IPayloadContext context) {
        Player player = context.player();
//        if (player.getVEHICLE_BUS() instanceof OldPartEntity e && e.getController() != null) {
//            e.getController().setRawMoveInput(payload.input());
//            e.getController().setMoveInputConflict(payload.inputConflict());
//        }
        //将玩家输入转发给其他玩家，以在其他玩家客户端模拟自己的操作
        PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(), payload);
    }
}
