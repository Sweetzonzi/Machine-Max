package io.github.sweetzonzi.machine_max.network.handler.research;

import io.github.sweetzonzi.machine_max.client.render.gui.screen.BlueprintResearchScreen;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchScreenOpenPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ResearchScreenOpenHandler {
    public static void handler(final ResearchScreenOpenPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new BlueprintResearchScreen()));
    }
}
