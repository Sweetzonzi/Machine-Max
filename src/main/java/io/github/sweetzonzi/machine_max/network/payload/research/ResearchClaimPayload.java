package io.github.sweetzonzi.machine_max.network.payload.research;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ResearchClaimPayload(
        ResourceLocation researchId
) implements CustomPacketPayload {
    public static final Type<ResearchClaimPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "research_claim_payload"));
    public static final StreamCodec<FriendlyByteBuf, ResearchClaimPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ResearchClaimPayload::researchId,
            ResearchClaimPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
