package io.github.sweetzonzi.machine_max.client.render.post;

import io.github.sweetzonzi.machine_max.client.event.RenderLevelLastEvent;
import io.github.sweetzonzi.machine_max.client.input.CameraShakeController;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * 后处理特效管理器。
 * <p>自身为 {@code @EventBusSubscriber}，自动订阅
 * {@link RenderLevelLastEvent} 和 {@link ClientTickEvent.Pre}，
 * 无需外部手动委托。</p>
 *
 * <p>集中管理所有 PostChain 后处理效果的生命周期：
 * 进入世界时懒加载，窗口 resize 时自动适配，退出世界时释放 GPU 资源。</p>
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class PostProcessingManager {

    private static final PostProcessingManager INSTANCE = new PostProcessingManager();

    private final DesaturateEffect desaturate = new DesaturateEffect();
    private final OverloadVisionEffect overloadVision = new OverloadVisionEffect();
    private final CrtMonitorEffect crtMonitor = new CrtMonitorEffect();

    private PostProcessingManager() {}

    // ========== 事件回调 ==========

    /** 在世界渲染最后一帧时按序执行所有后处理特效：先过载→再失色 */
    @SubscribeEvent
    private static void onRenderLevelLast(RenderLevelLastEvent event) {
        if (Minecraft.getInstance().level == null) return;
        INSTANCE.overloadVision.render(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        INSTANCE.desaturate.render(event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent
    private static void onRenderGui(RenderGuiEvent.Post event) {
        // CRT 滤镜内部自检 enabled 开关，默认关闭时不加载、不处理
        INSTANCE.crtMonitor.render(event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    /** 客户端 Tick：退出世界时自动释放所有 PostChain GPU 资源，同时衰减失色压制效果 */
    @SubscribeEvent
    private static void onClientTick(ClientTickEvent.Pre event) {
        if (Minecraft.getInstance().level == null) {
            INSTANCE.disposeAll();
        } else {
            CameraShakeController.tickSuppression(); // 失色压制每帧衰减
        }
    }

    // ========== 对外开关 ==========

    /**
     * 外部启用/关闭 CRT 显像管滤镜（默认关闭）。
     * <p>关闭时 {@link CrtMonitorEffect} 不加载、不执行 PostChain；
     * 开启后下一帧 GUI 渲染阶段即生效。</p>
     *
     * @param enabled true 启用 CRT 后处理，false 关闭
     */
    public static void setCrtMonitorEnabled(boolean enabled) {
        INSTANCE.crtMonitor.enabled = enabled;
    }

    // ========== 生命周期 ==========

    /** 释放所有后处理资源 */
    private void disposeAll() {
        desaturate.dispose();
        overloadVision.dispose();
        crtMonitor.dispose();
    }
}
