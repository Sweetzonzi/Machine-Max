package io.github.sweetzonzi.machine_max.client.render.renderable;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.anim.AnimInstance;
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex;
import cn.solarmoon.spark_core.animation.anim.origin.OAnimation;
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.ModelInstance;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.molang.MolangContextRegistry;
import cn.solarmoon.spark_core.molang.SparkMolangContext;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Transform;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.visual.HudElement;
import io.github.sweetzonzi.machine_max.common.visual.HudStyle;
import io.github.sweetzonzi.machine_max.common.visual.ModelHudStyle;
import io.github.sweetzonzi.machine_max.common.visual.ScopeBehavior;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI 可渲染的 3D 模型 HUD 元素。
 * <p>
 * 一个实例只对应一份布局条目：样式（模型/动画/纹理/染色/文本槽/裁剪）来自
 * {@link ModelHudStyle}，投放（位移/旋转/缩放/投影/炮镜行为）来自 {@link HudElement}。
 * 同一份样式在一个布局里出现多次时，每个条目各自创建一个实例。
 * <p>
 * 支持透视投影和正交投影两种渲染模式。V1 的 MoLang 上下文继续使用玩家所坐座椅
 * 所属 Part 的 {@link cn.solarmoon.spark_core.molang.SparkMolangContext}。
 */
@OnlyIn(Dist.CLIENT)
public class GuiAnimatable implements IAnimatable<Player>, ITickableRenderable, Renderable {
    private final Minecraft minecraft = Minecraft.getInstance();
    /** 样式资源键，仅用于诊断。 */
    private final ResourceLocation styleId;
    private final ModelHudStyle style;
    private final HudElement element;
    private final boolean perspective;
    /** 由布局条目固定下来的投放变换；构造后不再变化。 */
    private final Transform transform;
    private final ModelController modelController;
    private final AnimController animController;
    private final Map<String, Object> variables = HashMap.newHashMap(1);
    private final SparkMolangContext<IAnimatable<Player>> molangContext = new SparkMolangContext<>(this);

    public GuiAnimatable(ResourceLocation styleId, ModelHudStyle style, HudElement element) {
        if (style == null) throw new NullPointerException("ModelHudStyle 不能为空");
        if (element == null) throw new NullPointerException("HudElement 不能为空");
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
        this.modelController = new ModelController(this);
        this.animController = new AnimController(this);
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

    @Override
    public Map<String, Object> getVariables() {
        return variables;
    }

    public HudElement getElement() {
        return element;
    }

    public boolean isPerspective() {
        return perspective;
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

    /**
     * 若玩家坐在载具座位上，返回座位对应的 SubPart。
     */
    @Nullable
    private SubPart getRidingSubPart() {
        Player player = getAnimatable();
        if (player instanceof IEntityMixin mixin
                && mixin.machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat
                && seat.getOwner() instanceof ISubsystemHost host) {
            return host.getSubPart();
        }
        return null;
    }

    /**
     * 获取 Molang 上下文。如果玩家坐在载具座位上，委派给所属 Part 的上下文，
     * 否则使用自身的上下文。
     * <p>
     * V1 只保证座椅所属 Part 上的 {@code local.*} / {@code global.*} 查询等价；
     * HUD 专用上下文在后续版本接入。
     */
    @Override
    public SparkMolangContext<?> getMolangContext() {
        SubPart sp = getRidingSubPart();
        if (sp != null) {
            return sp.part.getSparkMolangContext();  // 内部 reset 到当前 Part 状态
        }
        return molangContext;
    }

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
                    Object value = MolangContextRegistry.evalAsObject(arg, getMolangContext());
                    switch (value) {
                        case String s -> args.add(s);
                        case Number n -> args.add(df.format(n.doubleValue()));
                        case Boolean b -> args.add(String.valueOf(b));
                        default -> args.add("null");
                    }
                } catch (Exception e) {
                    args.add("MOLANG_ERROR" + e);
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
        getAnimController().tick();
        var modelIndexForAnim = new ModelIndex(style.modelIndex().getType(), style.animation());
        var animSet = OAnimationSet.getORIGINS().get(modelIndexForAnim);
        if (!animController.isPlayingAnim() && animSet != null && !animSet.getAnimations().isEmpty()) {
            for (Map.Entry<String, OAnimation> entry : animSet.getAnimations().entrySet()) {
                String name = entry.getKey();
                var animInstance = new AnimInstance(this, new AnimIndex(modelIndexForAnim, name));
                animInstance.enter();
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
    public ModelController getModelController() {
        return this.modelController;
    }

    @NotNull
    @Override
    public Matrix4f getWorldPositionMatrix(@NotNull Number number) {
        return SparkMathKt.toMatrix4f(transform.toTransformMatrix());
    }

    @NotNull
    @Override
    public ModelIndex getDefaultModelIndex() {
        return style.modelIndex();
    }
}
