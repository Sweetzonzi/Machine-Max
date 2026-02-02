package io.github.sweetzonzi.machine_max.network.payload.research;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.BlueprintAttachment;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ResearchAttachmentSyncPayload (
        BlueprintAttachment attachment
) implements CustomPacketPayload {
    public static final Type<ResearchAttachmentSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "research_attachment_sync_payload"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchAttachmentSyncPayload> STREAM_CODEC = StreamCodec.composite(
            BlueprintAttachment.STREAM_CODEC, ResearchAttachmentSyncPayload::attachment,
            ResearchAttachmentSyncPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
