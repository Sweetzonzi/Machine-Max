package io.github.sweetzonzi.machine_max.network.handler.research;

import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchAttachmentSyncPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ResearchAttachmentSyncHandler {
    public static void handler(final ResearchAttachmentSyncPayload payload, final IPayloadContext context) {
        Player player = context.player();
        context.enqueueWork(() -> {
            player.setData(MMAttachments.getBLUEPRINT(), payload.attachment());
        });
    }
}