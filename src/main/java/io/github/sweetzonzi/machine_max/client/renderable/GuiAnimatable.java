package io.github.sweetzonzi.machine_max.client.renderable;

import cn.solarmoon.spark_core.SparkCore;
import cn.solarmoon.spark_core.animation.anim.AnimInstance;
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex;
import cn.solarmoon.spark_core.animation.model.ModelInstance;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.js.molang.JSMolangValue;
import cn.solarmoon.spark_core.js.molang.JSMolangValueKt;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machine_max.common.visual.AnimatableParams;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Brightness;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@OnlyIn(Dist.CLIENT)
public class GuiAnimatable extends ModelAnimatable implements Renderable {
    private final Minecraft minecraft = Minecraft.getInstance();
    private final boolean perspective;//是否采用透视投影
    private Matrix4f projectionMatrix;
    private static final Matrix4f VIEW_MATRIX = new Matrix4f().setLookAt(
            0, 0, 0,  // 摄像机位置 (屏幕前方0单位)
            0, 0, -0.01f,   // 观察点 (屏幕中心)
            0, 1, 0    // 上方向
    );

    public GuiAnimatable(AnimatableParams attr) {
        super(attr);
        this.perspective = attr.perspective;
    }

    @Override
    public final void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        MultiBufferSource.BufferSource bufferSource = guiGraphics.bufferSource();
        PoseStack poseStack = guiGraphics.pose();
        Lighting.setupForEntityInInventory(params.getQuaternion(partialTick));
        if (params.enableScissor) {//开始裁剪
            int centerX = guiGraphics.guiWidth() / 2 + params.scissorX;
            int centerY = guiGraphics.guiHeight() / 2 + params.scissorY;
            guiGraphics.enableScissor(
                    centerX - params.scissorWidth / 2,
                    centerY - params.scissorHeight / 2,
                    centerX + params.scissorWidth / 2,
                    centerY + params.scissorHeight / 2
            );
        }
        if (perspective) {
            // ===== 透视投影块 =====
            // 备份渲染设置
            RenderSystem.backupProjectionMatrix();
            Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
            Matrix4f modelViewMatrix = RenderSystem.getModelViewMatrix();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            // 设置投影矩阵和ModelView矩阵
            double fov = this.minecraft.options.fov().get();
//            double fov = 80;
            this.projectionMatrix = new Matrix4f().setPerspective(
                    (float) (fov * Math.PI / 180),
                    (float) this.minecraft.getWindow().getWidth() / (float) this.minecraft.getWindow().getHeight(),
                    0.1F, 1000);
            RenderSystem.setProjectionMatrix(this.projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.set(VIEW_MATRIX);
            RenderSystem.applyModelViewMatrix();
            // 执行渲染
            poseStack.pushPose();
            poseStack.setIdentity();
            applyTransformPerspective(poseStack, mouseX, mouseY, partialTick);
            poseStack.pushPose();
            poseStack.mulPose(params.getQuaternion(partialTick));
            Vector3f scale = params.getScale(partialTick);
            poseStack.scale(scale.x, scale.y, scale.z);
            renderModel(poseStack, bufferSource, partialTick);
            renderTexts(poseStack, bufferSource, partialTick);
            poseStack.popPose();
            poseStack.popPose();
            // 还原渲染设置
            bufferSource.endBatch();//使用重设的投影矩阵和ModelView矩阵提交渲染
            modelViewStack.set(modelViewMatrix);
            RenderSystem.applyModelViewMatrix();//还原ModelView矩阵
            RenderSystem.disableDepthTest();
            RenderSystem.restoreProjectionMatrix();//还原原投影矩阵
        } else {
            // ===== 正交投影块 =====
            poseStack.pushPose();
            applyTransformOrthogonal(poseStack, mouseX, mouseY, guiGraphics.guiWidth(), guiGraphics.guiHeight(), partialTick);
            poseStack.pushPose();
            poseStack.mulPose(params.getQuaternion(partialTick));
            Vector3f scale = params.getScale(partialTick);
            poseStack.scale(scale.x, scale.y, scale.z);
            renderModel(poseStack, bufferSource, partialTick);
            renderTexts(poseStack, bufferSource, partialTick);
            poseStack.popPose();
            poseStack.popPose();
        }
        if (params.enableScissor) guiGraphics.disableScissor();//结束裁剪
        Lighting.setupFor3DItems();
    }

    protected void applyTransformPerspective(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        Vector3f offset = params.getOffset(partialTick);
        poseStack.translate(offset.x, -offset.y, offset.z);
    }

    protected void applyTransformOrthogonal(PoseStack poseStack, int mouseX, int mouseY, int width, int height, float partialTick) {
        poseStack.translate((float) width / 2, (float) height / 2, 0);
        Vector3f offset = params.getOffset(partialTick);
        poseStack.translate(offset.x, offset.y, offset.z);
    }

    @Override
    public void renderModel(PoseStack poseStack,
                            MultiBufferSource.BufferSource bufferSource, float partialTick) {
        ModelInstance modelInstance = getModelController().getModel();
        if (modelInstance == null) return;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        if (!perspective) poseStack.mulPose(Axis.XP.rotationDegrees(180));
        ModelRenderHelperKt.render(
                getModelController().getOriginModel(),
                modelInstance.getPose(),
                poseStack.last().pose(),
                poseStack.last().normal(),
                bufferSource.getBuffer(RenderType.entityTranslucent(getModelController().getTextureLocation())),
                Brightness.FULL_BRIGHT.pack(),
                OverlayTexture.NO_OVERLAY,
                new Color(params.color.getX(), params.color.getY(), params.color.getZ(), params.transparency).getRGB(),
                partialTick,
                true
        );
        poseStack.popPose();
    }

    @Override
    protected void renderTexts(PoseStack poseStack,
                            MultiBufferSource.BufferSource bufferSource, float partialTick) {
        ModelInstance model = getModelController().getModel();
        if (model == null) return;
        poseStack.pushPose();
        if (perspective) {
            poseStack.mulPose(Axis.XP.rotationDegrees(180));//透视投影需要翻转文字
        }
        super.renderTexts(poseStack, bufferSource, partialTick);
        poseStack.popPose();
    }
}
