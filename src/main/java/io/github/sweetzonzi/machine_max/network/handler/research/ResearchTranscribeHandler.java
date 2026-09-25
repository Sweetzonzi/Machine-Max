package io.github.sweetzonzi.machine_max.network.handler.research;

import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchTranscribePayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ResearchTranscribeHandler {
    public static void handler(final ResearchTranscribePayload payload, final IPayloadContext context) {
        Player player = context.player();
        context.enqueueWork(() -> player.getData(MMAttachments.getBLUEPRINT()).transcribe(player, payload.researchId()));
    }
}
