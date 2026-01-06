package io.github.sweetzonzi.machine_max.network.payload.research;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.BluePrintAttachment;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public record ResearchProductSyncPayload(
        Map<ResourceLocation, ItemStack> researchProducts
) implements CustomPacketPayload {
    public static final Type<ResearchProductSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "research_product_sync_payload"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchProductSyncPayload> STREAM_CODEC = StreamCodec.composite(
            BluePrintAttachment.PRODUCTS_STREAM_CODEC, ResearchProductSyncPayload::researchProducts,
            ResearchProductSyncPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
