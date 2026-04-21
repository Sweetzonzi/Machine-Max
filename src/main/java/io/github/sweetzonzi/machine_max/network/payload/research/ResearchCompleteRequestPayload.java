package io.github.sweetzonzi.machine_max.network.payload.research;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ResearchCompleteRequestPayload(
        ResourceLocation researchId
) implements CustomPacketPayload {
    public static final Type<ResearchCompleteRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "research_complete_request_payload"));
    public static final StreamCodec<FriendlyByteBuf, ResearchCompleteRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ResearchCompleteRequestPayload::researchId,
            ResearchCompleteRequestPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
