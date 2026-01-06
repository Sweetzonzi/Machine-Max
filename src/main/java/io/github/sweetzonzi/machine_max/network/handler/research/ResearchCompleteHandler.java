package io.github.sweetzonzi.machine_max.network.handler.research;

import io.github.sweetzonzi.machine_max.client.network.ClientResearchCompleteHandler;
import io.github.sweetzonzi.machine_max.client.render.toast.BlueprintResearchToast;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchCompletePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.awt.Color;

public class ResearchCompleteHandler {
    public static void handler(final ResearchCompletePayload payload, final IPayloadContext context) {
        Player player = context.player();
        context.enqueueWork(() -> {
            var research = player.getData(MMAttachments.getBLUEPRINT());
            research.clearResearching(context.player());
            research.getProducts().put(payload.recipe(), payload.product());
            research.getResearchedRecipes().put(payload.recipe(), (float) payload.level());
            research.markDirty(player);
            ClientResearchCompleteHandler.handle(payload);
        });
    }
}