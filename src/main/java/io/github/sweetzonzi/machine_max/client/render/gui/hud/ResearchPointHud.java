package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.network.ClientResearchHandler;
import io.github.sweetzonzi.machine_max.client.render.gui.animation.AnimatedFloat;
import io.github.sweetzonzi.machine_max.client.render.gui.animation.TimeSource;
import io.github.sweetzonzi.machine_max.util.Easing;
import io.github.sweetzonzi.machine_max.util.data.RpAddReason;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ResearchPointHud implements LayeredDraw.Layer {
    private static final int ITEM_HEIGHT = 11;
    private static final int WIDTH = 64;

    /**
     * 存储研究点获取原因对应的显示透明度动画对象
     * 键为研究点增加原因，值为控制该原因显示透明度的AnimatedFloat对象
     * 数值范围：0.0（完全透明）到1.0（完全不透明）
     * 用于控制HUD元素的淡入淡出效果
     */
    public static final Map<RpAddReason, AnimatedFloat> RESEARCH_POINT_SHOWS = new HashMap<>();
    /**
     * 存储研究点获取原因对应的数值动画对象
     * 键为研究点增加原因，值为控制该原因研究点数量的AnimatedFloat对象
     * 数值表示当前显示的研究点增量值，会从0动画到目标值
     * 用于在HUD上显示具体获得了多少研究点
     */
    private static final Map<RpAddReason, AnimatedFloat> RESEARCH_POINTS = new HashMap<>();
    private static final AnimatedFloat HEIGHT = new AnimatedFloat(0);
    private static final DecimalFormat decimalFormat = new DecimalFormat("#0"); // 格式化为整数

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        updateTracker(deltaTracker);
        if (RESEARCH_POINTS.isEmpty() && HEIGHT.get() < 1e-5) return;
        int x = (int) (guiGraphics.guiWidth() / 4f);
        int y = (int) (guiGraphics.guiHeight() / 2f);
        guiGraphics.fill(x - 1, y - 2, x + WIDTH + 1, (int) (y + HEIGHT.get()), 0x80000000);
        for (Map.Entry<RpAddReason, AnimatedFloat> entry : RESEARCH_POINTS.entrySet()) {
            MutableComponent reason = entry.getKey().getTranslation();
            float num = entry.getValue().get();
            float alpha = RESEARCH_POINT_SHOWS.get(entry.getKey()).get();
            if (alpha > 0.05)
                guiGraphics.drawString(
                        Minecraft.getInstance().font,
                        reason.append(" +" + decimalFormat.format(num)),
                        x,
                        y,
                        Easing.lerpColorFromTransparent(0xffffffff, alpha)
                );
            y += ITEM_HEIGHT;
        }
    }

    private void updateTracker(DeltaTracker deltaTracker) {
        float currentTime = TimeSource.getTimeSeconds(Minecraft.getInstance(), deltaTracker.getGameTimeDeltaPartialTick(false));
        List<Pair<RpAddReason, Integer>> changes = ClientResearchHandler.getRpChanges();
        for (Pair<RpAddReason, Integer> change : changes) { // 录入新的研究点变化
            RpAddReason reason = change.getFirst();
            int amount = change.getSecond();
            if (RESEARCH_POINTS.containsKey(reason)) {
                AnimatedFloat num = RESEARCH_POINTS.get(reason);
                AnimatedFloat show = RESEARCH_POINT_SHOWS.get(reason);
                num.animateTo(num.getTarget() + amount, 0.5f, currentTime);
                show.setImmediate(1); // 重置淡出时间
                show.animateTo(0, 5.0f, currentTime);
            } else {
                AnimatedFloat num = new AnimatedFloat(0);
                AnimatedFloat show = new AnimatedFloat(1);
                show.easing(Easing::easeIn);
                num.animateTo(amount, 0.5f, currentTime);
                show.animateTo(0, 5.0f, currentTime);
                RESEARCH_POINTS.put(reason, num);
                RESEARCH_POINT_SHOWS.put(reason, show);
            }
        }
        Iterator<Map.Entry<RpAddReason, AnimatedFloat>> iterator = RESEARCH_POINTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<RpAddReason, AnimatedFloat> entry = iterator.next();
            AnimatedFloat num = entry.getValue();
            AnimatedFloat show = RESEARCH_POINT_SHOWS.get(entry.getKey());
            if (show.get() <= 1e-5) {
                iterator.remove();
                RESEARCH_POINT_SHOWS.remove(entry.getKey());
            } else {
                num.update(currentTime);
                show.update(currentTime);
            }
        }
        // 更新显示高度
        if (HEIGHT.getTarget() != ITEM_HEIGHT * RESEARCH_POINTS.size())
            HEIGHT.animateTo(ITEM_HEIGHT * RESEARCH_POINTS.size(), 0.1f, currentTime);
        HEIGHT.update(currentTime);
    }

    @SubscribeEvent
    public static void leave(PlayerEvent.PlayerLoggedOutEvent event) {
        // 清理资源
        if (Minecraft.getInstance().player != null && event.getEntity().getUUID().equals(Minecraft.getInstance().player.getUUID())) {
            RESEARCH_POINTS.clear();
            RESEARCH_POINT_SHOWS.clear();
            HEIGHT.setImmediate(0);
        }
    }
}
