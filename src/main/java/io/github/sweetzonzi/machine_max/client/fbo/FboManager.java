package io.github.sweetzonzi.machine_max.client.fbo;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局 FBO 注册中心（单例）。
 * 注册时若同 ID 已存在则先 close 旧实例（防止资源泄漏）。
 * 由 LevelRendererMixin 驱动 resize/close 生命周期。
 */
public enum FboManager {
    INSTANCE;

    private final Map<ResourceLocation, OffscreenFbo> fbos = new LinkedHashMap<>();

    /**
     * 注册 FBO 实例。若同 ID 已存在则关闭旧实例。
     * @return 传入的 fbo 实例（方便链式调用）
     */
    public OffscreenFbo register(OffscreenFbo fbo) {
        var old = fbos.put(fbo.id, fbo);
        if (old != null) old.close();  // 关闭被替换的旧实例
        return fbo;
    }

    /** 按 ID 获取已注册的 FBO */
    public @Nullable OffscreenFbo get(ResourceLocation id) { return fbos.get(id); }

    /**
     * 通知所有 autoResize 的 FBO 窗口尺寸变化
     * @param w 新窗口宽度
     * @param h 新窗口高度
     */
    public void resizeAll(int w, int h) { fbos.values().forEach(f -> f.onWindowResize(w, h)); }

    /** 关闭并清空所有 FBO */
    public void closeAll() {
        fbos.values().forEach(OffscreenFbo::close);
        fbos.clear();
    }
}
