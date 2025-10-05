package io.github.sweetzonzi.machine_max.network.payload.fabrication;

import io.github.sweetzonzi.machine_max.common.menu.FabricatingMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FabricationStartPayload(ResourceLocation recipeId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FabricationStartPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("machine_max", "fabrication_start"));

    public static final StreamCodec<FriendlyByteBuf, FabricationStartPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, FabricationStartPayload::recipeId,
            FabricationStartPayload::new
    );

    public static void handler(FabricationStartPayload message, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player.containerMenu instanceof FabricatingMenu menu) {
                menu.startFabrication(message.recipeId(), player);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}