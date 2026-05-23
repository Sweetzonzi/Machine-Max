package io.github.sweetzonzi.machine_max.client.render.renderer;

import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.mesh.BoxShapeMesh;
import cn.solarmoon.spark_core.util.SparkMathKt;
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import io.github.sweetzonzi.machine_max.MachineMax;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.common.item.prop.AssemblyItem;
import io.github.sweetzonzi.machine_max.common.item.prop.VehicleBlueprintItem;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.visual.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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

    @Override
    public void tick() {
        player = Minecraft.getInstance().player;
        if (player == null) return;
        // 清理无效的包围盒和投影
        Item rightItem = player.getMainHandItem().getItem();
        Item leftItem = player.getOffhandItem().getItem();
        if (rightItem instanceof VehicleBlueprintItem
                || leftItem instanceof VehicleBlueprintItem
                || rightItem instanceof AssemblyItem
                || leftItem instanceof AssemblyItem) {
        } else {
            VisualEffectHelper.boundingBox = null;
            VisualEffectHelper.vehicleProjection = null;
        }

        var cache = player.getData(MMAttachments.getVEHICLE_ASSEMBLY());
        if (cache.getPartType() instanceof PartType type) {
            VariantAttr variantAttr = cache.getVariant();
            if (variantAttr == null) {
                VisualEffectHelper.partToPlace = null;
                return;
            }
            if (VisualEffectHelper.partToPlace == null || VisualEffectHelper.partToPlace.variantAttr != variantAttr) {
                VisualEffectHelper.partToPlace = new PartAnimatable(player.level(), type, cache.getVariantName());
                VisualEffectHelper.partToPlace.setTransform(new Transform(
                        PhysicsHelperKt.toBVector3f(player.level().clip(new ClipContext(
                                player.getEyePosition(),
                                player.getEyePosition().add(player.getViewVector(1).scale(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation()),
                        Quaternion.IDENTITY
                ));
            }
        } else {
            VisualEffectHelper.partToPlace = null;
        }
    }

    @Override
    public void physTick(@NotNull PhysicsLevel physicsLevel) {
    }

    @Override
    public @NotNull RenderLevelStageEvent.Stage getRenderStage() {
        return RenderLevelStageEvent.Stage.AFTER_ENTITIES;
    }

    @Override
    public void render(@NotNull RenderLevelStageEvent event, @NotNull MultiBufferSource bufferSource, float partialTick) {
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        renderPartToAssembly(camPos, poseStack, bufferSource, partialTick);
        renderBoundingBoxes(camPos, poseStack, bufferSource, partialTick);
        renderVehicleProjection(camPos, poseStack, bufferSource, partialTick);
    }

    public void renderPartToAssembly(Vec3 camPos, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        if (player == null) return;
        var cache = player.getData(MMAttachments.getVEHICLE_ASSEMBLY());
        if (cache.getPartType() instanceof PartType partType) {
            String variant = cache.getVariantName();
            renderAttachPoints(partType, variant, camPos, poseStack, bufferSource, partialTick);
            renderPart(camPos, poseStack, bufferSource, partialTick);
        } else {
            VisualEffectHelper.partToPlace = null;
        }
    }

    public void renderPart(Vec3 camPos, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        if (player == null) return;
        if (VisualEffectHelper.partToPlace instanceof PartAnimatable part) {
            poseStack.pushPose();
            poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
            for (SubPartAnimatable subPart : part.getSubParts().values()) {
                poseStack.pushPose();
                poseStack.mulPose(subPart.getRenderWorldPositionMatrix(partialTick));
                for (OBone bone : subPart.getBones().values()) {
                    ModelRenderHelperKt.render(
                            bone,
                            subPart.getModelController().getModel().getPose(),
                            poseStack,
                            bufferSource.getBuffer(RenderType.entityTranslucent(subPart.getModelController().getTextureLocation())),
                            Brightness.FULL_BRIGHT.pack(),
                            OverlayTexture.NO_OVERLAY,
                            new Color(255, 255, 255, 64).getRGB(),
                            partialTick,
                            false
                    );
                }
                poseStack.popPose();
            }
            poseStack.popPose();
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

    /**
     * 渲染载具蓝图/装配体的3D投影预览。
     * <p>使用半透明颜色渲染，颜色取自{@link VisualEffectHelper#boundingBox}的状态（绿色可放置/红色碰撞）。</p>
     *
     * @param camPos       摄像机位置
     * @param poseStack    位姿栈
     * @param bufferSource 渲染缓冲区
     * @param partialTick  部分刻
     */
    private void renderVehicleProjection(Vec3 camPos, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        if (player == null) return;
        VehicleAnimatable vehicle = VisualEffectHelper.vehicleProjection;
        if (vehicle == null) return;
        
        // 取用AABB颜色，若AABB不存在则使用默认绿色
        Color baseColor;
        if (VisualEffectHelper.boundingBox != null) {
            baseColor = VisualEffectHelper.boundingBox.getColor();
        } else {
            baseColor = Color.GREEN;
        }
        // 转换为半透明颜色（Alpha = 64）
        Color translucentColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 64);
        
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        for (SubPartAnimatable subPart : vehicle.getSubParts().values()) {
            poseStack.pushPose();
            poseStack.mulPose(subPart.getRenderWorldPositionMatrix(partialTick));
            for (OBone bone : subPart.getBones().values()) {
                ModelRenderHelperKt.render(
                        bone,
                        subPart.getModelController().getModel().getPose(),
                        poseStack,
                        bufferSource.getBuffer(RenderType.entityTranslucent(subPart.getModelController().getTextureLocation())),
                        Brightness.FULL_BRIGHT.pack(),
                        OverlayTexture.NO_OVERLAY,
                        translucentColor.getRGB(),
                        partialTick,
                        false
                );
            }
            poseStack.popPose();
        }
        poseStack.popPose();
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
