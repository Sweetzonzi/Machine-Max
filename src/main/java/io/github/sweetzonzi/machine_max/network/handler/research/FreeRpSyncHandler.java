package io.github.sweetzonzi.machine_max.network.handler.research;

import io.github.sweetzonzi.machine_max.client.network.ClientResearchHandler;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.network.payload.research.FreeRpSyncPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class FreeRpSyncHandler {
    public static void handler(final FreeRpSyncPayload payload, final IPayloadContext context) {
        Player player = context.player();
        context.enqueueWork(() -> {
            player.getData(MMAttachments.getBLUEPRINT()).setFreeResearchPoint(context.player(), payload.freeRp());
            ClientResearchHandler.handleFreeRpChange(payload);
        });
    }
}
