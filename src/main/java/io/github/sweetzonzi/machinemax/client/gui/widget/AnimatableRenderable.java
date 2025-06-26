package io.github.sweetzonzi.machinemax.client.gui.widget;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.AnimController;
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.molang.core.storage.IForeignVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.IScopedVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.ITempVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.VariableStorage;
import cn.solarmoon.spark_core.sync.SyncData;
import cn.solarmoon.spark_core.sync.SyncerType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machinemax.MachineMax;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Brightness;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.checkerframework.checker.units.qual.A;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.awt.*;

@Getter
@OnlyIn(Dist.CLIENT)
public class AnimatableRenderable implements Renderable, IAnimatable<AnimatableRenderable> {
    private final Minecraft minecraft = Minecraft.getInstance();
    private final int x;
    private final int y;
    private final int z;
    private final boolean perspective;
    @Setter
    private Color color = Color.WHITE;
    @Setter
    private ModelIndex modelIndex;
    private BoneGroup boneGroup;
    private final AnimController animController = new AnimController(this);
    private final ITempVariableStorage tempStorage = new VariableStorage();
    private final IScopedVariableStorage scopedStorage = new VariableStorage();
    private final IForeignVariableStorage foreignStorage = new VariableStorage();
    private Matrix4f projectionMatrix;
    private static final Matrix4f VIEW_MATRIX = new Matrix4f().setLookAt(
            0, 0, 10,  // 摄像机位置 (屏幕前方10单位)
            0, 0, 0,   // 观察点 (屏幕中心)
            0, 1, 0    // 上方向
    );

    public AnimatableRenderable(ModelIndex modelIndex, int x, int y, int z, boolean perspective) {
        this.modelIndex = modelIndex;
        this.boneGroup = new BoneGroup(this);
        this.x = x;
        this.y = y;
        this.z = z;
        this.perspective = perspective;
    }

    @Override
    public final void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        MultiBufferSource.BufferSource bufferSource = guiGraphics.bufferSource();
        PoseStack poseStack = guiGraphics.pose();
        Lighting.setupForEntityInInventory();
        if (perspective) {
            // ===== 透视投影块 =====
            // 备份渲染设置
            RenderSystem.backupProjectionMatrix();
            Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
            Matrix4f modelViewMatrix = RenderSystem.getModelViewMatrix();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            // 设置投影矩阵和ModelView矩阵
            double fov = 90;
            this.projectionMatrix = new Matrix4f().setPerspective(
                    (float) (fov * Math.PI / 180),
                    (float) this.minecraft.getWindow().getWidth() / (float) this.minecraft.getWindow().getHeight(),
                    0.1F, 1000);
            RenderSystem.setProjectionMatrix(this.projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.set(VIEW_MATRIX);
            RenderSystem.applyModelViewMatrix();
            // 执行渲染
            poseStack.pushPose();
            applyTransformPerspective(poseStack, mouseX, mouseY);
            renderModel(guiGraphics, poseStack, bufferSource, partialTick);
            renderTexts(guiGraphics, poseStack, bufferSource, partialTick);
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
            applyTransformOrthogonal(poseStack, mouseX, mouseY);
            renderModel(guiGraphics, poseStack, bufferSource, partialTick);
            renderTexts(guiGraphics, poseStack, bufferSource, partialTick);
            poseStack.popPose();
        }
        Lighting.setupFor3DItems();
    }

    protected void applyTransformPerspective(PoseStack poseStack, int mouseX, int mouseY) {
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.XP.rotationDegrees(180));
        poseStack.mulPose(Axis.YP.rotationDegrees(mouseX%360));
    }

    protected void applyTransformOrthogonal(PoseStack poseStack, int mouseX, int mouseY) {
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(mouseX%360));
    }

    private void renderModel(GuiGraphics guiGraphics, PoseStack poseStack,
                             MultiBufferSource.BufferSource bufferSource, float partialTick) {
        poseStack.pushPose();
        poseStack.scale(20, 20, 20);
        ModelRenderHelperKt.render(
                getModel(),
                getBones(),
                poseStack.last().pose(),
                poseStack.last().normal(),
                bufferSource.getBuffer(RenderType.entityTranslucent(modelIndex.getTextureLocation())),
                Brightness.FULL_BRIGHT.pack(),
                OverlayTexture.NO_OVERLAY,
                color.getRGB(),
                partialTick,
                true
        );
        poseStack.popPose();
    }

    private void renderTexts(GuiGraphics guiGraphics, PoseStack poseStack,
                             MultiBufferSource.BufferSource bufferSource, float partialTick) {
        poseStack.pushPose();
        poseStack.translate(0, 0, 0);
        poseStack.scale(0.5f, 0.5f, 0.5f);
        Minecraft.getInstance().font.drawInBatch(perspective ? "Perspective" : "Orthogonal",
                0, 0,
                color.getRGB(),
                false,
                poseStack.last().pose(),
                bufferSource,
                Font.DisplayMode.NORMAL,
                new Color(0, 0, 0, 0).getRGB(),
                Brightness.FULL_BRIGHT.pack());
        poseStack.popPose();
    }

    public void animTick() {
        getAnimController().tick();
    }

    public void physicsTick() {
        getAnimController().physTick();
    }

    @Override
    public AnimatableRenderable getAnimatable() {
        return this;
    }

    @Override
    public Level getAnimLevel() {
        if (Minecraft.getInstance().level != null) {
            return Minecraft.getInstance().level;
        } else return null;
    }

    @NotNull
    @Override
    public AnimController getAnimController() {
        return animController;
    }

    @NotNull
    @Override
    public ModelIndex getModelIndex() {
        return modelIndex;
    }

    @Override
    public void setBones(@NotNull BoneGroup boneGroup) {
        this.boneGroup = boneGroup;
    }

    @NotNull
    @Override
    public BoneGroup getBones() {
        return boneGroup;
    }

    @NotNull
    @Override
    public Vec3 getWorldPosition(float v) {
        return new Vec3(x, y, 0);
    }

    @Override
    public float getRootYRot(float v) {
        return 0;
    }

    @NotNull
    @Override
    public SyncerType getSyncerType() {
        return null;
    }

    @NotNull
    @Override
    public SyncData getSyncData() {
        return null;
    }

}
