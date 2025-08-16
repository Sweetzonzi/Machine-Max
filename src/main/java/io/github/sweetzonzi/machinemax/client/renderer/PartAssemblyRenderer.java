package io.github.sweetzonzi.machinemax.client.renderer;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.SparkMathKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.mesh.BoxShapeMesh;
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Transform;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.client.renderable.ModelAnimatable;
import io.github.sweetzonzi.machinemax.client.renderable.VehicleAnimatable;
import io.github.sweetzonzi.machinemax.common.item.prop.PartItem;
import io.github.sweetzonzi.machinemax.common.vehicle.PartType;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machinemax.common.visual.AnimatableParams;
import io.github.sweetzonzi.machinemax.common.visual.RenderableBoundingBox;
import io.github.sweetzonzi.machinemax.common.visual.VisualEffectHelper;
import io.github.sweetzonzi.machinemax.external.MMDynamicRes;
import io.github.sweetzonzi.machinemax.mixin_interface.IEntityMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
    private VehicleAnimatable vehicle;
    private VehicleCore vehicleCore;

    @Override
    public void tick() {
        player = Minecraft.getInstance().player;
        if (vehicle != null) {
            vehicle.setTransform(new Transform().setTranslation(PhysicsHelperKt.toBVector3f(vehicleCore.getPosition())));
        }
    }

    @Override
    public void physTick(@NotNull PhysicsLevel physicsLevel) {
    }

    @Override
    public void render(@NotNull Minecraft minecraft, @NotNull Vec3 camPos, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, float partialTick) {
        renderPartToAssembly(camPos, poseStack, bufferSource, partialTick);
        renderBoundingBoxes(camPos, poseStack, bufferSource, partialTick);
        renderAnimatable(camPos, poseStack, bufferSource, partialTick);
    }

    public void renderAnimatable(Vec3 camPos, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        if (player == null) return;
        if (partToPlace != null) {
            poseStack.pushPose();
            poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
            partToPlace.render(poseStack, (MultiBufferSource.BufferSource) bufferSource, partialTick);
            poseStack.popPose();
        } else if (VisualEffectHelper.partToPlace != null) {
            partToPlace = new ModelAnimatable(VisualEffectHelper.partToPlace);
        }
//        if (vehicle != null) {
//            poseStack.pushPose();
//            poseStack.translate(-camPos.x, -camPos.y + 3, -camPos.z);
//            vehicle.render(poseStack, (MultiBufferSource.BufferSource) bufferSource, partialTick);
//            poseStack.popPose();
//        } else if (((IEntityMixin) player).machine_Max$getRidingSubsystem() instanceof SeatSubsystem seat) {
//            vehicleCore = seat.getPart().vehicle;
//            vehicle = new VehicleAnimatable(vehicleCore);
//        }
    }

    public void renderPartToAssembly(Vec3 camPos, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        if (player == null) return;
        if (player.getMainHandItem().getItem() instanceof PartItem) {
            ItemStack partItem = player.getMainHandItem();
            PartType partType = PartItem.getPartType(partItem, player.level());
            String variant = PartItem.getPartAssemblyInfo(partItem, player.level()).variant();
            ResourceLocation model = partType.variants.get(variant);
            ResourceLocation texture = partType.textures.getFirst();
            ResourceLocation animation = partType.getAnimation();
            if (VisualEffectHelper.partToPlace == null || VisualEffectHelper.partToPlace.getModelIndex().getModelPath() != model) {
                VisualEffectHelper.partToPlace = new AnimatableParams(model, animation, texture);
                VisualEffectHelper.partToPlace.setTransparency(64);
            }
            renderAttachPoints(partType, variant, camPos, poseStack, bufferSource, partialTick);
        } else {
            this.partToPlace = null;
            VisualEffectHelper.partToPlace = null;
        }
    }

    public void renderInteractBoxes() {
        //TODO: 实现交互判定区的渲染
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
            Matrix4f transform = SparkMathKt.toMatrix4f(SparkMathKt.lerp(body.lastTickTransform, body.tickTransform, partialTick).toTransformMatrix());
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
