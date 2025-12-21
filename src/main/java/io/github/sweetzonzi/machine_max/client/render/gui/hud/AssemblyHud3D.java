package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelInstance;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.render.MMRenderTypes;
import io.github.sweetzonzi.machine_max.client.render.gui.animation.AnimatedFloat;
import io.github.sweetzonzi.machine_max.client.render.gui.animation.AnimatedQuaternion;
import io.github.sweetzonzi.machine_max.client.render.gui.animation.TimeSource;
import io.github.sweetzonzi.machine_max.client.render.gui.hud3d.Hud3DContext;
import io.github.sweetzonzi.machine_max.client.render.gui.hud3d.IHud3DElement;
import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.IngredientCountPair;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.util.Easing;
import io.github.sweetzonzi.machine_max.util.ViewOrientationResolver;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Brightness;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class AssemblyHud3D implements IHud3DElement {
    public static final Vector3f UP = new Vector3f(0, 1, 0);
    public static final Vector3f FORWARD = new Vector3f(0, 0, -1);


    /* ================== 布局参数 (保持像素单位) ================== */

    private static final int HUD_WIDTH = 150;
    private static final int HEADER_HEIGHT = 55;
    private static final int LINE_HEIGHT = 18;
    private static final int PADDING = 8;
    // 3D 缩放比例：将像素映射到世界单位 (调节此值改变 HUD 在世界中的物理大小)
    private static final float PIXEL_SCALE = 0.5f;

    /* ================== 颜色定义 ================== */

    private static final int HUD_BG = 0xCC111111;
    private static final int HUD_SIDE = 0xFFFF6600;
    private static final int BAR_BG = 0xAA2A2A2A;

    private static final int ROW_BG_DARK = 0x66222222;
    private static final int ROW_BG_LIGHT = 0x555A5A5A;
    private static final int ROW_BG_ACTIVE = 0x55FF6600;
    private static final int ROW_BG_LACK = 0x557A2222;

    private static final int TEXT_MAIN = 0xFFFFFFFF;
    private static final int TEXT_SUB = 0xFFBBBBBB;
    private static final int TEXT_DIM = 0xFF888888;

    /* ================== 动画状态 ================== */

    private final AnimatedFloat animatedProgressFloat = new AnimatedFloat(0).easing(Easing::easeInOut);
    private final AnimatedFloat animatedHudHeight = new AnimatedFloat(0).easing(Easing::easeInOut);
    private final AnimatedFloat animatedHudWidth = new AnimatedFloat(0).easing(Easing::easeInOut);
    private final AnimatedQuaternion projectionRot = new AnimatedQuaternion(new Quaternionf()).easing(Easing::easeInOut);
    private Part part;
    private final ViewOrientationResolver projectionResolver = new ViewOrientationResolver();
    private final Vector3f forward = new Vector3f(FORWARD);
    private final Vector3f up = new Vector3f(UP);

    /* ================== 逻辑实现 ================== */

    @Override
    public boolean shouldRender(LocalPlayer player) {
        // 检查视线是否聚焦在部件上
        LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
        SubPart subPart = eyesight.getSubPart();
        return subPart != null || (animatedHudWidth.get() > 0);
    }

    @Override
    public void render(Hud3DContext ctx) {
        // 获取数据上下文
        float currentTime = TimeSource.getTimeSeconds(ctx.mc, ctx.partialTicks);
        LivingEntityEyesightAttachment eyesight = ctx.player.getData(MMAttachments.getENTITY_EYESIGHT());
        SubPart subPart = eyesight.getSubPart();
        // 动画状态更新
        if (subPart == null) {
            if (animatedHudWidth.getTarget() != 0) {
                animatedHudWidth.animateTo(0, 0.25f, currentTime);
                animatedProgressFloat.setImmediate(0);
                part = null;
            } else if (animatedHudWidth.get() == 0) return;
        } else {
            if (animatedHudWidth.getTarget() != HUD_WIDTH)
                animatedHudWidth.animateTo(HUD_WIDTH, 0.25f, currentTime);
            if (part != subPart.part) {
                part = subPart.part;
                animatedProgressFloat.setImmediate(part.getAssemblingProgress());
                Quaternionf rot = resolveViewOrientation(subPart, ctx);
                projectionRot.setImmediate(rot);
            }
        }
        animatedHudWidth.update(currentTime);
        // 计算状态
        List<MaterialStatus> materials;
        if (part != null && part.getRecipe() instanceof FabricatingRecipe recipe)
            materials = buildMaterialStatus(recipe, part, ctx.player);
        else materials = List.of();

        int hudHeight = HEADER_HEIGHT + materials.size() * LINE_HEIGHT + PADDING;
        if (animatedHudHeight.getTarget() != hudHeight)
            animatedHudHeight.animateTo(hudHeight, 0.25f, currentTime);
        animatedHudHeight.update(currentTime);
        // 开始 3D 变换
        PoseStack poseStack = ctx.poseStack;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(20));
        poseStack.translate(50, 0, 30);
        poseStack.pushPose();

        // 1. 缩放：将像素单位缩小到世界单位，并翻转 Y 轴 (GUI Y向下, 世界 Y向上)
        poseStack.scale(PIXEL_SCALE, PIXEL_SCALE, PIXEL_SCALE);

        // 2. 平移：将原点从中心移动到 HUD 左上角，实现居中布局
        float startX = -HUD_WIDTH / 2f;
        float startY = -animatedHudHeight.get() / 2f;

        // Z-Offset 定义：背景在后 (0.01), 内容在前 (-0.01 ~ -0.05)
        float zBg = 0.01f;
        float zText = -0.02f;

        /* ---------- 背景绘制 ---------- */

        // 主背景
        ctx.fill(startX, startY, startX + animatedHudWidth.get(), startY + animatedHudHeight.get(), HUD_BG, zBg * 2);
        // 侧边装饰条
        ctx.fill(startX, startY, startX + 3, startY + animatedHudHeight.get(), HUD_SIDE, zBg);

        /* ---------- 标题部分 ---------- */
        String partName = part != null ? Component
                .translatable(part.type.getRegistryKey().toLanguageKey())
                .getString() : "";

        // 标题缩放
        poseStack.pushPose();
        poseStack.translate(startX + PADDING, startY + 6, zText);
        poseStack.scale(1.3f, 1.3f, 1.3f);
        ctx.drawText(Component.literal(partName), 0, 0, TEXT_MAIN);
        poseStack.popPose();

        ctx.drawText(Component.literal(part != null ? "组装进度: " : ""), startX + PADDING, startY + 20, TEXT_SUB);

        /* ---------- 总进度条 ---------- */
        drawAnimatedProgressBar(
                ctx,
                startX + PADDING,
                startY + 34,
                Math.max(animatedHudWidth.get() - PADDING * 2, 0),
                subPart != null ? subPart.part.getAssemblingProgress() : 0f,
                currentTime
        );

        /* ---------- 材料列表 ---------- */
        float listY = startY + HEADER_HEIGHT - 6;
        var text = part != null ? Component.translatable("gui.machine_max.fabricator.materials").append(":") : Component.empty();
        ctx.drawText(text, startX + PADDING, listY, TEXT_MAIN);
        listY += 10;

        for (MaterialStatus m : materials) {
            drawMaterialRow(ctx, startX, listY, m);
            listY += LINE_HEIGHT;
            if (listY > startY + animatedHudHeight.get()) break;
        }

        if (subPart != null) {
            poseStack.pushPose();
            poseStack.scale(20, -20, 0.001f);
            var normal = new Matrix3f();
            Quaternionf rot = resolveViewOrientation(subPart, ctx);
            normal.rotate(rot);
            if (rot.dot(projectionRot.getTarget()) < 0.99f) {
                projectionRot.animateTo(rot, 0.25f, currentTime);
            }
            projectionRot.update(currentTime);
            poseStack.mulPose(projectionRot.get());
            ModelController modelController = subPart.getModelController();
            ModelInstance modelInstance = modelController.getModel();
            ModelRenderHelperKt.render(
                    modelInstance.getOrigin(),
                    modelInstance.getPose(),
                    poseStack.last().pose(),
                    normal,
                    ctx.buffer.getBuffer(MMRenderTypes.alwaysVisibleLines()),
                    Brightness.FULL_BRIGHT.pack(),
                    OverlayTexture.NO_OVERLAY,
                    Color.WHITE.getRGB(),
                    ctx.partialTicks,
                    true
            );
            poseStack.popPose();
        }

        poseStack.popPose();
        poseStack.popPose(); // 恢复变换
    }

    /* ================== 绘制辅助方法 ================== */

    private void drawAnimatedProgressBar(
            Hud3DContext ctx,
            float x,
            float y,
            float width,
            float targetProgress,
            float currentTime
    ) {
        // 平滑动画
        if (targetProgress != animatedProgressFloat.getTarget())
            animatedProgressFloat.animateTo(targetProgress, 0.5f, currentTime);

        animatedProgressFloat.update(currentTime);

        float height = 10;

        // 进度条背景
        ctx.fill(x, y, x + width, y + height, BAR_BG, 0.002f);

        // 进度条前景
        float filled = width * animatedProgressFloat.get();
        int fillColor = Easing.lerpColorFromTransparent(0xFFFF6600, animatedProgressFloat.get());

        // 使用 fillGradient 保持视觉一致性
        ctx.fill(x, y, x + filled, y + height, fillColor, 0.001f);

        // 百分比文字
        String percent = part != null ? Math.round(animatedProgressFloat.get() * 100) + "%" : "";
        if (percent.isEmpty()) return;
        int textW = ctx.font.width(percent);

        ctx.drawText(
                Component.literal(percent),
                x + width / 2f - textW / 2f,
                y + height / 2f - 4, // 垂直居中微调
                TEXT_MAIN
        );
    }

    private void drawMaterialRow(
            Hud3DContext ctx,
            float hudX,
            float y,
            MaterialStatus m
    ) {
        float rowX = hudX + PADDING;
        float rowW = Math.max(animatedHudWidth.get() - PADDING * 2, 0);
        float rowH = LINE_HEIGHT - 2;

        int baseColor;
        if (m.lacking()) {
            baseColor = ROW_BG_LACK;
        } else if (m.consuming) {
            baseColor = ROW_BG_ACTIVE;
        } else if (m.completed()) {
            baseColor = ROW_BG_LIGHT;
        } else {
            baseColor = ROW_BG_DARK;
        }

        /* 底层背景 */
        ctx.fill(rowX, y, rowX + rowW, y + rowH, baseColor, 0.002f);

        /* 消耗进度填充 */
        float fillW = rowW * m.progress();
        if (fillW > 0) {
            int fillColor = Easing.lerpColor(
                    brighten(baseColor, 1.3f),
                    brighten(baseColor, 0.7f),
                    m.progress()
            );
            ctx.fill(rowX, y, rowX + fillW, y + rowH, fillColor, 0.001f);
        }

        /* 物品图标 */
        ItemStack icon = m.pair.ingredient().getItems()[0];
        // 3D HUD 中绘制物品需传入 scale。16.0f 对应 GUI 中的 16x16 像素大小
        ctx.drawItem(icon, rowX + rowH / 2 + 1, y + rowH / 2, 16.0f);

        /* 文本信息 */
        int textColor = m.completed() ?
                Easing.lerpColorFromTransparent(TEXT_MAIN, animatedHudWidth.get() / HUD_WIDTH) :
                Easing.lerpColorFromTransparent(TEXT_DIM, animatedHudWidth.get() / HUD_WIDTH);
        if (animatedHudWidth.get() / HUD_WIDTH < 0.3f) return;
        ctx.drawText(
                icon.getHoverName(),
                rowX + 22,
                y + 5,
                textColor
        );

        ctx.drawText(
                Component.literal(String.valueOf(m.required())),
                rowX + rowW - 46,
                y + 5,
                Easing.lerpColorFromTransparent(TEXT_SUB, animatedHudWidth.get() / HUD_WIDTH)
        );

        if (m.completed()) {
            ctx.drawText(Component.literal("✔"),
                    rowX + rowW - 30,
                    y + 5,
                    Easing.lerpColorFromTransparent(TEXT_MAIN, animatedHudWidth.get() / HUD_WIDTH));
        }

        String inv = m.inventory == Integer.MAX_VALUE ? "∞" : String.valueOf(m.inventory);
        ctx.drawText(Component.literal(inv),
                rowX + rowW - 16,
                y + 5,
                Easing.lerpColorFromTransparent(TEXT_SUB, animatedHudWidth.get() / HUD_WIDTH));
    }

    private record MaterialStatus(
            IngredientCountPair pair,
            int consumed,
            int inventory,
            boolean consuming
    ) {
        int required() {
            return pair.count();
        }

        boolean completed() {
            return consumed >= required();
        }

        boolean lacking() {
            return inventory < (required() - consumed);
        }

        float progress() {
            return required() == 0 ? 1f : consumed / (float) required();
        }
    }

    private List<MaterialStatus> buildMaterialStatus(
            FabricatingRecipe recipe,
            Part part,
            LocalPlayer player
    ) {
        List<MaterialStatus> list = new ArrayList<>();
        int consumedTotal = part.getMaterialProgress();

        for (IngredientCountPair pair : recipe.getIngredientPairs()) {
            int required = pair.count();
            int consumed = Math.min(required, consumedTotal);

            boolean consuming = consumedTotal > 0 && consumedTotal < required;
            consumedTotal -= consumed;

            int inventory = 0;
            if (player.isCreative()) {
                inventory = Integer.MAX_VALUE;
            } else {
                for (ItemStack stack : player.getInventory().items) {
                    if (!stack.isEmpty() && pair.ingredient().test(stack)) {
                        inventory += stack.getCount();
                    }
                }
            }
            list.add(new MaterialStatus(pair, consumed, inventory, consuming));
        }
        return list;
    }

    private int brighten(int color, float factor) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private Quaternionf resolveViewOrientation(SubPart subPart, Hud3DContext ctx){
        var camPose = new Matrix4f(subPart.getWorldPositionMatrix(ctx.partialTicks)).invert();
        camPose.rotate(ctx.camera.rotation());
        FORWARD.mul(new Matrix3f(camPose), forward);
        UP.mul(new Matrix3f(camPose), up);
        return projectionResolver.resolveViewRotation(forward, up);
    }
}