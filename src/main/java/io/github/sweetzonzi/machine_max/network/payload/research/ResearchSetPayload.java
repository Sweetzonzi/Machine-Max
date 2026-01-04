package io.github.sweetzonzi.machine_max.network.payload.research;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ResearchSetPayload(
        ResourceLocation recipe
) implements CustomPacketPayload {
    public static final Type<ResearchSetPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "research_set_payload"));
    public static final StreamCodec<FriendlyByteBuf, ResearchSetPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ResearchSetPayload::recipe,
            ResearchSetPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handler(final ResearchSetPayload payload, final IPayloadContext context) {
        Player player = context.player();
        context.enqueueWork(() -> {
            var research = player.getData(MMAttachments.getBLUEPRINT());
            research.setResearching(context.player(), payload.recipe());
        });
    }
}
