package io.github.sweetzonzi.machinemax.client.renderable;

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
import cn.solarmoon.spark_core.physics.SparkMathKt;
import cn.solarmoon.spark_core.sync.SyncData;
import cn.solarmoon.spark_core.sync.SyncerType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machinemax.common.visual.AnimatableParams;
import kotlin.Unit;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@OnlyIn(Dist.CLIENT)
public class ModelAnimatable implements IAnimatable<Player>, ITickableRenderable {
    private final Minecraft minecraft = Minecraft.getInstance();
    protected final AnimatableParams params;//各种渲染参数
    private BoneGroup boneGroup;
    private final AnimController animController = new AnimController(this);

    public ModelAnimatable(AnimatableParams params) {
        this.params = params;
        this.boneGroup = new BoneGroup(this);
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
        poseStack.pushPose();
        ModelRenderHelperKt.render(
                getModel(),
                getBones(),
                poseStack.last().pose(),
                poseStack.last().normal(),
                bufferSource.getBuffer(RenderType.entityTranslucent(getModelIndex().getTextureLocation())),
                Brightness.FULL_BRIGHT.pack(),
                OverlayTexture.NO_OVERLAY,
                new Color(params.color.getX(), params.color.getY(), params.color.getZ(), params.transparency).getRGB(),
                partialTick,
                true
        );
        poseStack.popPose();
    }

    protected void renderTexts(PoseStack poseStack,
                               MultiBufferSource.BufferSource bufferSource, float partialTick) {
        poseStack.pushPose();
        poseStack.scale(-1, -1, 1);
        poseStack.mulPose(Axis.XP.rotationDegrees(180));//透视投影需要翻转文字
        //渲染所有文本
        var evaluator = ExpressionEvaluator.evaluator(getAnimatable());
        for (Map.Entry<String, AnimatableParams.TextParams> entry : params.textAttr.entrySet()) {
            String locatorName = entry.getKey();
            AnimatableParams.TextParams textParams = entry.getValue();
            Matrix4f matrix = getSpaceBoneMatrix(locatorName, partialTick);
            var offset = getModel().getLocators().get(locatorName).getOffset().toVector3f();
            matrix.translate(offset.x, offset.y, offset.z);
            poseStack.pushPose();
            poseStack.mulPose(matrix);
            //计算molang表达式
            List<String> args = new ArrayList<>();
            int num = textParams.significand();
            DecimalFormat df = new DecimalFormat("#"); // 初始化为不保留小数点
            if (num > 0) {
                df = new DecimalFormat("#." + "0".repeat(num)); // 如果num大于0，则保留相应数量的有效数字
            }
            for (String arg : textParams.molangArgs()) {
                try {
                    Object value = SparkCore.PARSER.parseExpression(arg).evalUnsafe(evaluator);
                    switch (value) {
                        case String stringValue -> args.add(stringValue);
                        case Number number -> args.add(df.format(number.doubleValue()));
                        case Boolean bool -> args.add(String.valueOf(bool));
                        case null, default -> args.add("null");
                    }
                } catch (Exception e) {
                    args.add("MOLANG_ERROR" + e);
                }
            }
            String text;
            if (textParams.molangArgs().isEmpty())
                text = net.minecraft.network.chat.Component.translatable(textParams.key()).getString();//无参数直接使用翻译键
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
        var animSet = getModelIndex().getAnimationSet().getAnimations();
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
        return params.modelIndex;
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
        return SparkMathKt.toVec3(params.getOffset(v));
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

    @Override
    public void setModelIndex(@NotNull ModelIndex modelIndex) {
        params.setModelIndex(modelIndex);
        setBones(new BoneGroup(this));
    }
}
