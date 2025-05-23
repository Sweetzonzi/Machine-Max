package io.github.tt432.machinemax.client.renderer;

import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.SparkMathKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.mesh.BoxShapeMesh;
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.item.prop.MMPartItem;
import io.github.tt432.machinemax.common.vehicle.PartProjection;
import io.github.tt432.machinemax.common.vehicle.PartType;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Brightness;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.*;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PartAssemblyRenderer extends VisualEffectRenderer {

    public PartProjection partToAssembly = null;
    public ConcurrentMap<AbstractConnector, PhysicsRigidBody> attachPoints = new ConcurrentHashMap<>();
    LocalPlayer player;

    @Override
    public void tick() {
        player = Minecraft.getInstance().player;
    }

    @Override
    public void physTick(@NotNull PhysicsLevel physicsLevel) {
    }

    @Override
    public void render(@NotNull Minecraft minecraft, @NotNull Vec3 camPos, @NotNull PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, float partialTick) {
        renderPartToAssembly(camPos, poseStack, multiBufferSource, partialTick);
    }

    public void renderPartToAssembly(Vec3 camPos, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        if (player == null) return;
        if (player.getMainHandItem().getItem() instanceof MMPartItem) {
            ItemStack partItem = player.getMainHandItem();
            PartType partType = MMPartItem.getPartType(partItem, player.level());
            String variant = MMPartItem.getPartAssemblyInfo(partItem, player.level()).variant();
            if (partToAssembly == null || !partType.equals(partToAssembly.type)) {
                partToAssembly = new PartProjection(partType, player.level(), variant,
                        new Transform(
                                PhysicsHelperKt.toBVector3f(player.position()),
                                Quaternion.IDENTITY
                        ));
            }
            if (!partToAssembly.variant.equals(variant)) {
                partToAssembly.setVariant(variant);
            }
            renderPartProjection(partToAssembly, poseStack, bufferSource, partialTick);
            renderAttachPoints(partType, variant, camPos, poseStack, bufferSource, partialTick);
        }
    }

    public void renderAttachPoints(PartType partType, String variant, Vec3 camPos, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        if (player == null) return;
        Iterator<Map.Entry<AbstractConnector, PhysicsRigidBody>> iterator = attachPoints.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<AbstractConnector, PhysicsRigidBody> entry = iterator.next();
            AbstractConnector connector = entry.getKey();
            PhysicsRigidBody body = entry.getValue();
            if (body == null || !body.isInWorld()) {
                iterator.remove();//保险措施，清理无效的物理体
            } else if (!connector.hasPart() && connector.conditionCheck(partType, variant)) {
                renderShape(body, camPos, Color.GREEN, poseStack, bufferSource, partialTick);
            } else if (!connector.hasPart() && !connector.conditionCheck(partType, variant)) {
                renderShape(body, camPos, Color.RED, poseStack, bufferSource, partialTick);
            }
        }
    }

    private void renderPartProjection(PartProjection partProjection, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        poseStack.pushPose();//开始渲染
        ModelRenderHelperKt.render(
                partProjection.getModel(),
                partProjection.getBones(),
                partProjection.getWorldPositionMatrix(partialTick),
                poseStack.last().normal(),
                bufferSource.getBuffer(RenderType.entityTranslucentEmissive(partProjection.modelIndex.getTextureLocation())),
                Brightness.FULL_BRIGHT.pack(),
                OverlayTexture.NO_OVERLAY,
                partProjection.color.getRGB(),
                partialTick);
        poseStack.popPose();//结束渲染
    }

    private void renderShape(
            PhysicsCollisionObject body,
            Vec3 camPos,
            Color color,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            float partialTick
    ) {
        if (body.getCollisionShape() instanceof BoxCollisionShape boxShape) {
            poseStack.pushPose();//开始渲染
            BoxShapeMesh mesh = new BoxShapeMesh();
            mesh.update(boxShape);
            var buffer = bufferSource.getBuffer(RenderType.lines());
            var edges = mesh.getEdgesOrder();
            Matrix4f transform = SparkMathKt.toMatrix4f(SparkMathKt.lerp(body.lastTickTransform, body.tickTransform, partialTick).toTransformMatrix());
            for (int i = 0; i < edges.length; i += 2) {
                Vector3f from = mesh.getWorldVertexPosition(edges[i], transform).sub(camPos.toVector3f(), new Vector3f());
                Vector3f to = mesh.getWorldVertexPosition(edges[i + 1], transform).sub(camPos.toVector3f(), new Vector3f());
                Vector3f normal = to.sub(from, new Vector3f()).normalize();
                int packedColor = color.getRGB();
                buffer.addVertex(poseStack.last().pose(), from.x(), from.y(), from.z()).setColor(packedColor).setNormal(poseStack.last(), normal.x(), normal.y(), normal.z());
                buffer.addVertex(poseStack.last().pose(), to.x(), to.y(), to.z()).setColor(packedColor).setNormal(poseStack.last(), normal.x(), normal.y(), normal.z());
            }
            poseStack.popPose();//结束渲染
        }
    }
}
