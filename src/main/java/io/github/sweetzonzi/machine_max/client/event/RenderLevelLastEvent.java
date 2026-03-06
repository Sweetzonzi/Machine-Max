package io.github.sweetzonzi.machine_max.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import lombok.Getter;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.neoforged.bus.api.Event;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

/**
 * 在 GameRenderer#renderLevel 的最终阶段触发的事件。
 * <p>
 * 触发时机：
 * - 所有世界渲染完成
 * - RenderLevelStageEvent.AFTER_LEVEL 之后
 * - 手部渲染（若存在）之后
 * - profiler.pop() 调用之前
 * <p>
 * 特点：
 * - 不受第一/第三人称影响
 * - 不受 renderHand 条件影响
 * - 适合做真正的“最终叠加渲染”
 */
@Getter
public class RenderLevelLastEvent extends Event {

    private final LevelRenderer levelRenderer;
    private final PoseStack poseStack;
    private final Matrix4f modelViewMatrix;
    private final Matrix4f projectionMatrix;
    private final int renderTick;
    private final DeltaTracker partialTick;
    private final Camera camera;
    private final Frustum frustum;

    public RenderLevelLastEvent(
            LevelRenderer levelRenderer,
            @Nullable PoseStack poseStack,
            Matrix4f modelViewMatrix,
            Matrix4f projectionMatrix,
            int renderTick,
            DeltaTracker partialTick,
            Camera camera,
            Frustum frustum
    ) {
        this.levelRenderer = levelRenderer;
        this.poseStack = poseStack != null ? poseStack : new PoseStack();
        this.modelViewMatrix = modelViewMatrix;
        this.projectionMatrix = projectionMatrix;
        this.renderTick = renderTick;
        this.partialTick = partialTick;
        this.camera = camera;
        this.frustum = frustum;
    }

}
