package io.github.sweetzonzi.machine_max.network.payload.research;

import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.BlueprintAttachment;
import io.github.sweetzonzi.machine_max.util.data.RpAddReason;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ResearchPushPayload(
        ResourceLocation recipe,
        float finalProgress,
        List<Pair<RpAddReason, Integer>> rpChanges
) implements CustomPacketPayload {
    public static final Type<ResearchPushPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "research_push_payload"));
    public static final StreamCodec<FriendlyByteBuf, ResearchPushPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ResearchPushPayload::recipe,
            ByteBufCodecs.FLOAT, ResearchPushPayload::finalProgress,
            BlueprintAttachment.RP_CHANGE_LIST_STREAM_CODEC, ResearchPushPayload::rpChanges,
            ResearchPushPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
