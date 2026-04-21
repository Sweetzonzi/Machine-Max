package io.github.sweetzonzi.machine_max.network.payload.research;

import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.BlueprintAttachment;
import io.github.sweetzonzi.machine_max.util.data.RpAddReason;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ResearchCompletePayload(
        ResourceLocation researchId,
        ItemStack product
) implements CustomPacketPayload {
    public static final Type<ResearchCompletePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "research_complete_payload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchCompletePayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ResearchCompletePayload::researchId,
            ItemStack.OPTIONAL_STREAM_CODEC, ResearchCompletePayload::product,
            ResearchCompletePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
