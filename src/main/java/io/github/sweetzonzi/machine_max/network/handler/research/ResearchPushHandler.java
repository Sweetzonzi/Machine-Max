package io.github.sweetzonzi.machine_max.network.handler.research;

import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchPushPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ResearchPushHandler {
    public static void handler(final ResearchPushPayload payload, final IPayloadContext context) {
        Player player = context.player();
        context.enqueueWork(() -> {
            var research = player.getData(MMAttachments.getBLUEPRINT());
            research.setResearching(context.player(), payload.recipe());
            if (payload.finalProgress() > 0f)
                research.getResearchedRecipes().put(payload.recipe(), payload.finalProgress()); // 同步进度
            else {
                research.getResearchedRecipes().remove(payload.recipe()); // 取消研究
                research.clearResearching(player);
            }
        });
    }
}