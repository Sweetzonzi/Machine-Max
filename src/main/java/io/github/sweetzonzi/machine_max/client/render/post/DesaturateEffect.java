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
 * 失色（灰度化）后处理效果。
 * <p>从 {@link VisualEffectHelper#desaturationLevel} 读取当前失色程度，
 * 写入 PostChain uniform "Desaturation" 驱动着色器。</p>
 */
public class DesaturateEffect {

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
                    ResourceLocation.parse("machine_max:shaders/post/desaturate.json")
            );
            // 若已有旧链则释放
            if (chain != null) chain.close();
            chain = newChain;
            cachedWidth = mc.getWindow().getWidth();
            cachedHeight = mc.getWindow().getHeight();
            chain.resize(cachedWidth, cachedHeight);
            loaded = true;
            MachineMax.LOGGER.debug("失色后处理已加载");
        } catch (IOException e) {
            MachineMax.LOGGER.error("失色后处理加载失败", e);
            loaded = true; // 标记已尝试，不再重复失败
        }
    }

    /**
     * 每帧在主渲染目标上执行失色后处理。
     *
     * @param partialTick 帧间插值时间
     */
    public void render(float partialTick) {
        if (!MMClientConfig.isSuppressionEnabled()) return;
        if (!loaded && chain == null) {
            load();
            return;
        }
        if (chain == null) return;

        // 窗口尺寸变化时重设
        var mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (w != cachedWidth || h != cachedHeight) {
            cachedWidth = w;
            cachedHeight = h;
            chain.resize(w, h);
        }

        // 从状态存储读取当前失色程度，乘以配置强度倍率
        float level = VisualEffectHelper.desaturationLevel * MMClientConfig.getSuppressionIntensity();
        level = Math.clamp(level, 0f, 1f);

        chain.setUniform("Desaturation", level);
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
