package io.github.sweetzonzi.machine_max.network.payload.research;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ResearchPushPayload(
        ResourceLocation recipe,
        float finalProgress
) implements CustomPacketPayload {
    public static final Type<ResearchPushPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "research_push_payload"));
    public static final StreamCodec<FriendlyByteBuf, ResearchPushPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ResearchPushPayload::recipe,
            ByteBufCodecs.FLOAT, ResearchPushPayload::finalProgress,
            ResearchPushPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handler(final ResearchPushPayload payload, final IPayloadContext context) {
        Player player = context.player();
        context.enqueueWork(() -> {
            var research = player.getData(MMAttachments.getBLUEPRINT());
            research.setResearching(context.player(), payload.recipe());
            if (payload.finalProgress() > 0f)
                research.getResearchedRecipes().computeIfAbsent(payload.recipe(), k -> payload.finalProgress()); // 同步进度
            else {
                research.getResearchedRecipes().remove(payload.recipe()); // 取消研究
                research.clearResearching(player);
            }
        });
    }
}
