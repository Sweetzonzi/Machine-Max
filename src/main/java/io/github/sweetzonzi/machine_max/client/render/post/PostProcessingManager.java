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
 * {@link RenderLevelStageEvent}（{@code AFTER_LEVEL}）、{@link RenderLevelLastEvent} 和
 * {@link ClientTickEvent.Pre}，无需外部手动委托。</p>
 *
 * <p>集中管理所有 PostChain 后处理效果的生命周期：
 * 进入世界时懒加载，窗口 resize 时自动适配，退出世界时释放 GPU 资源。</p>
 *
 * <p>同帧内的实际次序：{@code AFTER_LEVEL} 取两份世界深度快照（折射链、曳光链）并提交
 * <b>曳光光照</b> → {@code RenderLevelLastEvent} 提交 <b>折射 → 过载黑视 → 失色</b>。
 * 前三个都需要世界深度，因此都从各自的快照采样、都不读 {@code minecraft:main:depth}；
 * 次序见 {@link #onRenderLevelStage} 的说明。</p>
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class PostProcessingManager {

    private static final PostProcessingManager INSTANCE = new PostProcessingManager();

    private final DesaturateEffect desaturate = new DesaturateEffect();
    private final OverloadVisionEffect overloadVision = new OverloadVisionEffect();
    private final CrtMonitorEffect crtMonitor = new CrtMonitorEffect();
    private final BlastDistortionEffect blastDistortion = new BlastDistortionEffect();
    private final TracerLightEffect tracerLight = new TracerLightEffect();

    private PostProcessingManager() {}

    // ========== 事件回调 ==========

    /** 在世界渲染最后一帧时按序执行：折射最先 → 过载 → 失色（曳光光照更早，见 {@link #onRenderLevelStage}） */
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
     * 世界深度快照与曳光光照：取在 {@code AFTER_LEVEL}，也就是世界画完、手部块之前。
     *
     * <p>手部块（{@code GameRenderer#renderLevel} 中 {@code popPush("hand")} 之后）会执行一次
     * {@code RenderSystem.clear(GL_DEPTH_BUFFER_BIT)}：原版会话里深度写掩码为真，它把主目标深度整体写成远平面；
     * 光影会话里掩码为假，清除是空操作。等到了 {@link RenderLevelLastEvent} 再取，原版下已经没有世界几何，
     * 遮挡判据不可能成立。所以快照点固定在这里，两种会话都能拿到完好的世界深度。</p>
     *
     * <p><b>本方法内的次序是硬约束：两份快照都必须在曳光光照的 pass 之前取走。</b>
     * 任一以 {@code minecraft:main} 为输出目标的 pass 在绘制前会清空该目标（链尾的 {@code blit → main} 即在
     * 此列），把主目标深度写成常量。曳光光照的 pass 正好是这样一个 pass，而它自己的快照取在同一方法内的
     * pass 之前，所以折射链的 {@code captureWorldDepth} 必须排在曳光 {@code render()} 之前——两份快照拿到的
     * 是同一时刻、同一份未被破坏的世界深度。</p>
     */
    @SubscribeEvent
    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // 1) 折射链的世界深度快照
        INSTANCE.blastDistortion.captureWorldDepth(ExplosionManager.get(mc.level).visuals());
        // 2) 曳光光照：内部先取自己的深度快照，再提交光照 pass
        INSTANCE.tracerLight.render(
                event.getModelViewMatrix(),
                event.getProjectionMatrix(),
                event.getCamera().getPosition(),
                mc.level,
                event.getPartialTick().getGameTimeDeltaPartialTick(true));
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
        tracerLight.dispose();
    }
}
