package io.github.sweetzonzi.machine_max.client.render.renderer;

import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sweetzonzi.machine_max.client.render.MMRenderTypes;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.LightingSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.LightingSubsystem;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.Iterator;

public class LightSourceRenderer extends VisualEffectRenderer {
    private static final int BEAM_SEGMENTS = 24;

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        VisualEffectHelper.lightingSubsystems.removeIf(subsystem -> shouldRemove(subsystem, minecraft));
    }

    @Override
    public void physTick(@NotNull PhysicsLevel physicsLevel) {
    }

    @Override
    public void render(@NotNull Minecraft minecraft, @NotNull Vec3 camPos, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, float partialTick) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.dragonRays());
        Iterator<LightingSubsystem> iterator = VisualEffectHelper.lightingSubsystems.iterator();
        while (iterator.hasNext()) {
            LightingSubsystem subsystem = iterator.next();
            if (shouldRemove(subsystem, minecraft)) {
                iterator.remove();
                continue;
            }
            if (!isRenderable(subsystem, minecraft)) continue;
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
