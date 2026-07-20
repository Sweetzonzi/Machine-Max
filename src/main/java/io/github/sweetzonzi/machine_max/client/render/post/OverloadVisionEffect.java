package io.github.sweetzonzi.machine_max.client.render.post;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.MMClientConfig;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * 过载黑视/红视后处理效果。
 * <p>从 {@link VisualEffectHelper#overloadLevel} 读取当前过载程度，
 * 正值产生黑视效果，负值产生红视效果，
 * 写入 PostChain uniform "Overload" 驱动着色器。</p>
 */
public class OverloadVisionEffect {

    @Nullable
    private PostChain chain;
    private boolean loaded = false;
    private int cachedWidth = -1;
    private int cachedHeight = -1;

    /**
     * 加载或重新加载 PostChain 资源。
     */
    public void load() {
        var mc = Minecraft.getInstance();
        if (mc.getResourceManager() == null) return;
        try {
            var newChain = new PostChain(
                    mc.getTextureManager(),
                    mc.getResourceManager(),
                    mc.getMainRenderTarget(),
                    ResourceLocation.parse("machine_max:shaders/post/overload_vision.json")
            );
            if (chain != null) chain.close();
            chain = newChain;
            cachedWidth = mc.getWindow().getWidth();
            cachedHeight = mc.getWindow().getHeight();
            chain.resize(cachedWidth, cachedHeight);
            loaded = true;
            MachineMax.LOGGER.debug("过载黑视/红视后处理已加载");
        } catch (IOException e) {
            MachineMax.LOGGER.error("过载黑视/红视后处理加载失败", e);
            loaded = true;
        }
    }

    /**
     * 每帧在主渲染目标上执行过载后处理。
     *
     * @param partialTick 帧间插值时间
     */
    public void render(float partialTick) {
        if (!MMClientConfig.isOverloadEnabled()) return;
        if (!loaded && chain == null) {
            load();
            return;
        }
        if (chain == null) return;

        var mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (w != cachedWidth || h != cachedHeight) {
            cachedWidth = w;
            cachedHeight = h;
            chain.resize(w, h);
        }

        // 读取过载程度，乘以配置强度倍率后钳位到 [-1, 1]
        float level = VisualEffectHelper.overloadLevel * MMClientConfig.getOverloadIntensity();
        level = Math.clamp(level, -1f, 1f);

        chain.setUniform("Overload", level);
        chain.process(partialTick);
        mc.getMainRenderTarget().bindWrite(true);
    }

    /**
     * 释放 GPU 资源。
     */
    public void dispose() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
        loaded = false;
        cachedWidth = -1;
        cachedHeight = -1;
    }
}
