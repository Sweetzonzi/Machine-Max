package io.github.sweetzonzi.machine_max.network.handler.research;

import io.github.sweetzonzi.machine_max.client.network.ClientResearchHandler;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchCompletePayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ResearchCompleteHandler {
    public static void handler(final ResearchCompletePayload payload, final IPayloadContext context) {
        Player player = context.player();
        context.enqueueWork(() -> {
            var research = player.getData(MMAttachments.getBLUEPRINT());
            if (!payload.product().isEmpty()) {
                research.getProducts().put(payload.researchId(), payload.product());
            }
            research.getCompletedResearches().add(payload.researchId());
            research.markDirty(player);
            ClientResearchHandler.handleComplete(payload);
        });
    }
}
