package io.github.sweetzonzi.machine_max.client.render.renderable;

import io.github.sweetzonzi.machine_max.client.render.gui.hud.molang.HudRuntimeElement;
import io.github.sweetzonzi.machine_max.client.render.gui.hud.molang.HudMolangContext;
import io.github.sweetzonzi.machine_max.client.render.gui.hud.molang.HudExpression;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.animation.anim.AnimInstance;
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex;
import cn.solarmoon.spark_core.animation.anim.origin.OAnimation;
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.ModelInstance;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.molang.MolangContextRegistry;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Transform;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machine_max.common.visual.HudElement;
import io.github.sweetzonzi.machine_max.common.visual.HudStyle;
import io.github.sweetzonzi.machine_max.common.visual.ModelHudStyle;
import io.github.sweetzonzi.machine_max.common.visual.ScopeBehavior;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Brightness;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI 可渲染的 3D 模型 HUD 元素。
 * <p>
 * 一个实例只对应一份布局条目：样式（模型/动画/纹理/染色/文本槽/裁剪）来自
 * {@link ModelHudStyle}，投放（位移/旋转/缩放/投影/炮镜行为）来自 {@link HudElement}。
 * 同一份样式在一个布局里出现多次时，每个条目各自创建一个实例。
 * <p>
 * 本元素的 {@link HudRuntimeElement} 提供按消费线程隔离的上下文。
 * 物理线程混合骨骼，客户端主线程发布姿态，渲染帧插值并求值文本。
 */
@OnlyIn(Dist.CLIENT)
public class GuiAnimatable implements HudElementInstance, ITickableRenderable, Renderable {
    /** 样式资源键，仅用于诊断。 */
    private final ResourceLocation styleId;
    private final ModelHudStyle style;
    private final HudElement element;
    private final boolean perspective;
    /** 由布局条目固定下来的投放变换；构造后不再变化。 */
    private final Transform transform;
    private final ModelController modelController;
    private final AnimController animController;
    private final HudRuntimeElement runtime;
    private final java.util.Set<String> expressionWarnings = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<OAnimation> rejectedAnimations = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private volatile boolean disposed;

    public GuiAnimatable(ResourceLocation styleId, ModelHudStyle style, HudElement element) {
        this(styleId, style, element, new HudRuntimeElement(null, element));
    }

    public GuiAnimatable(ResourceLocation styleId, ModelHudStyle style, HudElement element, HudRuntimeElement runtime) {
        this.runtime = runtime;
        if (style == null) throw new NullPointerException("ModelHudStyle 不能为空");
        if (element == null) throw new NullPointerException("HudElement 不能为空");
        runtime.setModelIndex(style.modelIndex());
        this.styleId = styleId;
        this.style = style;
        this.element = element;
        this.perspective = element.perspective();
        Vector3f rot = SparkMathKt.toRadians(element.rotation()).toVector3f();
        Quaternionf quaternionf = new Quaternionf().rotationZYX(rot.z(), rot.y(), rot.x());
        this.transform = new Transform(
                PhysicsHelperKt.toBVector3f(element.offset()),
                SparkMathKt.toBQuaternion(quaternionf),
                PhysicsHelperKt.toBVector3f(element.scale())
        );
        this.modelController = runtime.getModelController();
        this.animController = runtime.getAnimController();
        getModelController().setModel(style.modelIndex());
        getModelController().setTextureLocation(style.texture());
        create();
    }

    public ResourceLocation getStyleId() {
        return styleId;
    }

    public ModelHudStyle getStyle() {
        return style;
    }

    public Map<String, Object> getVariables() {
        return runtime.getVariables();
    }

    public HudElement getElement() {
        return element;
    }

    public boolean isPerspective() {
        return perspective;
    }

    @Override
    public void renderGui(GuiGraphics guiGraphics, float partialTick) {
        render(guiGraphics, 0, 0, partialTick);
    }

    @Override
    public void dispose() {
        disposed = true;
        ITickableRenderable.super.destroy();
    }

    public ScopeBehavior scopeBehavior() {
        return element.scopeBehavior();
    }

    public boolean ignoreZoom() {
        return element.ignoreZoom();
    }

    public Vector3f getOffset(float partialTick) {
        return SparkMathKt.toVector3f(transform.getTranslation());
    }

