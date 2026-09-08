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
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.visual.HudAttr;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI 可渲染的 3D 模型 HUD 元素。
 * 支持透视投影和正交投影两种渲染模式。
 * 对应 JSON 定义的 HUD 组件，每个实例持有一个模型和动画控制器。
 */
@Getter
@OnlyIn(Dist.CLIENT)
public class GuiAnimatable implements IAnimatable<Player>, ITickableRenderable, Renderable {
    private final Minecraft minecraft = Minecraft.getInstance();
    protected final HudAttr params;// HUD 渲染参数
    private final ModelController modelController;
    private final AnimController animController;
    private final Map<String, Object> variables = HashMap.newHashMap(1);
    private final SparkMolangContext<IAnimatable<Player>> molangContext = new SparkMolangContext<>(this);
    private final boolean perspective;//是否采用透视投影
    private Matrix4f projectionMatrix;
    private static final Matrix4f VIEW_MATRIX = new Matrix4f().setLookAt(
            0, 0, 0,  // 摄像机位置 (屏幕前方0单位)
            0, 0, -0.01f,   // 观察点 (屏幕中心)
            0, 1, 0    // 上方向
    );

    public GuiAnimatable(HudAttr attr) {
        if (attr == null) throw new NullPointerException("HudAttr 不能为空");
        this.params = attr;
        this.perspective = attr.perspective;
        this.modelController = new ModelController(this);
        this.animController = new AnimController(this);
        getModelController().setModel(params.modelIndex);
        getModelController().setTextureLocation(params.texture);
        create();
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
        Lighting.setupForEntityInInventory(params.getQuaternion(partialTick));
        if (params.scissor.enable()) {//开始裁剪
            int centerX = guiGraphics.guiWidth() / 2 + params.scissor.x();
            int centerY = guiGraphics.guiHeight() / 2 + params.scissor.y();
            guiGraphics.enableScissor(
                    centerX - params.scissor.width() / 2,
                    centerY - params.scissor.height() / 2,
                    centerX + params.scissor.width() / 2,
                    centerY + params.scissor.height() / 2
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
        if (params.scissor.enable()) guiGraphics.disableScissor();//结束裁剪
        Lighting.setupFor3DItems();
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTick) {
        // ITickableRenderable 接口的世界空间渲染方法（不使用投影矩阵设置）
        poseStack.pushPose();
        Vector3f offset = params.getOffset(partialTick);
        poseStack.translate(offset.x, offset.y, offset.z);
        renderContent(poseStack, bufferSource, partialTick);
        poseStack.popPose();
    }

    /**
     * 渲染模型和文本内容（不包含投影矩阵设置，供统一 flush 流程调用）。
     */
    public void renderContent(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTick) {
        poseStack.pushPose();
        poseStack.mulPose(params.getQuaternion(partialTick));
        Vector3f scale = params.getScale(partialTick);
        poseStack.scale(scale.x, scale.y, scale.z);
        renderModel(poseStack, bufferSource, partialTick);
        renderTexts(poseStack, bufferSource, partialTick);
        poseStack.popPose();
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
                new Color(params.color.getX(), params.color.getY(), params.color.getZ(), params.transparency).getRGB(),
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
        for (Map.Entry<String, HudAttr.TextParams> entry : params.textAttr.entrySet()) {
            String locatorName = entry.getKey();
            HudAttr.TextParams textParams = entry.getValue();
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
                    textParams.getColor(),
                    textParams.shadow(),
                    poseStack.last().pose().scale(textParams.scale().toVector3f()),
                    bufferSource,
                    Font.DisplayMode.NORMAL,
                    textParams.getBackgroundColor(),
                    Brightness.FULL_BRIGHT.pack());
            poseStack.popPose();
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    public void animTick() {
        getAnimController().tick();
        var modelIndexForAnim = new ModelIndex(params.modelIndex.getType(), params.getAnimation());
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
        if (params.lastTransform == null && params.transform != null)
            params.lastTransform = params.transform;
        return SparkMathKt.toMatrix4f(SparkMathKt.lerp(params.lastTransform, params.transform, number.floatValue()).toTransformMatrix());
    }

    @NotNull
    @Override
    public ModelIndex getDefaultModelIndex() {
        return params.modelIndex;
    }
}
