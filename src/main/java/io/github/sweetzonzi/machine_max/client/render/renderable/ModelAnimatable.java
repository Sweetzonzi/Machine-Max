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
import cn.solarmoon.spark_core.js.molang.JSMolangValueKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machine_max.common.visual.AnimatableParams;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
 * 正在将功能向SubPartAnimatable等迁移
 */
@Getter
@OnlyIn(Dist.CLIENT)
@Deprecated()
public class ModelAnimatable implements IAnimatable<Player>, ITickableRenderable {
    private final Minecraft minecraft = Minecraft.getInstance();
    protected final AnimatableParams params;//各种渲染参数
    private final ModelController modelController;
    private final AnimController animController;
    private final Map<String, Object> variables = HashMap.newHashMap(1);

    public ModelAnimatable(AnimatableParams params) {
        if (params == null) throw new NullPointerException();
        this.params = params;
        this.modelController = new ModelController(this);
        this.animController = new AnimController(this);
        getModelController().setModel(params.modelIndex);
        getModelController().setTextureLocation(params.texture);
        create();
    }

    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTick) {
        poseStack.pushPose();
        Vector3f offset = params.getOffset(partialTick);
        poseStack.translate(offset.x, offset.y, offset.z);
        poseStack.pushPose();
        poseStack.mulPose(params.getQuaternion(partialTick));
        Vector3f scale = params.getScale(partialTick);
        poseStack.scale(scale.x, scale.y, scale.z);
        renderModel(poseStack, bufferSource, partialTick);
        renderTexts(poseStack, bufferSource, partialTick);
        poseStack.popPose();
        poseStack.popPose();
    }

    protected void renderModel(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTick) {
        ModelInstance model = getModelController().getModel();
        if (model == null) return;
        poseStack.pushPose();
        ModelRenderHelperKt.render(
                getModelController().getOriginModel(),
                model.getPose(),
                poseStack,
                bufferSource.getBuffer(RenderType.entityTranslucent(getModelController().getModel().getTextureLocation())),
                Brightness.FULL_BRIGHT.pack(),
                OverlayTexture.NO_OVERLAY,
                new Color(params.color.getX(), params.color.getY(), params.color.getZ(), params.transparency).getRGB(),
                partialTick
        );
        poseStack.popPose();
    }

    protected void renderTexts(PoseStack poseStack,
                               MultiBufferSource.BufferSource bufferSource, float partialTick) {
        ModelInstance model = getModelController().getModel();
        if (model == null) return;
        poseStack.pushPose();
        poseStack.scale(-1, -1, 1);
        //渲染所有文本
        for (Map.Entry<String, AnimatableParams.TextParams> entry : params.textAttr.entrySet()) {
            String locatorName = entry.getKey();
            AnimatableParams.TextParams textParams = entry.getValue();
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
            //利用js解析molang表达式
            for (String arg : textParams.molangArgs()) {
                try {
                    Object value = JSMolangValueKt.eval(arg, this);
                    switch (value) {
                        case String stringValue -> args.add(stringValue);
                        case Number number -> args.add(df.format(number.doubleValue()));
                        case Boolean bool -> args.add(String.valueOf(bool));
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
        return SparkMathKt.toMatrix4f(SparkMathKt.lerp(params.lastTransform, params.transform, number.floatValue()).toTransformMatrix());
    }

    @NotNull
    @Override
    public ModelIndex getDefaultModelIndex() {
        return params.modelIndex;
    }
}
