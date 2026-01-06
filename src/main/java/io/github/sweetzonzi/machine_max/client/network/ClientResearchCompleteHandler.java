package io.github.sweetzonzi.machine_max.client.network;

import io.github.sweetzonzi.machine_max.client.render.toast.BlueprintResearchToast;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchCompletePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.awt.*;

@OnlyIn(Dist.CLIENT)
public class ClientResearchCompleteHandler {
    public static void handle(ResearchCompletePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.getToasts().addToast(
                new BlueprintResearchToast(
                        payload.product(),
                        Component.translatable("toast.machine_max.research_complete"),
                        payload.product().getHoverName().copy().withColor(Color.WHITE.getRGB())
                )
        ));
    }
}
