package io.github.sweetzonzi.machine_max.network.payload.research;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ResearchCompletePayload(
        ResourceLocation recipe,
        int level,
        ItemStack product
) implements CustomPacketPayload {
    public static final Type<ResearchCompletePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "research_complete_payload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchCompletePayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ResearchCompletePayload::recipe,
            ByteBufCodecs.INT, ResearchCompletePayload::level,
            ItemStack.STREAM_CODEC, ResearchCompletePayload::product,
            ResearchCompletePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handler(final ResearchCompletePayload payload, final IPayloadContext context) {
        Player player = context.player();
        context.enqueueWork(() -> {
            var research = player.getData(MMAttachments.getBLUEPRINT());
            research.clearResearching(context.player());
            research.setProduct(payload.product());
            research.getResearchedRecipes().computeIfAbsent(payload.recipe(), k -> (float) payload.level());
            research.markDirty();
        });
    }
}
