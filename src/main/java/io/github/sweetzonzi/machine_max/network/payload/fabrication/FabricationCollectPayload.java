package io.github.sweetzonzi.machine_max.network.payload.fabrication;

import io.github.sweetzonzi.machine_max.common.menu.FabricatingMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FabricationCollectPayload(int taskIndex) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FabricationCollectPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("machine_max", "fabrication_collect"));

    public static final StreamCodec<FriendlyByteBuf, FabricationCollectPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, FabricationCollectPayload::taskIndex,
            FabricationCollectPayload::new
    );

    public static void handler(FabricationCollectPayload message, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player.containerMenu instanceof FabricatingMenu menu) {
                menu.collectTask(message.taskIndex(), player);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}