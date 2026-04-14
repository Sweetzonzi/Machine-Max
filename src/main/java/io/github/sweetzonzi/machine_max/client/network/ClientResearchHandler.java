package io.github.sweetzonzi.machine_max.client.network;

import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.client.render.toast.BlueprintResearchToast;
import io.github.sweetzonzi.machine_max.network.payload.research.FreeRpSyncPayload;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchCompletePayload;
import io.github.sweetzonzi.machine_max.util.data.RpAddReason;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

@OnlyIn(Dist.CLIENT)
public class ClientResearchHandler {
    private static final Queue<Pair<RpAddReason, Integer>> RP_CHANGE = new ArrayDeque<>();
    public static void handleComplete(ResearchCompletePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (!payload.product().isEmpty()) {
            mc.execute(() -> mc.getToasts().addToast(
                    new BlueprintResearchToast(
                            payload.product(),
                            Component.translatable("toast.machine_max.research_complete"),
                            payload.product().getHoverName().copy().withColor(Color.WHITE.getRGB())
                    )
            ));
        }
    }

    public static void handleFreeRpChange(FreeRpSyncPayload payload) {
        RP_CHANGE.addAll(payload.rpChanges());
    }

    /**
     * 获取所有RP变化
     * @return 原因-变化值列表
     */
    public static List<Pair<RpAddReason, Integer>> getRpChanges() {
        List<Pair<RpAddReason, Integer>> result = new ArrayList<>();
        while (!RP_CHANGE.isEmpty()) {
            result.add(RP_CHANGE.poll());
        }
        return result;
    }
}
