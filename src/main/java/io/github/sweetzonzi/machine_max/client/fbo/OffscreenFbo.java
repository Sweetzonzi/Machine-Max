package io.github.sweetzonzi.machine_max.client.fbo;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * 离屏渲染目标（FBO）。
 *
 * <h3>两种模式</h3>
 * <ul>
 *   <li><b>PostChain 模式</b>（inspector）：持有 postChain 提供的 input / output
 *       和可选 mcdepth，后处理 blit output 到屏幕</li>
 *   <li><b>单目标模式</b>（CCTV）：直接持有单 RenderTarget，通过
 *       {@link #getColorTextureId()} 暴露纹理供外部采样</li>
 * </ul>
 *
 * <h3>GPU 资源模型</h3>
 * 每个 {@link RenderTarget} 在 GPU 显存中持有 FBO + 颜色纹理 + 可选深度纹理。
 * 全部资源由 {@link RenderTarget#destroyBuffers()} 释放。
 */
public class OffscreenFbo implements AutoCloseable {

    // ── 尺寸参数（所有模式通用）──
    final ResourceLocation id;
    int width, height;           // 当前分辨率
    final boolean autoResize;    // 窗口 resize 时是否自动重算 width/height

    /** 构造时存储的 PostChain ID，loadPostChain() 使用 */
    @Nullable final ResourceLocation postChainId;

    // ── PostChain 模式字段 ──
    @Nullable PostChain postChain;
    @Nullable RenderTarget input, mcdepth, output;

    // ── 单目标模式字段 ──
    @Nullable RenderTarget renderTarget;

    // ── 状态 ──
    boolean renderedAnything;    // 本帧是否有内容写入（控制 process() 是否执行）

    // ═══════════════════ 工厂方法 ═══════════════════

    /** PostChain 模式（如 inspector）：使用完整窗口分辨率并自动 resize */
    public static OffscreenFbo postProcess(ResourceLocation id, ResourceLocation postChainId) {
        var window = Minecraft.getInstance().getWindow();
        return new OffscreenFbo(id, window.getWidth(), window.getHeight(), true, postChainId);
    }

    /** 固定分辨率模式（如 CCTV）：autoResize=false */
    public static OffscreenFbo fixed(ResourceLocation id, int width, int height) {
        return new OffscreenFbo(id, width, height, false, null);
    }

    private OffscreenFbo(ResourceLocation id, int width, int height,
                         boolean autoResize, @Nullable ResourceLocation postChainId) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.autoResize = autoResize;
        this.postChainId = postChainId;

        if (postChainId != null) {
            // PostChain 模式：由 loadPostChain() 延迟加载（需在 GL 上下文就绪后调用）
        } else {
            // 单目标模式：立即创建
            renderTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        }
    }

    /** 加载后处理链；失败时保持关闭状态并返回 false */
    public boolean loadPostChain(Minecraft mc) {
        if (postChain != null) {
            postChain.close();
        }
        postChain = null;
        input = mcdepth = output = null;
        PostChain loaded = null;
        try {
            loaded = new PostChain(mc.getTextureManager(), mc.getResourceManager(),
                mc.getMainRenderTarget(), postChainId);
            loaded.resize(width, height);
            var loadedInput = loaded.getTempTarget("input");
            var loadedDepth = loaded.getTempTarget("mcdepth");
            var loadedOutput = loaded.getTempTarget("output");
            if (loadedInput == null || loadedOutput == null) {
                throw new JsonSyntaxException("Post chain must define input and output targets");
            }
            postChain = loaded;
            input = loadedInput;
            mcdepth = loadedDepth;
            output = loadedOutput;
            return true;
        } catch (IOException | JsonSyntaxException e) {
            if (loaded != null) loaded.close();
            MachineMax.LOGGER.warn("无法加载 FBO '{}' 的后处理链", id, e);
            return false;
        }
    }

    // ═══════════════════ 尺寸 ═══════════════════

    /** 窗口 resize 回调：仅 autoResize 模式重新计算尺寸 */
    void onWindowResize(int windowW, int windowH) {
        if (!autoResize) return;
        width = Math.max(1, windowW);
        height = Math.max(1, windowH);
        applyResize();
    }

    /** 手动设置新分辨率并立即生效 */
    public void setResolution(int w, int h) {
        this.width = w;
        this.height = h;
        applyResize();
    }

    /** 将当前 width/height 应用到 GPU 资源 */
    private void applyResize() {
        if (postChain != null) {
            postChain.resize(width, height);
            input   = postChain.getTempTarget("input");
            mcdepth = postChain.getTempTarget("mcdepth");
            output  = postChain.getTempTarget("output");
        }
        if (renderTarget != null) {
            renderTarget.resize(width, height, Minecraft.ON_OSX);
        }
    }

    // ═══════════════════ PostChain 模式渲染 ═══════════════════

    /** 清空 input 并恢复主渲染目标（AFTER_SKY 调用）。clear() 后显式恢复主目标 */
    public void clearInput(Minecraft mc) {
        if (input == null) return;
        input.clear(Minecraft.ON_OSX);
        mc.getMainRenderTarget().bindWrite(true);    // true = 恢复 viewport
        renderedAnything = false;
    }

    /** 绑定 input 以写入剪影。切换不同尺寸目标时需用 true 恢复 viewport */
    public void bindInput() {
        if (input != null) input.bindWrite(true);
    }

    /** 从主帧缓冲拷贝深度到 mcdepth（AFTER_LEVEL 调用，此时深度完整） */
    public void copyMainDepth(Minecraft mc) {
        if (mcdepth == null) return;
        mcdepth.clear(Minecraft.ON_OSX);
        mcdepth.copyDepthFrom(mc.getMainRenderTarget());
        mc.getMainRenderTarget().bindWrite(true);
    }

    /** 由调用方在确认至少渲染了一个 SubPart 后设置 */
    public void setRenderedAnything(boolean value) { this.renderedAnything = value; }

    /** 执行后处理链 + blit output 到屏幕 */
    public void process(float delta) {
        if (postChain == null || output == null || !renderedAnything) return;
        RenderSystem.setShaderColor(1, 1, 1, 1);
        postChain.setUniform("OutlineSize", (float) Minecraft.getInstance().getWindow().getGuiScale());
        postChain.process(delta);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        output.blitToScreen(
            Minecraft.getInstance().getWindow().getWidth(),
            Minecraft.getInstance().getWindow().getHeight(), false);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }

    // ═══════════════════ 单目标模式渲染（CCTV） ═══════════════════

    /** 绑定单目标 FBO 以渲染场景 */
    public void bindWrite() {
        if (renderTarget != null) renderTarget.bindWrite(true);
    }

    public void unbindWrite() {
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }

    public void clearSingle() {
        if (renderTarget == null) return;
        renderTarget.clear(Minecraft.ON_OSX);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }

    /** 获取颜色纹理的 GPU 句柄，用于外部采样 */
    public int getColorTextureId() {
        return renderTarget != null ? renderTarget.getColorTextureId() : -1;
    }

    // ═══════════════════ 生命周期 ═══════════════════

    @Override
    public void close() {
        if (postChain != null) {
            postChain.close();
            postChain = null;
            input = mcdepth = output = null;
        }
        if (renderTarget != null) {
            renderTarget.destroyBuffers();
            renderTarget = null;
        }
    }
}
