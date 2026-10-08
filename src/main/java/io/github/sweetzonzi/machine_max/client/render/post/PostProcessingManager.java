package io.github.sweetzonzi.machine_max.client.render.post;

import io.github.sweetzonzi.machine_max.client.event.RenderLevelLastEvent;
import io.github.sweetzonzi.machine_max.client.input.CameraShakeController;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

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
    private final BlastDistortionEffect blastDistortion = new BlastDistortionEffect();

    private PostProcessingManager() {}

    // ========== 事件回调 ==========

    /** 在世界渲染最后一帧时按序执行所有后处理特效：折射最先 → 过载 → 失色 */
    @SubscribeEvent
    private static void onRenderLevelLast(RenderLevelLastEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // 爆炸波前折射要读未被染色的画面，因此必须排在过载与失色之前
        INSTANCE.blastDistortion.render(
                event.getModelViewMatrix(),
                event.getProjectionMatrix(),
                event.getCamera().getPosition(),
                ExplosionManager.get(mc.level).visuals(),
                partialTick);
        INSTANCE.overloadVision.render(partialTick);
        INSTANCE.desaturate.render(partialTick);
    }

    /**
     * 世界深度快照：取在 {@code AFTER_LEVEL}，也就是世界画完、手部块之前。
     *
     * <p>手部块（{@code GameRenderer#renderLevel} 中 {@code popPush("hand")} 之后）会执行一次
     * {@code RenderSystem.clear(GL_DEPTH_BUFFER_BIT)}：原版会话里深度写掩码为真，它把主目标深度整体写成远平面；
     * 光影会话里掩码为假，清除是空操作。等到了 {@link RenderLevelLastEvent} 再取，原版下已经没有世界几何，
     * 遮挡判据不可能成立。所以快照点固定在这里，两种会话都能拿到完好的世界深度。</p>
     */
    @SubscribeEvent
    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        INSTANCE.blastDistortion.captureWorldDepth(ExplosionManager.get(mc.level).visuals());
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
        blastDistortion.dispose();
    }
}
