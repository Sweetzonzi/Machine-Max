package io.github.sweetzonzi.machine_max.client.render.gui.animation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * HUD 动效时间源
 * - 单人模式暂停时停止
 * - 多人模式始终推进
 */
public final class TimeSource {

    /**
     * 获取当前 HUD 时间（单位：秒）
     */
    public static float getTimeSeconds(Minecraft minecraft, float partialTicks) {
        LocalPlayer player = minecraft.player;

        if (player == null) {
            return 0f;
        }

        // 游戏 tick（暂停时不增长）
        long gameTicks = player.tickCount;

        return (gameTicks + partialTicks) / 20.0f;
    }
}
