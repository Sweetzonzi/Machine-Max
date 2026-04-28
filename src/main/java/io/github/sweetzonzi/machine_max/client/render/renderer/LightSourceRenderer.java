package io.github.sweetzonzi.machine_max.client.render.renderer;

import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.LightingSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.LightingSubsystem;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.awt.Color;
import java.util.Iterator;

/**
 * 车辆灯光的体积光渲染器。
 * <p>监听 NeoForge 的世界渲染阶段，在 AFTER_LEVEL 末尾绘制体积光。
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public class LightSourceRenderer {
    private static final int BEAM_SEGMENTS = 24;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        VisualEffectHelper.lightingSubsystems.removeIf(subsystem -> shouldRemove(subsystem, minecraft));
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // AFTER_LEVEL 事件传入的是空 PoseStack；相机旋转在 modelViewMatrix 中，需要手动乘回去。
        // 顶点坐标仍然在下方按 worldPos - camPos 生成，因此这里刚好补上“相机相对坐标 -> 视图空间”的变换。
        poseStack.mulPose(event.getModelViewMatrix());
        try {
            render(
                    minecraft,
                    event.getCamera().getPosition(),
                    poseStack,
                    bufferSource,
                    event.getPartialTick().getGameTimeDeltaPartialTick(true)
            );
            // 只提交本类写入的 dragonRays，避免把同一个 BufferSource 中其他阶段的批次一并冲掉。
            bufferSource.endBatch(RenderType.dragonRays());
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * 遍历当前活跃灯光系统并写入体积光三角形。
     */
    private static void render(Minecraft minecraft, Vec3 camPos, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.dragonRays());
        Iterator<LightingSubsystem> iterator = VisualEffectHelper.lightingSubsystems.iterator();
        while (iterator.hasNext()) {
            LightingSubsystem subsystem = iterator.next();
            if (shouldRemove(subsystem, minecraft)) {
                iterator.remove();
                continue;
            }
            if (!isRenderable(subsystem, minecraft)) continue; // 跳过未开启的灯光子系统
            SubPart subPart = subsystem.getSubPart();
            String locatorName = subsystem.attr.getLightLocator();
            if (!subPart.attr.getLocatorTransforms().containsKey(locatorName)) continue;
            Transform transform = subPart.getLerpedLocatorWorldTransform(locatorName, partialTick);
            LightingSubsystemStaticAttr attr = subsystem.attr.getStaticAttribute();
            if (attr.getRange() <= 0f || attr.getIntensity() <= 0f) continue;
            Vector3f toCamera = transform.getTranslation().subtract(new Vector3f((float) camPos.x, (float) camPos.y, (float) camPos.z));
            if (toCamera.lengthSquared() > getMaxRenderDistanceSqr(minecraft, attr.getRange())) {
                continue;
            }
            if (attr.getLightType() == LightingSubsystemStaticAttr.LightType.POINT) {
                renderPointLight(buffer, poseStack, camPos, transform, attr);
            } else {
                renderBeamLight(buffer, poseStack, camPos, transform, attr);
            }
        }
    }

    /**
     * 失效光源会在 tick 和 render 两处清理：tick 负责常规维护，render 负责处理两帧之间刚失效的对象。
     */
    private static boolean shouldRemove(LightingSubsystem subsystem, Minecraft minecraft) {
        if (subsystem == null) return true;
        try {
            SubPart subPart = subsystem.getSubPart();
            if (subPart == null) return true;
            return minecraft.level != null && subPart.isRemoved();
        } catch (Exception ignored) {
            return true;
        }
    }

    private static boolean isRenderable(LightingSubsystem subsystem, Minecraft minecraft) {
        if (subsystem == null || minecraft.level == null) return false;
        try {
            SubPart subPart = subsystem.getSubPart();
            return subPart != null
                    && subPart.getLevel() == minecraft.level
                    && subPart.body != null
                    && subPart.body.isInWorld()
                    && subsystem.isActive();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static double getMaxRenderDistanceSqr(Minecraft minecraft, float range) {
        float renderDistance = minecraft.gameRenderer.getRenderDistance() + range;
        return renderDistance * renderDistance;
    }

    /**
     * 聚光灯使用一组从灯源到远端圆环的三角形模拟光锥。
     */
    private static void renderBeamLight(VertexConsumer buffer, PoseStack poseStack, Vec3 camPos, Transform transform, LightingSubsystemStaticAttr attr) {
        Vector3f origin = transform.getTranslation();
        Vector3f forward = transform.getRotation().toRotationMatrix().mult(new Vector3f(0f, 0f, -1f), new Vector3f()).normalize();
        Vector3f right = transform.getRotation().toRotationMatrix().mult(new Vector3f(1f, 0f, 0f), new Vector3f()).normalize();
        Vector3f up = transform.getRotation().toRotationMatrix().mult(new Vector3f(0f, 1f, 0f), new Vector3f()).normalize();
        float radius = (float) Math.tan(Math.toRadians(attr.getBeamAngle() * 0.5f)) * attr.getRange();
        Vector3f endCenter = origin.add(forward.mult(attr.getRange()));

        for (int i = 0; i < BEAM_SEGMENTS; i++) {
            double angleA = Math.TAU * i / BEAM_SEGMENTS;
            double angleB = Math.TAU * (i + 1) / BEAM_SEGMENTS;
            Vector3f pointA = ringPoint(endCenter, right, up, radius, angleA);
            Vector3f pointB = ringPoint(endCenter, right, up, radius, angleB);
            addTriangle(buffer, poseStack, camPos, attr, origin, 0.28f, pointA, 0f, pointB, 0f);
        }
    }

    /**
     * 点光源渲染为始终朝向相机的径向光晕。
     */
    private static void renderPointLight(VertexConsumer buffer, PoseStack poseStack, Vec3 camPos, Transform transform, LightingSubsystemStaticAttr attr) {
        Vector3f origin = transform.getTranslation();
        Vector3f normal = new Vector3f((float) camPos.x - origin.x, (float) camPos.y - origin.y, (float) camPos.z - origin.z);
        if (normal.lengthSquared() < 0.0001f) {
            normal = new Vector3f(0f, 0f, 1f);
        } else {
            normal = normal.normalize();
        }
        Vector3f worldUp = Math.abs(normal.y) > 0.95f ? new Vector3f(1f, 0f, 0f) : new Vector3f(0f, 1f, 0f);
        Vector3f right = normal.cross(worldUp, new Vector3f()).normalize();
        Vector3f up = right.cross(normal, new Vector3f()).normalize();
        float radius = attr.getRange();

        for (int i = 0; i < BEAM_SEGMENTS; i++) {
            double angleA = Math.TAU * i / BEAM_SEGMENTS;
            double angleB = Math.TAU * (i + 1) / BEAM_SEGMENTS;
            Vector3f pointA = ringPoint(origin, right, up, radius, angleA);
            Vector3f pointB = ringPoint(origin, right, up, radius, angleB);
            addTriangle(buffer, poseStack, camPos, attr, origin, 0.2f, pointA, 0f, pointB, 0f);
        }
    }

    private static Vector3f ringPoint(Vector3f center, Vector3f right, Vector3f up, float radius, double angle) {
        return center
                .add(right.mult((float) Math.cos(angle) * radius))
                .add(up.mult((float) Math.sin(angle) * radius));
    }

    private static void addTriangle(
            VertexConsumer buffer,
            PoseStack poseStack,
            Vec3 camPos,
            LightingSubsystemStaticAttr attr,
            Vector3f a,
            float alphaA,
            Vector3f b,
            float alphaB,
            Vector3f c,
            float alphaC
    ) {
        addVertex(buffer, poseStack, a, camPos, attr.getColor(), attr.getIntensity(), alphaA);
        addVertex(buffer, poseStack, b, camPos, attr.getColor(), attr.getIntensity(), alphaB);
        addVertex(buffer, poseStack, c, camPos, attr.getColor(), attr.getIntensity(), alphaC);
        addVertex(buffer, poseStack, c, camPos, attr.getColor(), attr.getIntensity(), alphaC);
        addVertex(buffer, poseStack, b, camPos, attr.getColor(), attr.getIntensity(), alphaB);
        addVertex(buffer, poseStack, a, camPos, attr.getColor(), attr.getIntensity(), alphaA);
    }

    private static void addVertex(VertexConsumer buffer, PoseStack poseStack, Vector3f worldPos, Vec3 camPos, Color color, float intensity, float alphaFactor) {
        int alpha = Math.clamp((int) (255f * intensity * alphaFactor), 0, 255);
        buffer.addVertex(
                        poseStack.last().pose(),
                        worldPos.x - (float) camPos.x,
                        worldPos.y - (float) camPos.y,
                        worldPos.z - (float) camPos.z
                )
                .setColor(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
