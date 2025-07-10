package io.github.sweetzonzi.machinemax.client.gui.renderable;

import cn.solarmoon.spark_core.SparkCore;
import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.origin.OAnimation;
import cn.solarmoon.spark_core.animation.anim.play.*;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.molang.core.storage.IForeignVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.IScopedVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.ITempVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.VariableStorage;
import cn.solarmoon.spark_core.molang.engine.runtime.ExpressionEvaluator;
import cn.solarmoon.spark_core.sync.SyncData;
import cn.solarmoon.spark_core.sync.SyncerType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machinemax.client.gui.MMGuiManager;
import kotlin.Unit;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

import java.awt.*;
import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@OnlyIn(Dist.CLIENT)
public class AnimatableRenderable implements Renderable, IAnimatable<Player> {
    private final Minecraft minecraft = Minecraft.getInstance();
    private final RenderableAttr attr;//各种属性，用于恢复默认值
    //三轴偏移
    @Setter
    private float xOffset;
    @Setter
    private float yOffset;
    private final float zOffset;
    //三轴旋转
    private final float xRot;
    private final float yRot;
    private final float zRot;
    //三轴缩放
    @Setter
    private float xScale;
    @Setter
    private float yScale;
    private final float zScale;
    @Setter
    private Color color;//颜色与透明度
    private final boolean perspective;//是否采用透视投影
    private final Map<String, RenderableAttr.TextAttr> textAttr;
    private final boolean enableScissor;
    @Setter
    private int scissorX;
    @Setter
    private int scissorY;
    @Setter
    private int scissorWidth;
    @Setter
    private int scissorHeight;
    @Setter
    private ModelIndex modelIndex;
    private BoneGroup boneGroup;
    private final AnimController animController = new AnimController(this);
    private Matrix4f projectionMatrix;
    private static final Matrix4f VIEW_MATRIX = new Matrix4f().setLookAt(
            0, 0, 0,  // 摄像机位置 (屏幕前方0单位)
            0, 0, -0.01f,   // 观察点 (屏幕中心)
            0, 1, 0    // 上方向
    );

    public AnimatableRenderable(RenderableAttr attr) {
        this.attr = attr;
        this.modelIndex = new ModelIndex(attr.model, attr.animation, attr.texture);
        this.boneGroup = new BoneGroup(this);
        this.xOffset = (float) attr.offset.x;
        this.yOffset = (float) attr.offset.y;
        this.zOffset = (float) attr.offset.z;
        this.xRot = (float) attr.rotation.x;
        this.yRot = (float) attr.rotation.y;
        this.zRot = (float) attr.rotation.z;
        this.xScale = (float) attr.scale.x;
        this.yScale = (float) attr.scale.y;
        this.zScale = (float) attr.scale.z;
        this.color = new Color(attr.color.getX(), attr.color.getY(), attr.color.getZ(), attr.transparency);
        this.perspective = attr.perspective;
        this.textAttr = attr.textAttr;
        this.enableScissor = attr.enableScissor;
        this.scissorX = attr.scissorX;
        this.scissorY = attr.scissorY;
        this.scissorWidth = attr.scissorWidth;
        this.scissorHeight = attr.scissorHeight;
        MMGuiManager.animatableWidgets.add(new WeakReference<>(this, MMGuiManager.referenceQueue));
    }

