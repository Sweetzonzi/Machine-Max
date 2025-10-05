package io.github.sweetzonzi.machine_max.network.payload.fabrication;

import io.github.sweetzonzi.machine_max.common.menu.FabricatingMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record FabricationCancelPayload(int taskIndex) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FabricationCancelPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("machine_max", "fabrication_cancel"));

    public static final StreamCodec<FriendlyByteBuf, FabricationCancelPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, FabricationCancelPayload::taskIndex,
            FabricationCancelPayload::new
    );

    public static void handler(FabricationCancelPayload message, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player.containerMenu instanceof FabricatingMenu menu) {
                menu.cancelTask(message.taskIndex());
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}