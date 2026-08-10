package io.github.sweetzonzi.machine_max.client.render.post;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * CRT 显像管后处理效果（默认关闭）。
 * <p>复刻 CRT 的物理显示特性：高斯光束光斑、扫描线暗缝、荫罩荧光点阵、
 * 荧光粉余晖辉光、屏面渐晕与三枪汇聚误差。</p>
 * <p>参数按"物理真实向"取默认值，每帧写入 PostChain uniform 驱动着色器；
 * 通过 {@link #enabled} 开关控制是否执行，默认关闭，
 * 由外部代码（如载具座舱、按键绑定）在需要时开启。</p>
 */
public class CrtMonitorEffect {

    // ========== 物理真实向参数 ==========

    /** 效果总强度 */
    private static final float INTENSITY = 1.0f;
    /** 扫描线暗缝黑度（1.0 为纯黑消隐线） */
    private static final float SCANLINE_AMOUNT = 0.9f;
    /** 荫罩荧光点阵强度 */
    private static final float MASK_AMOUNT = 0.5f;
    /** 荧光粉辉光强度 */
    private static final float BLOOM_AMOUNT = 0.6f;

    @Nullable
    private PostChain chain;
    private boolean loaded = false;
    private int cachedWidth = -1;
    private int cachedHeight = -1;

    /**
     * CRT 滤镜总开关（默认关闭）。
     * <p>外部代码可直接修改本字段，或通过
     * {@link PostProcessingManager#setCrtMonitorEnabled(boolean)} 启用。</p>
     * <p>关闭时不加载、不执行 PostChain，不产生任何渲染开销。</p>
     */
    public volatile boolean enabled = false;

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
                    ResourceLocation.parse("machine_max:shaders/post/crt_monitor.json")
            );
            if (chain != null) chain.close();
            chain = newChain;
            cachedWidth = mc.getWindow().getWidth();
            cachedHeight = mc.getWindow().getHeight();
            chain.resize(cachedWidth, cachedHeight);
            loaded = true;
            MachineMax.LOGGER.debug("CRT 显像管后处理已加载");
        } catch (IOException e) {
            MachineMax.LOGGER.error("CRT 显像管后处理加载失败", e);
            loaded = true;
        }
    }

    /**
     * 每帧在主渲染目标上执行 CRT 后处理。
     *
     * @param partialTick 帧间插值时间
     */
    public void render(float partialTick) {
        // 开关默认关闭：未启用时不加载、不执行 CRT 后处理
        if (!enabled) return;
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

        // 开关已启用：直接写入物理真实向默认参数
        chain.setUniform("Intensity", INTENSITY);
        chain.setUniform("ScanlineAmount", SCANLINE_AMOUNT);
        chain.setUniform("MaskAmount", MASK_AMOUNT);
        chain.setUniform("BloomAmount", BLOOM_AMOUNT);
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