    @Override
    public final void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        MultiBufferSource.BufferSource bufferSource = guiGraphics.bufferSource();
        PoseStack poseStack = guiGraphics.pose();
        Lighting.setupForEntityInInventory(new Quaternionf().rotationZYX(
                (float) (zRot * Math.PI / 180),
                (float) (yRot * Math.PI / 180),
                (float) (xRot * Math.PI / 180)));
        if (enableScissor) {//开始裁剪
            int centerX = guiGraphics.guiWidth() / 2 + scissorX;
            int centerY = guiGraphics.guiHeight() / 2 + scissorY;
            guiGraphics.enableScissor(
                    centerX - scissorWidth / 2,
                    centerY - scissorHeight / 2,
                    centerX + scissorWidth / 2,
                    centerY + scissorHeight / 2
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
            applyTransformPerspective(poseStack, mouseX, mouseY);
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(zRot));
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
            poseStack.mulPose(Axis.XP.rotationDegrees(xRot));
            poseStack.scale(xScale, yScale, zScale);
            renderModel(guiGraphics, poseStack, bufferSource, partialTick);
            renderTexts(guiGraphics, poseStack, bufferSource, partialTick);
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
            applyTransformOrthogonal(poseStack, mouseX, mouseY, guiGraphics.guiWidth(), guiGraphics.guiHeight());
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(zRot));
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
            poseStack.mulPose(Axis.XP.rotationDegrees(xRot));
            poseStack.scale(xScale, yScale, zScale);
            renderModel(guiGraphics, poseStack, bufferSource, partialTick);
            renderTexts(guiGraphics, poseStack, bufferSource, partialTick);
            poseStack.popPose();
            poseStack.popPose();
        }
        if (enableScissor) guiGraphics.disableScissor();//结束裁剪
        Lighting.setupFor3DItems();
    }

    protected void applyTransformPerspective(PoseStack poseStack, int mouseX, int mouseY) {
        poseStack.translate(xOffset, -yOffset, zOffset);
    }

    protected void applyTransformOrthogonal(PoseStack poseStack, int mouseX, int mouseY, int width, int height) {
        poseStack.translate((float) width / 2, (float) height / 2, 0);
        poseStack.translate(xOffset, yOffset, zOffset);
    }

    private void renderModel(GuiGraphics guiGraphics, PoseStack poseStack,
                             MultiBufferSource.BufferSource bufferSource, float partialTick) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        if (!perspective) poseStack.mulPose(Axis.XP.rotationDegrees(180));
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
        poseStack.scale(-1, -1, 1);
        if (perspective) {
            poseStack.mulPose(Axis.XP.rotationDegrees(180));//透视投影需要翻转文字
        }
        //渲染所有文本
        var evaluator = ExpressionEvaluator.evaluator(getAnimatable());
        for (Map.Entry<String, RenderableAttr.TextAttr> entry : textAttr.entrySet()) {
            String locatorName = entry.getKey();
            RenderableAttr.TextAttr textAttr = entry.getValue();
            Matrix4f matrix = getSpaceBoneMatrix(locatorName, partialTick);
            var offset = getModel().getLocators().get(locatorName).getOffset().toVector3f();
            matrix.translate(offset.x, offset.y, offset.z);
            poseStack.pushPose();
            poseStack.mulPose(matrix);
            //计算molang表达式
            List<String> args = new ArrayList<>();
            int num = textAttr.significand();
            DecimalFormat df = new DecimalFormat("#"); // 初始化为不保留小数点
            if (num > 0) {
                df = new DecimalFormat("#." + "0".repeat(num)); // 如果num大于0，则保留相应数量的有效数字
            }
            for (String arg : textAttr.molangArgs()) {
                try {
                    Object value = SparkCore.PARSER.parseExpression(arg).evalUnsafe(evaluator);
                    if (value instanceof String stringValue) {
                        args.add(stringValue);
                    } else if (value instanceof Number) {
                        args.add(df.format(((Number) value).doubleValue()));
                    } else if (value instanceof Boolean) {
                        args.add(String.valueOf(value));
                    } else args.add("null");
                } catch (Exception e) {
                    args.add("MOLANG_ERROR" + e);
                }
            }
            String text;
            if (textAttr.molangArgs().isEmpty()) text = Component.translatable(textAttr.key()).getString();//无参数直接使用翻译键
                //否则用解析的molang表达式结果作为参数
            else text = Component.translatable(textAttr.key(), args.toArray(new Object[0])).getString();
            //绘制文字
            poseStack.pushPose();
            poseStack.scale(0.05f, 0.05f, (perspective ? -1 : 1) * 0.05f);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180));
            //noinspection IntegerDivisionInFloatingPointContext
            Minecraft.getInstance().font.drawInBatch(text,
                    textAttr.centered() ? (-Minecraft.getInstance().font.width(text) / 2) : 0,
                    textAttr.centered() ? (-Minecraft.getInstance().font.lineHeight / 2) : -Minecraft.getInstance().font.lineHeight,
                    textAttr.getColor(),
                    textAttr.shadow(),
                    poseStack.last().pose().scale(textAttr.scale().toVector3f()),
                    bufferSource,
                    Font.DisplayMode.NORMAL,
                    textAttr.getBackgroundColor(),
                    Brightness.FULL_BRIGHT.pack());
            poseStack.popPose();
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    public void animTick() {
        getAnimController().tick();
        var animSet = modelIndex.getAnimationSet().getAnimations();
        if (!animSet.isEmpty() && animController.getMainAnim() == null) {
            for (Map.Entry<String, OAnimation> entry : animSet.entrySet()) {
                String name = entry.getKey();
                var anim = entry.getValue();
                var animInstance = AnimInstance.create(this, name, anim, a -> Unit.INSTANCE);
                getAnimController().getBlendSpace().putIfAbsent(name, new BlendAnimation(animInstance, 1, List.of()));
                getAnimController().setAnimation(name, 0, a -> Unit.INSTANCE);
            }
        }
    }

    public void physicsTick() {
        getAnimController().physTick();
    }

    @Nullable
    @Override
    public Player getAnimatable() {
        if (Minecraft.getInstance().player instanceof Player player) return player;
        else return null;
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
        return new Vec3(xOffset, yOffset, 0);
    }

    @Override
    public float getRootYRot(float v) {
        return 0;
    }

    @NotNull
    @Override
    public SyncerType getSyncerType() {
        if (getAnimatable() instanceof IEntityAnimatable<?> entityAnimatable) return entityAnimatable.getSyncerType();
        else return null;
    }

    @NotNull
    @Override
    public SyncData getSyncData() {
        if (getAnimatable() instanceof IEntityAnimatable<?> entityAnimatable) return entityAnimatable.getSyncData();
        else return null;
    }

    @NotNull
    @Override
    public ITempVariableStorage getTempStorage() {
        if (getAnimatable() instanceof IEntityAnimatable<?> entityAnimatable) return entityAnimatable.getTempStorage();
        else return new VariableStorage();
    }

    @NotNull
    @Override
    public IScopedVariableStorage getScopedStorage() {
        if (getAnimatable() instanceof IEntityAnimatable<?> entityAnimatable)
            return entityAnimatable.getScopedStorage();
        else return new VariableStorage();
    }

    @NotNull
    @Override
    public IForeignVariableStorage getForeignStorage() {
        if (getAnimatable() instanceof IEntityAnimatable<?> entityAnimatable)
            return entityAnimatable.getForeignStorage();
        else return new VariableStorage();
    }
}