    public Quaternionf getQuaternion(float partialTick) {
        return SparkMathKt.toQuaternionf(transform.getRotation());
    }

    public Vector3f getScale(float partialTick) {
        return SparkMathKt.toVector3f(transform.getScale());
    }

    @Override
    public HudRuntimeElement runtime() { return runtime; }

    public HudMolangContext getMolangContext() { return runtime.getMolangContext(); }

    @Override
    public final void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        MultiBufferSource.BufferSource bufferSource = guiGraphics.bufferSource();
        PoseStack poseStack = guiGraphics.pose();
        Lighting.setupForEntityInInventory(getQuaternion(partialTick));
        HudStyle.ScissorParams scissor = style.scissor();
        if (scissor.enable()) {//开始裁剪
            int centerX = guiGraphics.guiWidth() / 2 + scissor.x();
            int centerY = guiGraphics.guiHeight() / 2 + scissor.y();
            guiGraphics.enableScissor(
                    centerX - scissor.width() / 2,
                    centerY - scissor.height() / 2,
                    centerX + scissor.width() / 2,
                    centerY + scissor.height() / 2
            );
        }
        if (perspective) {
            // ===== 透视投影块：由外部 CustomHud 统一管理投影矩阵 =====
            poseStack.pushPose();
            applyTransformPerspective(poseStack, mouseX, mouseY, partialTick);
            renderContent(poseStack, bufferSource, partialTick);
            poseStack.popPose();
        } else {
            // ===== 正交投影块 =====
            poseStack.pushPose();
            applyTransformOrthogonal(poseStack, mouseX, mouseY, guiGraphics.guiWidth(), guiGraphics.guiHeight(), partialTick);
            renderContent(poseStack, bufferSource, partialTick);
            poseStack.popPose();
        }
        if (scissor.enable()) guiGraphics.disableScissor();//结束裁剪
        Lighting.setupFor3DItems();
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTick) {
        // ITickableRenderable 接口的世界空间渲染方法（不使用投影矩阵设置）
        poseStack.pushPose();
        Vector3f offset = getOffset(partialTick);
        poseStack.translate(offset.x, offset.y, offset.z);
        renderContent(poseStack, bufferSource, partialTick);
        poseStack.popPose();
    }

    /**
     * 渲染模型和文本内容（不包含投影矩阵设置，供统一 flush 流程调用）。
     */
    public void renderContent(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTick) {
        poseStack.pushPose();
        poseStack.mulPose(getQuaternion(partialTick));
        Vector3f scale = getScale(partialTick);
        poseStack.scale(scale.x, scale.y, scale.z);
        renderModel(poseStack, bufferSource, partialTick);
        renderTexts(poseStack, bufferSource, partialTick);
        poseStack.popPose();
    }

    protected void applyTransformPerspective(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        Vector3f offset = getOffset(partialTick);
        poseStack.translate(offset.x, -offset.y, offset.z);
    }

    protected void applyTransformOrthogonal(PoseStack poseStack, int mouseX, int mouseY, int width, int height, float partialTick) {
        poseStack.translate((float) width / 2, (float) height / 2, 0);
        Vector3f offset = getOffset(partialTick);
        poseStack.translate(offset.x, offset.y, offset.z);
    }

    /**
     * 渲染 3D 模型。
     */
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
                poseStack,
                bufferSource.getBuffer(RenderType.entityTranslucent(getModelController().getTextureLocation())),
                Brightness.FULL_BRIGHT.pack(),
                OverlayTexture.NO_OVERLAY,
                style.colorArgb(),
                partialTick
        );
        poseStack.popPose();
    }

    /**
     * 渲染所有附着在骨骼上的文本标签。
     */
    protected void renderTexts(PoseStack poseStack,
                               MultiBufferSource.BufferSource bufferSource, float partialTick) {
        ModelInstance model = getModelController().getModel();
        if (model == null) return;
        poseStack.pushPose();
        poseStack.scale(-1, -1, 1);
        if (perspective) {
            poseStack.mulPose(Axis.XP.rotationDegrees(180));//透视投影需要翻转文字
        }
        //渲染所有文本
        for (Map.Entry<String, HudStyle.TextParams> entry : style.texts().entrySet()) {
            String locatorName = entry.getKey();
            HudStyle.TextParams textParams = entry.getValue();
            Matrix4f matrix = model.getPose().getSpaceBoneLocatorMatrix(locatorName, partialTick);
            poseStack.pushPose();
            poseStack.mulPose(matrix);
            //计算molang表达式
            List<String> args = new ArrayList<>();
            int num = textParams.significand();
            DecimalFormat df = new DecimalFormat("#"); // 初始化为不保留小数点
            if (num > 0) {
                df = new DecimalFormat("#." + "0".repeat(num)); // 如果num大于0，则保留相应数量的有效数字
            }
            //利用Mocha引擎解析molang表达式
            for (String arg : textParams.molangArgs()) {
                try {
                    HudMolangContext context = getMolangContext();
                    HudExpression.validate(arg, context, null);
                    Object value = MolangContextRegistry.evalAsObject(arg, context);
                    switch (value) {
                        case String s -> args.add(s);
                        case Number n -> args.add(df.format(n.doubleValue()));
                        case Boolean b -> args.add(String.valueOf(b));
                        default -> args.add("null");
                    }
                } catch (Exception e) {
                    args.add("MOLANG_ERROR");
                    if (expressionWarnings.add(locatorName + ":" + arg)) {
                        io.github.sweetzonzi.machine_max.MachineMax.LOGGER.warn(
                                "HUD 表达式失败 (style={}, locator={}, expression={}): {}", styleId, locatorName, arg, e.toString());
                    }
                }
            }
            String text;
            if (textParams.molangArgs().isEmpty())
                text = Component.translatable(textParams.key()).getString();//无参数直接使用翻译键
                //否则用解析的molang表达式结果作为参数
            else text = Component.translatable(textParams.key(), args.toArray(new Object[0])).getString();
            //绘制文字
            poseStack.pushPose();
            poseStack.scale(0.05f, 0.05f, -0.05f);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180));
            //noinspection IntegerDivisionInFloatingPointContext
            Minecraft.getInstance().font.drawInBatch(text,
                    textParams.centered() ? (-Minecraft.getInstance().font.width(text) / 2) : 0,
                    textParams.centered() ? (-Minecraft.getInstance().font.lineHeight / 2) : -Minecraft.getInstance().font.lineHeight,
                    textParams.colorArgb(),
                    textParams.shadow(),
                    poseStack.last().pose().scale(textParams.scale().toVector3f()),
                    bufferSource,
                    Font.DisplayMode.NORMAL,
                    textParams.backgroundColorArgb(),
                    Brightness.FULL_BRIGHT.pack());
            poseStack.popPose();
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    public void animTick() {
        var level = runtime.getAnimLevel();
        if (disposed || level == null || SparkLevel.getPhysicsLevelOrNull(level) == null) return;
        getAnimController().tick();
        var modelIndexForAnim = new ModelIndex(style.modelIndex().getType(), style.animation());
        var animSet = OAnimationSet.getORIGINS().get(modelIndexForAnim);
        if (!animController.isPlayingAnim() && animSet != null && !animSet.getAnimations().isEmpty()) {
            for (Map.Entry<String, OAnimation> entry : animSet.getAnimations().entrySet()) {
                String name = entry.getKey();
                if (rejectedAnimations.contains(entry.getValue())) continue;
                try {
                    io.github.sweetzonzi.machine_max.client.render.gui.hud.molang.HudAnimationExpressions.validate(entry.getValue(), getMolangContext());
                } catch (RuntimeException failure) {
                    rejectedAnimations.add(entry.getValue());
                    io.github.sweetzonzi.machine_max.MachineMax.LOGGER.warn("HUD 动画绑定失败 (style={}, animation={}): {}", styleId, name, failure.toString());
                    continue;
                }
                var animInstance = new AnimInstance(runtime, new AnimIndex(modelIndexForAnim, name));
                animInstance.enter();
            }
        }
    }

    public void physicsTick() {
        if (disposed || runtime.getAnimLevel() == null) return;
        try (var ignored = runtime.pinFrame()) {
            getAnimController().physTick();
        } catch (RuntimeException failure) {
            if (expressionWarnings.add("animation-physics")) {
                io.github.sweetzonzi.machine_max.MachineMax.LOGGER.warn("HUD 动画物理步失败 (style={}): {}", styleId, failure.toString());
            }
        }
    }

    public AnimController getAnimController() { return animController; }
    public ModelController getModelController() { return modelController; }
}
