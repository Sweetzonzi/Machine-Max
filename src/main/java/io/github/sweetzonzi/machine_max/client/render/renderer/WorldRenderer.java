package io.github.sweetzonzi.machine_max.client.render.renderer;

import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.SparkMathKt;
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.render.MMRenderTypes;
import io.github.sweetzonzi.machine_max.client.render.gui.world.WorldRenderer2DElement;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Brightness;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;


public class WorldRenderer extends VisualEffectRenderer {

    private final List<WorldRenderer2DElement> worldRenderer2DElements =  new ArrayList<>();

    public void register(WorldRenderer2DElement worldRenderer2DElement) {
        worldRenderer2DElements.add(worldRenderer2DElement);
    }

    public void unregister(WorldRenderer2DElement worldRenderer2DElement) {
        worldRenderer2DElements.remove(worldRenderer2DElement);
    }

    @Override
    public void tick() {
        // 无需 tick 逻辑
    }

    @Override
    public void physTick(@NotNull PhysicsLevel physicsLevel) {
        // 无需物理 tick 逻辑
    }

    @Override
    public @NotNull RenderLevelStageEvent.Stage getRenderStage() {
        // 必须使用含相机旋转与 -camPos 平移的早期阶段（如 AFTER_ENTITIES）：
        // AFTER_LEVEL 阶段 NeoForge dispatch 时传入 null poseStack，RenderLevelStageEvent 会新建单位矩阵，
        // 在单位矩阵上做世界坐标平移会把锚点推到视锥外导致完全不可见。
        // 显式 endBatch() 使 NO_DEPTH_TEST 在提交时即生效，锚点可穿透方块显示。
        return RenderLevelStageEvent.Stage.AFTER_ENTITIES;
    }

    @Override
    public void render(@NotNull RenderLevelStageEvent event, @NotNull MultiBufferSource bufferSource, float partialTick) {
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();

        render2d(bufferSource, poseStack, camPos, camera);
    }

    private void render2d(@NotNull MultiBufferSource bufferSource, PoseStack poseStack, Vec3 camPos, Camera camera) {
        for (WorldRenderer2DElement renderer2DElement : worldRenderer2DElements) {
            poseStack.pushPose();
            // 抵消相机平移，使世界坐标 (0,0,0) 正确映射到屏幕空间
            Vec3 position = renderer2DElement.getPosition();
            poseStack.translate(position.x + 0.5 - camPos.x, position.y + 0.5 - camPos.y, position.z + 0.5 - camPos.z);

            // 公告板：以相机 right/up 为基向量铺开四边形，使贴图始终正对镜头
            Vec3 look = SparkMathKt.toVec3(camera.getLookVector());
            Vec3 up = SparkMathKt.toVec3(camera.getUpVector());
            Vec3 right = look.cross(up);

            double scale = renderer2DElement.getScale();
            double distance = position.distanceTo(camPos);
            int th = 20;
            if (distance < th) scale = distance /(th*1.5);
            Vec3 c0 = Vec3.ZERO.add(right.scale(-scale)).add(up.scale(-scale));
            Vec3 c1 = Vec3.ZERO.add(right.scale(scale)).add(up.scale(-scale));
            Vec3 c2 = Vec3.ZERO.add(right.scale(scale)).add(up.scale(scale));
            Vec3 c3 = Vec3.ZERO.add(right.scale(-scale)).add(up.scale(scale));

            int light = Brightness.FULL_BRIGHT.pack();
            Matrix4f matrix = poseStack.last().pose();
            VertexConsumer buffer = bufferSource.getBuffer(renderer2DElement.getRenderType());
            // 4 顶点公告板四边形：UV v0 为纹理顶部（对应屏幕上方），法线取相机 look 方向使 entity 着色器给出满亮度
            buffer.addVertex(matrix, (float) c0.x, (float) c0.y, (float) c0.z).setUv(0F, 1F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setColor(1F, 1F, 1F, 1F).setNormal((float) look.x, (float) look.y, (float) look.z);
            buffer.addVertex(matrix, (float) c1.x, (float) c1.y, (float) c1.z).setUv(1F, 1F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setColor(1F, 1F, 1F, 1F).setNormal((float) look.x, (float) look.y, (float) look.z);
            buffer.addVertex(matrix, (float) c2.x, (float) c2.y, (float) c2.z).setUv(1F, 0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setColor(1F, 1F, 1F, 1F).setNormal((float) look.x, (float) look.y, (float) look.z);
            buffer.addVertex(matrix, (float) c3.x, (float) c3.y, (float) c3.z).setUv(0F, 0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setColor(1F, 1F, 1F, 1F).setNormal((float) look.x, (float) look.y, (float) look.z);

            poseStack.popPose();

            // 立即提交批次（参考 VehicleInspectorRenderer 的做法）：
            // bufferSource 的批次默认延迟到 LevelRenderer 末尾统一 flush，
            // 显式 endBatch 保证 NO_DEPTH_TEST 状态在提交时即生效，锚点绘制在所有世界几何体之上
            if (bufferSource instanceof MultiBufferSource.BufferSource source) {
                source.endBatch();
            }
        }
    }
}
