package io.github.sweetzonzi.machine_max.network.handler.research;

import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchApplyFreeRpPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ResearchApplyFreeRpHandler {
    public static void handler(final ResearchApplyFreeRpPayload payload, final IPayloadContext context) {
        Player player = context.player();
        context.enqueueWork(() -> {
            var research = player.getData(MMAttachments.getBLUEPRINT());
            if (research.hasStartedResearching(payload.recipe())) {
                research.setResearching(player, payload.recipe());
                research.applyFreeRp(player);
            }
        });
    }
}