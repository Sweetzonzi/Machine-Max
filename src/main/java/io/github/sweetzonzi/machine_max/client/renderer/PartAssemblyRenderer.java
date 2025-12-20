package io.github.sweetzonzi.machine_max.client.renderer;

import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.mesh.BoxShapeMesh;
import cn.solarmoon.spark_core.util.SparkMathKt;
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.client.renderable.ModelAnimatable;
import io.github.sweetzonzi.machine_max.common.item.prop.PartItem;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.visual.AnimatableParams;
import io.github.sweetzonzi.machine_max.common.visual.RenderableBoundingBox;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.*;
import java.util.Iterator;
import java.util.Map;

public class PartAssemblyRenderer extends VisualEffectRenderer {

    private Player player;
    private ModelAnimatable partToPlace = null;
    private VehicleCore vehicleCore;

    @Override
    public void tick() {
        player = Minecraft.getInstance().player;
        if (player == null) return;
        if (player.getMainHandItem().getItem() instanceof PartItem) {
            ItemStack partItem = player.getMainHandItem();
            PartType partType;
            partType = PartItem.getPartType(partItem, player.level());
            if (partType == null) return;
            String variantName = PartItem.getPartAssemblyInfo(partItem, partType).getVariant();
            VariantAttr variantAttr = partType.getVariant(variantName);
            ResourceLocation model = variantAttr.getModel("default");
            ResourceLocation texture = variantAttr.getTextures("default").getFirst();
            ResourceLocation animation = variantAttr.getAnimation("default");
            if (VisualEffectHelper.partToPlace == null || VisualEffectHelper.partToPlace.getModelIndex().getLocation() != model) {
                VisualEffectHelper.partToPlace = new AnimatableParams(new ModelIndex("part", model), animation, texture);
                VisualEffectHelper.partToPlace.setTransparency(64);
                VisualEffectHelper.partToPlace.setTransform(new Transform(
                        PhysicsHelperKt.toBVector3f(player.level().clip(new ClipContext(
                                player.getEyePosition(),
                                player.getEyePosition().add(player.getViewVector(1).scale(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation()),
                        Quaternion.IDENTITY
                ));
                this.partToPlace = null; // 清空原有已有动画体
            }
        } else {
            this.partToPlace = null;
            VisualEffectHelper.partToPlace = null;
        }
    }

    @Override
    public void physTick(@NotNull PhysicsLevel physicsLevel) {
    }

    @Override
    public void render(@NotNull Minecraft minecraft, @NotNull Vec3 camPos, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, float partialTick) {
        renderPartToAssembly(camPos, poseStack, bufferSource, partialTick);
        renderBoundingBoxes(camPos, poseStack, bufferSource, partialTick);
    }

    public void renderPartToAssembly(Vec3 camPos, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        if (player == null) return;
        if (player.getMainHandItem().getItem() instanceof PartItem) {
            ItemStack partItem = player.getMainHandItem();
            PartType partType = PartItem.getPartType(partItem, player.level());
            if (partType == null) return;
            String variant = PartItem.getPartAssemblyInfo(partItem, partType).getVariant();
            renderAttachPoints(partType, variant, camPos, poseStack, bufferSource, partialTick);
            renderPart(camPos, poseStack, bufferSource, partialTick);
        } else {
            this.partToPlace = null;
            VisualEffectHelper.partToPlace = null;
        }
    }

    public void renderPart(Vec3 camPos, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        if (player == null) return;
        if (partToPlace != null) {
            poseStack.pushPose();
            poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
            partToPlace.render(poseStack, (MultiBufferSource.BufferSource) bufferSource, partialTick);
            poseStack.popPose();
        } else if (VisualEffectHelper.partToPlace != null) {
            partToPlace = new ModelAnimatable(VisualEffectHelper.partToPlace);
        }
    }

    public void renderAttachPoints(PartType partType, String variant, Vec3 camPos, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        if (player == null) return;
        Iterator<Map.Entry<AbstractConnector, PhysicsRigidBody>> iterator = VisualEffectHelper.attachPoints.entrySet().iterator();
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

    private void renderBoundingBoxes(Vec3 camPos, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        if (player == null) return;
        RenderableBoundingBox boundingBox = VisualEffectHelper.boundingBox;
        if (boundingBox != null) {
            renderBoundingBox(boundingBox, camPos, poseStack, bufferSource, partialTick);
        }
    }

    private void renderBoundingBox(RenderableBoundingBox boundingBox, Vec3 camPos, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        poseStack.pushPose();//开始渲染
        Matrix4f transform = boundingBox.getTransformMatrix(partialTick);
        BoxShapeMesh mesh = new BoxShapeMesh();
        mesh.update(new BoxCollisionShape(boundingBox.getXExtent(), boundingBox.getYExtent(), boundingBox.getZExtent()));
        renderBox(mesh, transform, camPos, boundingBox.getColor(), poseStack, bufferSource);
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
            Matrix4f transform = SparkMathKt.toMatrix4f(SparkMathKt.lerp(
                    PhysicsBodyExtensionKt.stateOf(body).getLastTransform(),
                    PhysicsBodyExtensionKt.stateOf(body).getTransform(),
                    partialTick).toTransformMatrix());
            renderBox(mesh, transform, camPos, color, poseStack, bufferSource);
            poseStack.popPose();//结束渲染
        }
    }

    private void renderBox(
            BoxShapeMesh mesh,
            Matrix4f transform,//形状的变换矩阵，需要经过插值计算
            Vec3 camPos,
            Color color,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        poseStack.pushPose();//开始渲染
        var buffer = bufferSource.getBuffer(RenderType.lines());
        var edges = mesh.getEdgesOrder();
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
