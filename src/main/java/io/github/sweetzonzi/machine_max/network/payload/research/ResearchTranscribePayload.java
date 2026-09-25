package io.github.sweetzonzi.machine_max.network.payload.research;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 玩家请求抄录一个已完成的研发条目：消耗一张空白蓝图，取出对应的制造蓝图。
 *
 * @param researchId 研发配方ID
 */
public record ResearchTranscribePayload(
        ResourceLocation researchId
) implements CustomPacketPayload {
    public static final Type<ResearchTranscribePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "research_transcribe_payload"));
    public static final StreamCodec<FriendlyByteBuf, ResearchTranscribePayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ResearchTranscribePayload::researchId,
            ResearchTranscribePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
