package io.github.sweetzonzi.machine_max.network.payload.research;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ResearchApplyFreeRpPayload(
        ResourceLocation recipe
) implements CustomPacketPayload {
    public static final Type<ResearchApplyFreeRpPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "research_apply_free_rp_payload"));
    public static final StreamCodec<ByteBuf, ResearchApplyFreeRpPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ResearchApplyFreeRpPayload::recipe,
            ResearchApplyFreeRpPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
