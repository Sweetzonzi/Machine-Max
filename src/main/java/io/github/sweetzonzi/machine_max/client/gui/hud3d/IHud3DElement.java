package io.github.sweetzonzi.machine_max.client.gui.hud3d;

import net.minecraft.client.player.LocalPlayer;

public interface IHud3DElement {

    /**
     * 是否应当在当前帧渲染该 HUD 元素
     */
    boolean shouldRender(LocalPlayer player);

    /**
     * 执行实际渲染（已处于相机空间）
     */
    void render(Hud3DContext context);
}

