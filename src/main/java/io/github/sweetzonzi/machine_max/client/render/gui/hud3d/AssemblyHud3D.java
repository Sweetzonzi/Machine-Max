package io.github.sweetzonzi.machine_max.client.render.gui.hud3d;

import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelInstance;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machine_max.client.input.KeyBinding;
import io.github.sweetzonzi.machine_max.client.render.MMRenderTypes;
import io.github.sweetzonzi.machine_max.client.render.gui.animation.AnimatedFloat;
import io.github.sweetzonzi.machine_max.client.render.gui.animation.AnimatedQuaternion;
import io.github.sweetzonzi.machine_max.client.render.gui.animation.TimeSource;
import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machine_max.common.item.prop.CrowbarItem;
import io.github.sweetzonzi.machine_max.common.item.prop.WeldingTorchItem;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.IngredientCountPair;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.util.Easing;
import io.github.sweetzonzi.machine_max.util.ViewOrientationResolver;
import net.minecraft.client.player.LocalPlayer;
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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@OnlyIn(Dist.CLIENT)
public class AssemblyHud3D implements IHud3DElement {
    public static final Vector3f ZERO = new Vector3f();
    public static final Vector3f UP = new Vector3f(0, 1, 0);
    public static final Vector3f RIGHT = new Vector3f(1, 0, 0);
    public static final Vector3f FORWARD = new Vector3f(0, 0, -1);
    private final Random random = new Random();

    /* ================== 布局参数 (保持像素单位) ================== */

    private static final int HUD_WIDTH = 150;
    private static final int HEADER_HEIGHT = 25;
    private static final int PROJECTION_HEIGHT = 75;
    private static final int TEXT_LINE_HEIGHT = 13;
    private static final int MATERIAL_LINE_HEIGHT = 18;
    private static final int PADDING = 8;
    // 3D 缩放比例：将像素映射到世界单位 (调节此值改变 HUD 在世界中的物理大小)
    private static final float PIXEL_SCALE = 0.5f;

    /* ================== 颜色定义 ================== */

    private static final int HUD_BG = new Color(32, 32, 32, 128).getRGB();
    private static final int HUD_THEME = new Color(255, 100, 0, 255).getRGB();
    //    private static final int HUD_THEME = new Color(150, 200, 255, 200).getRGB();
    private static final int BAR_BG = new Color(64, 64, 64, 32).getRGB();
    private static final int ROW_BG_DARK = 0x66222222;
    private static final int ROW_BG_LIGHT = 0x555A5A5A;
    //    private static final int ROW_BG_ACTIVE = new Color(150, 200, 255, 155).getRGB();
    private static final int ROW_BG_ACTIVE = new Color(255, 100, 0, 155).getRGB();
    private static final int ROW_BG_LACK = new Color(150, 32, 32, 128).getRGB();

    private static final int TEXT_MAIN = new Color(255, 255, 255, 255).getRGB();
    private static final int TEXT_SUB = new Color(200, 200, 200, 255).getRGB();
    private static final int TEXT_DIM = new Color(150, 150, 150, 255).getRGB();
    private static final int TEXT_WARN = new Color(255, 0, 0, 255).getRGB();
    private static final int TEXT_HINT = new Color(255, 255, 100, 255).getRGB();

    private static final Color BAR_HP_FULL = new Color(100, 255, 100, 200);
    private static final Color BAR_HP_80 = new Color(255, 255, 200, 200);
    private static final Color BAR_HP_60 = new Color(255, 255, 0, 200);
    private static final Color BAR_HP_40 = new Color(255, 128, 0, 200);
    private static final Color BAR_HP_20 = new Color(255, 64, 0, 200);
    private static final Color BAR_HP_0 = new Color(255, 0, 0, 200);
    private static final Color BAR_HP_DESTROYED = new Color(50, 50, 50, 200);

    private static final Color SUBSYSTEM_HP_FULL = new Color(100, 255, 100, 64);
    private static final Color SUBSYSTEM_HP_80 = new Color(255, 255, 64, 64);
    private static final Color SUBSYSTEM_HP_60 = new Color(255, 255, 0, 64);
    private static final Color SUBSYSTEM_HP_40 = new Color(255, 128, 0, 64);
    private static final Color SUBSYSTEM_HP_20 = new Color(255, 64, 0, 64);
    private static final Color SUBSYSTEM_HP_0 = new Color(255, 0, 0, 64);
    private static final Color SUBSYSTEM_HP_DESTROYED = new Color(50, 50, 50, 128);

    /* ================== 动画状态 ================== */

    private final AnimatedFloat animatedDurabilityFloat = new AnimatedFloat(0).easing(Easing::easeInOut);
    private final AnimatedFloat animatedProgressFloat = new AnimatedFloat(0).easing(Easing::easeInOut);
    private final AnimatedFloat animatedHudHeight = new AnimatedFloat(0).easing(Easing::easeInOut);
    private final AnimatedFloat animatedHudWidth = new AnimatedFloat(0).easing(Easing::easeInOut);
    private final AnimatedQuaternion projectionRot = new AnimatedQuaternion(new Quaternionf()).easing(Easing::easeInOut);
    private final AnimatedFloat projectionScale = new AnimatedFloat(1).easing(Easing::easeInOut);
    private Part part;
    private final ViewOrientationResolver projectionResolver = new ViewOrientationResolver();
    private final List<Component> warningMessages = new ArrayList<>();
    private final List<Component> hintMessages = new ArrayList<>();
    private final Vector3f forward = new Vector3f(FORWARD);
    private final Vector3f up = new Vector3f(UP);
    private final DecimalFormat decimalFormat = new DecimalFormat("#0");

    /* ================== 逻辑实现 ================== */

    @Override
    public boolean shouldRender(LocalPlayer player) {
        // 检查视线是否聚焦在部件上
        boolean flag = (player.getMainHandItem().getItem() instanceof WeldingTorchItem
                || player.getOffhandItem().getItem() instanceof WeldingTorchItem
                || player.getMainHandItem().getItem() instanceof CrowbarItem
                || player.getOffhandItem().getItem() instanceof CrowbarItem);
        LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
        SubPart subPart = eyesight.getSubPart();
        return (flag && subPart != null) || (animatedHudWidth.get() > 0);
    }

    @Override
    public void render(Hud3DContext ctx) {
        // 获取数据上下文
        float currentTime = TimeSource.getTimeSeconds(ctx.mc, ctx.partialTicks);
        LivingEntityEyesightAttachment eyesight = ctx.player.getData(MMAttachments.getENTITY_EYESIGHT());
        boolean flag = (ctx.player.getMainHandItem().getItem() instanceof WeldingTorchItem
                || ctx.player.getOffhandItem().getItem() instanceof WeldingTorchItem
                || ctx.player.getMainHandItem().getItem() instanceof CrowbarItem
                || ctx.player.getOffhandItem().getItem() instanceof CrowbarItem);
        SubPart subPart = eyesight.getSubPart();
        // 动画状态更新
        if (subPart == null || !flag) {
            if (animatedHudWidth.getTarget() != 0) {
                animatedHudWidth.animateTo(0, 0.25f, currentTime);
                animatedProgressFloat.setImmediate(0);
                animatedDurabilityFloat.setImmediate(0);
                part = null;
                warningMessages.clear();
                hintMessages.clear();
            } else if (animatedHudWidth.get() == 0) return;
        } else {
            if (animatedHudWidth.getTarget() != HUD_WIDTH)
                animatedHudWidth.animateTo(HUD_WIDTH, 0.25f, currentTime);
            if (part != subPart.part) {
                part = subPart.part;
                animatedProgressFloat.setImmediate(part.getAssemblingProgress());
                animatedDurabilityFloat.setImmediate(subPart.getDurability() / subPart.getMaxDurability());
                updateViewOrientation(subPart, ctx);
                Quaternionf rot = resolveViewOrientation();
                float scale = resolveViewScale(subPart, HUD_WIDTH - 2 * PADDING, PROJECTION_HEIGHT - 10, 22f);
                projectionRot.setImmediate(rot);
                projectionScale.setImmediate(scale);
            }
        }
        animatedHudWidth.update(currentTime);
        // 计算状态
        List<MaterialStatus> materials;
        if (part != null && part.getRecipe() instanceof FabricatingRecipe recipe)
            materials = buildMaterialStatus(recipe, part, ctx.player);
        else materials = List.of();
        var research = ctx.player.getData(MMAttachments.getBLUEPRINT());
        if (subPart != null) {
            warningMessages.clear();
            hintMessages.clear();
            if (subPart.isDestroyed()) {
                warningMessages.add(Component.translatable("hud.warn.machine_max.subpart_destroying", decimalFormat.format(subPart.getDestroyTime() * 0.05f)));
            }
            for (AbstractSubsystem subsystem : subPart.subsystems.values()) {
                if (subsystem.isDestroyed())
                    warningMessages.add(Component.translatable("hud.warn.machine_max.subsystem_malfunction",
                            Component.translatable(subsystem.name).getString()));
                if (subsystem.getDurability() < subsystem.getMaxDurability())
                    hintMessages.add(Component.translatable("hud.hint.machine_max.subsystem_durability",
                            Component.translatable(subsystem.name).getString(),
                            decimalFormat.format(subsystem.getDurability()),
                            decimalFormat.format(subsystem.getMaxDurability())));
            }
            for (AbstractConnector connector : subPart.connectors.values()) {
                if (!connector.isInternal() && connector.hasPart()) {
                    if (connector.getIntegrity() < 0.1 * connector.getBasicIntegrity())
                        warningMessages.add(Component.translatable("hud.warn.machine_max.connector_integrity_low",
                                Component.translatable(connector.name).getString()));
                    if (connector.getIntegrity() < connector.getBasicIntegrity())
                        hintMessages.add(Component.translatable("hud.hint.machine_max.connector_integrity",
                                Component.translatable(connector.name).getString(),
                                decimalFormat.format(connector.getIntegrity()),
                                decimalFormat.format(connector.getBasicIntegrity())));
                }
            }
            if (!research.canAssemble(ctx.player, subPart.part)) {
                hintMessages.add(Component.translatable("hud.hint.machine_max.cannot_assemble"));
            }
        }
        int hudHeight = HEADER_HEIGHT + PROJECTION_HEIGHT + materials.size() * MATERIAL_LINE_HEIGHT + PADDING;
        if (!materials.isEmpty()) hudHeight += TEXT_LINE_HEIGHT * 2;
        if (subPart != null && subPart.part.subParts.size() > 1) hudHeight += TEXT_LINE_HEIGHT;
        hudHeight += -TEXT_LINE_HEIGHT + 4;
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
        ctx.fill(startX, startY, startX + 3, startY + animatedHudHeight.get(), HUD_THEME, zBg);

        /* ---------- 标题部分 ---------- */
        if (subPart != null) // 连线至部件的中心点
            ctx.drawScreenFacingLine(
                    new Vector3f(startX + 1, startY + 1, 0),
                    ctx.worldToLocal(subPart.getWorldPositionMatrix(ctx.partialTicks).getTranslation(new Vector3f())),
                    3f,
                    HUD_THEME,
                    MMRenderTypes.alwaysVisibleSolid()
            );
        String partName = part != null ? Component
                .translatable(part.type.getRegistryKey().toLanguageKey())
                .getString() : "";
        // 标题缩放
        startY += 5;
        poseStack.pushPose();
        poseStack.translate(startX + PADDING, startY, zText);
        poseStack.scale(1.5f, 1.5f, 1.5f);
        ctx.drawText(Component.literal(partName), 0, 0, TEXT_MAIN);
        poseStack.popPose();
        if (subPart != null && subPart.part.subParts.size() > 1) {
            startY += TEXT_LINE_HEIGHT;
            ctx.drawText(
                    Component.translatable(subPart.getName()),
                    startX + PADDING, startY, TEXT_SUB);
        }

        //分割线
        startY += TEXT_LINE_HEIGHT;
        ctx.fill(
                startX + PADDING, startY,
                startX + animatedHudWidth.get() - PADDING, startY + 1,
                Easing.lerpColorFromTransparent(TEXT_DIM, animatedHudWidth.get() / HUD_WIDTH), zBg
        );

        // 部件投影
        startY += 1;
        if (subPart != null) {
            drawSubPartProjection(ctx,
                    subPart,
                    startX + PADDING, startY,
                    animatedHudWidth.get() - 2 * PADDING, PROJECTION_HEIGHT,
                    currentTime);
        }

        // 渲染警告和提示信息
        ctx.poseStack.pushPose();
        ctx.poseStack.translate(startX + PADDING, startY + 3, 0);
        ctx.poseStack.scale(0.45f, 0.45f, 0.45f);
        float textY = 0;
        for (Component message : warningMessages) {
            int warnColor = Easing.lerpColorFromTransparent(TEXT_WARN, animatedHudWidth.get() / HUD_WIDTH);
            ctx.drawText(message, 0, textY, warnColor);
            ctx.drawText(message, 0.5f, textY, warnColor);
            textY += TEXT_LINE_HEIGHT;
        }
        for (Component message : hintMessages) {
            int hintColor = Easing.lerpColorFromTransparent(TEXT_HINT, animatedHudWidth.get() / HUD_WIDTH);
            ctx.drawText(message, 0, textY, hintColor);
            ctx.drawText(message, 0.3f, textY, hintColor);
            textY += TEXT_LINE_HEIGHT;
        }
        ctx.poseStack.popPose();

        startY += PROJECTION_HEIGHT + 5;

        if (!materials.isEmpty()) {
            // 材料列表
            for (MaterialStatus m : materials.reversed()) {
                drawMaterialRow(ctx, startX, startY, m);
                startY += MATERIAL_LINE_HEIGHT;
                if (startY > startY + animatedHudHeight.get()) break;
            }

            // 组装总进度条
            ctx.drawText(part != null ? Component.translatable("hud.info.machine_max.assembling_progress") : Component.empty(), startX + PADDING, startY, TEXT_SUB);
            startY += TEXT_LINE_HEIGHT;
            drawAnimatedProgressBar(
                    ctx,
                    startX + PADDING,
                    startY,
                    Math.max(animatedHudWidth.get() - PADDING * 2, 0),
                    10f,
                    subPart != null ? subPart.part.getAssemblingProgress() : 0f,
                    ROW_BG_ACTIVE,
                    currentTime,
                    false
            );
            // 百分比文字
            String percent = part != null ? Math.round(animatedProgressFloat.get() * 100) + "%" : "";
            if (!percent.isEmpty()) {
                int textW = ctx.font.width(percent);
                ctx.drawText(
                        Component.literal(percent),
                        startX + PADDING + Math.max(animatedHudWidth.get() - PADDING * 2, 0) / 2f - textW / 2f,
                        startY + 10f / 2f - 4, // 垂直居中微调
                        TEXT_MAIN
                );
            }
        }
        // 绘制按键提示
        if (ctx.mc.player != null && subPart != null) {
            startY += TEXT_LINE_HEIGHT + 2;
            boolean crouching = ctx.mc.player.isCrouching();
            if (ctx.mc.player.getMainHandItem().getItem() instanceof WeldingTorchItem) {
                var availableRecipes = research.getAvailablePartRecipeFor(ctx.player, subPart.part.type.getRegistryKey());
                if (research.canAssemble(ctx.player, subPart.part)) {
                    ctx.drawText(
                            Component.translatable("hud.key.machine_max.assemble",
                                    ctx.mc.options.keyUse.getKey().getDisplayName()),
                            startX + PADDING / 2f, startY, Easing.lerpColorFromTransparent(!crouching ? TEXT_HINT : TEXT_DIM, animatedHudWidth.get() / HUD_WIDTH)
                    );
                } else {
                    ctx.drawText(
                            Component.translatable("hud.key.machine_max.repair_without_assemble",
                                    ctx.mc.options.keyUse.getKey().getDisplayName()),
                            startX + PADDING / 2f, startY, Easing.lerpColorFromTransparent(!crouching ? TEXT_HINT : TEXT_DIM, animatedHudWidth.get() / HUD_WIDTH)
                    );
                }
                startY += TEXT_LINE_HEIGHT + 2;
                ctx.drawText(Component.translatable("hud.key.machine_max.disassemble",
                                ctx.mc.options.keyShift.getKey().getDisplayName(),
                                ctx.mc.options.keyUse.getKey().getDisplayName()),
                        startX + PADDING / 2f, startY, Easing.lerpColorFromTransparent(crouching ? TEXT_HINT : TEXT_DIM, animatedHudWidth.get() / HUD_WIDTH)
                );
                if (ctx.player.isCreative() || (part.getAssemblingProgress() <= 0 && part.getMaterialProgress() <= 0)) {
                    if (!availableRecipes.isEmpty() && availableRecipes.size() > 1) {
                        startY += TEXT_LINE_HEIGHT + 2;
                        ctx.drawText(Component.translatable("hud.key.machine_max.cycle_recipe",
                                        KeyBinding.assemblyCycleRecipeKey.getKey().getDisplayName(),
                                        availableRecipes.size()),
                                startX + PADDING / 2f, startY, Easing.lerpColorFromTransparent(availableRecipes.size() > 1 ? TEXT_HINT : TEXT_DIM, animatedHudWidth.get() / HUD_WIDTH)
                        );
                    }
                }
            } else if (ctx.mc.player.getMainHandItem().getItem() instanceof CrowbarItem) {
                ctx.drawText(
                        Component.translatable("hud.key.machine_max.tear_down",
                                ctx.mc.options.keyUse.getKey().getDisplayName()),
                        startX + PADDING / 2f, startY, Easing.lerpColorFromTransparent(!crouching ? TEXT_HINT : TEXT_DIM, animatedHudWidth.get() / HUD_WIDTH)
                );
                startY += TEXT_LINE_HEIGHT + 2;
                ctx.drawText(Component.translatable("hud.key.machine_max.detach",
                                ctx.mc.options.keyShift.getKey().getDisplayName(),
                                ctx.mc.options.keyUse.getKey().getDisplayName()),
                        startX + PADDING / 2f, startY, Easing.lerpColorFromTransparent(crouching ? TEXT_HINT : TEXT_DIM, animatedHudWidth.get() / HUD_WIDTH)
                );
            }
        }
        poseStack.popPose();
        poseStack.popPose(); // 恢复变换
    }

    /* ================== 绘制辅助方法 ================== */

    private void drawSubPartProjection(
            Hud3DContext ctx,
            SubPart subPart,
            float x,
            float y,
            float width,
            float height,
            float currentTime
    ) {
        float progress = animatedHudWidth.get() / HUD_WIDTH;
        ctx.poseStack.pushPose();
        ctx.poseStack.translate(x + width / 2, y + height / 2 - 2, 0);//原点移动至展示范围中心
        ctx.poseStack.scale(
                -20f * progress,
                -20f * progress,
                0.5f * progress); // 左右取反，Z轴压扁，使其看起来接近正交投影
        ctx.poseStack.pushPose();
        float scale = resolveViewScale(subPart, HUD_WIDTH - 2 * PADDING, PROJECTION_HEIGHT - 10, 22f);
        if (projectionScale.getTarget() != scale) {
            projectionScale.animateTo(scale, 0.25f, currentTime);
        }
        projectionScale.update(currentTime);
        ctx.poseStack.scale(projectionScale.get(), projectionScale.get(), projectionScale.get()); // 缩放到合适大小
        updateViewOrientation(subPart, ctx);
        Quaternionf rot = resolveViewOrientation();
        var normal = new Matrix3f();
        normal.rotate(rot);
        if (rot.dot(projectionRot.getTarget()) < 0.999f) {
            projectionRot.animateTo(rot, 0.25f, currentTime);
        }
        projectionRot.update(currentTime);
        ctx.poseStack.mulPose(projectionRot.get()); // 按照视角方向旋转投影
        Vector3f offset = resolveViewTranslation(subPart).mul(-1f);
        ctx.poseStack.translate(offset.x(), offset.y(), offset.z()); // 将展示中心挪到AABB中心
        ctx.poseStack.pushPose();
        ctx.poseStack.mulPose(SparkMathKt.toMatrix4f(subPart.getLocalMassCenterTransform().invert().toTransformMatrix())); // 考虑模型原点和质心的位置差异
        ModelController modelController = subPart.getModelController();
        ModelInstance modelInstance = modelController.getModel();
        // 渲染所有块
        var bonesToRender = subPart.attr.getBones(subPart.part.variant);
        for (OBone bone : bonesToRender.values()) {
            ModelRenderHelperKt.render(
                    bone,
                    modelInstance.getPose(),
                    new Matrix4f(ctx.poseStack.last().pose()),
                    normal,
                    ctx.buffer.getBuffer(MMRenderTypes.alwaysVisibleSolid()),
                    Brightness.FULL_BRIGHT.pack(),
                    OverlayTexture.NO_OVERLAY,
                    Easing.lerpColorFromTransparent(0x11ffffff, progress),
                    ctx.partialTicks,
                    false
            );
        }
        // 独立渲染子系统，并根据耐久度调整渲染颜色
        for (Map.Entry<String, HitBoxAttr> entry : subPart.attr.getHitBoxes().entrySet()) {
            String name = entry.getKey();
            String subsystemName = entry.getValue().subsystem();
            if (bonesToRender.containsKey(name) && !subsystemName.isEmpty()) {
                AbstractSubsystem subsystem = subPart.subsystems.get(subsystemName);
                if (subsystem != null) {
                    ModelRenderHelperKt.render(
                            bonesToRender.get(name),
                            modelInstance.getPose(),
                            new Matrix4f(ctx.poseStack.last().pose()),
                            normal,
                            ctx.buffer.getBuffer(MMRenderTypes.alwaysVisibleSolid()),
                            Brightness.FULL_BRIGHT.pack(),
                            OverlayTexture.NO_OVERLAY,
                            Easing.lerpColorFromTransparent(
                                    getSubsystemColorByDurability(subsystem.getDurability(), subsystem.getMaxDurability()), progress),
                            ctx.partialTicks,
                            true
                    );
                }
            }
        }
        ctx.poseStack.popPose();
        float halfSize = subPart.getBody().getCollisionShape().boundingBoxWithoutRecalculate(
                com.jme3.math.Vector3f.ZERO,
                com.jme3.math.Matrix3f.IDENTITY,
                null
        ).getExtent(null).length() / 3 * 0.2f;
        halfSize = Math.max(0.01f, halfSize);
        float lineWidth = 2;
        float crossOffset = 0f;
        // 渲染连接点结构完整性
        for (Map.Entry<String, AbstractConnector> entry : subPart.getConnectors().entrySet()) {
            AbstractConnector connector = entry.getValue();
            float integrityProgress = connector.getIntegrity() / connector.getBasicIntegrity();
            integrityProgress *= integrityProgress;
            integrityProgress = 1 - Math.clamp(integrityProgress, 0, 1);
            if (!connector.isInternal()) {
                crossOffset = connector.hasPart() ? 0.1f * integrityProgress : 0;
            }
            ctx.poseStack.pushPose();
            ctx.poseStack.mulPose(SparkMathKt.toMatrix4f(connector.getOffsetFromMassCenter().toTransformMatrix()));
            ctx.poseStack.pushPose();
            ctx.poseStack.mulPose(rot.invert()); // 标记面向hud平面
            // 绘制十字表示连接点完整性
            if (connector.hasPart()) {
                int redShiftGreen = Easing.lerpColor(0xff008800, 0xff880000, 0.25f * integrityProgress);
                int redShiftBlue = Easing.lerpColor(0xff000088, 0xff880000, 0.25f * integrityProgress);
                ctx.poseStack.translate(nextRandomNegPos1() * crossOffset, nextRandomNegPos1() * crossOffset, nextRandomNegPos1() * crossOffset);
                ctx.drawScreenFacingLine(new Vector3f(UP).mul(-halfSize), new Vector3f(UP).mul(halfSize), lineWidth, 0xff880000, MMRenderTypes.additiveSolidAlwaysVisible());
                ctx.drawScreenFacingLine(new Vector3f(RIGHT).mul(-halfSize), new Vector3f(RIGHT).mul(halfSize), lineWidth, 0xff880000, MMRenderTypes.additiveSolidAlwaysVisible());
                ctx.poseStack.translate(nextRandomNegPos1() * crossOffset, nextRandomNegPos1() * crossOffset, nextRandomNegPos1() * crossOffset);
                ctx.drawScreenFacingLine(new Vector3f(UP).mul(-halfSize), new Vector3f(UP).mul(halfSize), lineWidth, redShiftGreen, MMRenderTypes.additiveSolidAlwaysVisible());
                ctx.drawScreenFacingLine(new Vector3f(RIGHT).mul(-halfSize), new Vector3f(RIGHT).mul(halfSize), lineWidth, redShiftGreen, MMRenderTypes.additiveSolidAlwaysVisible());
                ctx.poseStack.translate(nextRandomNegPos1() * crossOffset, nextRandomNegPos1() * crossOffset, nextRandomNegPos1() * crossOffset);
                ctx.drawScreenFacingLine(new Vector3f(UP).mul(-halfSize), new Vector3f(UP).mul(halfSize), lineWidth, redShiftBlue, MMRenderTypes.additiveSolidAlwaysVisible());
                ctx.drawScreenFacingLine(new Vector3f(RIGHT).mul(-halfSize), new Vector3f(RIGHT).mul(halfSize), lineWidth, redShiftBlue, MMRenderTypes.additiveSolidAlwaysVisible());
            } else {
                ctx.drawScreenFacingLine(new Vector3f(UP).mul(-halfSize), new Vector3f(UP).mul(halfSize), lineWidth, 0xffaaaaaa, MMRenderTypes.alwaysVisibleSolid());
                ctx.drawScreenFacingLine(new Vector3f(RIGHT).mul(-halfSize), new Vector3f(RIGHT).mul(halfSize), lineWidth, 0xffaaaaaa, MMRenderTypes.alwaysVisibleSolid());
            }
            ctx.poseStack.popPose();
            ctx.poseStack.popPose();
        }
        ctx.drawScreenFacingLine(
                ZERO,
                new Vector3f(halfSize * 5, 0, 0), lineWidth,
                0xffff0000, MMRenderTypes.additiveSolidAlwaysVisible());
        ctx.drawScreenFacingLine(
                ZERO,
                new Vector3f(0, halfSize * 5, 0), lineWidth,
                0xff00ff00, MMRenderTypes.additiveSolidAlwaysVisible());
        ctx.drawScreenFacingLine(
                ZERO,
                new Vector3f(0, 0, halfSize * 5), lineWidth,
                0xff0000ff, MMRenderTypes.additiveSolidAlwaysVisible());
        ctx.poseStack.popPose();
        ctx.poseStack.popPose();
        // 渲染零件耐久度
        int color = getSubPartColorByDurability(subPart.getDurability(), subPart.getMaxDurability());
        drawAnimatedDurabilityBar(
                ctx,
                x, y + height,
                width, 3,
                subPart.getDurability() / subPart.getMaxDurability(),
                color,
                currentTime
        );
        String text = Math.round(subPart.getDurability()) + "/" + Math.round(subPart.getMaxDurability());
        int textW = ctx.font.width(text) + 2;
        ctx.drawText(
                Component.literal(text),
                x + width - textW,
                y + height - 3 - 5,
                Easing.lerpColorFromTransparent(color, progress)
        );
    }

    private void drawAnimatedDurabilityBar(
            Hud3DContext ctx,
            float x,
            float y,
            float width,
            float height,
            float targetProgress,
            int color,
            float currentTime
    ) {
        // 计算进度
        if (targetProgress != animatedDurabilityFloat.getTarget())
            animatedDurabilityFloat.animateTo(targetProgress, 0.5f, currentTime);
        animatedDurabilityFloat.update(currentTime);
        // 绘制进度条
        drawProgressBar(ctx, x, y, width, height, animatedDurabilityFloat.get(), color, false);
    }

    private void drawAnimatedProgressBar(
            Hud3DContext ctx,
            float x,
            float y,
            float width,
            float height,
            float targetProgress,
            int color,
            float currentTime,
            boolean additive
    ) {
        // 计算进度
        if (targetProgress != animatedProgressFloat.getTarget())
            animatedProgressFloat.animateTo(targetProgress, 0.5f, currentTime);
        animatedProgressFloat.update(currentTime);
        // 绘制进度条
        drawProgressBar(ctx, x, y, width, height, animatedProgressFloat.get(), color, additive);
    }

    private void drawProgressBar(
            Hud3DContext ctx,
            float x,
            float y,
            float width,
            float height,
            float progress,
            int color,
            boolean additive
    ) {
        // 进度条背景
        ctx.fill(x, y, x + width, y + height, BAR_BG, 0.002f, additive ? MMRenderTypes.additiveSolidDepth() : MMRenderTypes.alwaysVisibleSolid());
        // 进度条前景
        ctx.fill(x, y, x + progress * width, y + height, color, 0.001f, additive ? MMRenderTypes.additiveSolidDepth() : MMRenderTypes.alwaysVisibleSolid());
    }

    private void drawMaterialRow(
            Hud3DContext ctx,
            float hudX,
            float y,
            MaterialStatus m
    ) {
        float rowX = hudX + PADDING;
        float rowW = Math.max(animatedHudWidth.get() - PADDING * 2, 0);
        float rowH = MATERIAL_LINE_HEIGHT - 2;

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

    private void updateViewOrientation(SubPart subPart, Hud3DContext ctx) {
        var camPose = new Matrix4f(subPart.getWorldPositionMatrix(ctx.partialTicks)).invert();
        camPose.rotate(ctx.camera.rotation());
        FORWARD.mul(new Matrix3f(camPose), forward);
        UP.mul(new Matrix3f(camPose), up);
    }

    private Vector3f resolveViewTranslation(SubPart subPart) {
        var center = subPart.getBody().getCollisionShape().aabbCenter(null).mult(1);
        return SparkMathKt.toVector3f(center);
    }

    private float resolveViewScale(SubPart subPart, float width, float height, float scale) {
        width /= scale;
        height /= scale;
        var facingDir = projectionResolver.resolveFacingWithHysteresis(forward);
        var upDir = projectionResolver.resolveUpDir(forward, up, facingDir);
        var aabb = subPart.getBody().getCollisionShape().boundingBoxWithoutRecalculate(
                com.jme3.math.Vector3f.ZERO,
                com.jme3.math.Matrix3f.IDENTITY,
                null
        );
        float x = 2 * aabb.getXExtent();
        float y = 2 * aabb.getYExtent();
        float z = 2 * aabb.getZExtent();
        if (facingDir == ViewOrientationResolver.Facing.NEG_X || facingDir == ViewOrientationResolver.Facing.POS_X) {
            float yScale = height / y;
            float zScale = width / z;
            return Math.min(yScale, zScale);
        } else if (facingDir == ViewOrientationResolver.Facing.NEG_Z || facingDir == ViewOrientationResolver.Facing.POS_Z) {
            float xScale = width / x;
            float yScale = height / y;
            return Math.min(xScale, yScale);
        } else {
            float xScale;
            float zScale;
            if (upDir == ViewOrientationResolver.UpDir.NEG_X || upDir == ViewOrientationResolver.UpDir.POS_X) {
                xScale = height / x;
                zScale = width / z;
            } else {
                xScale = width / x;
                zScale = height / z;
            }
            return Math.min(xScale, zScale);
        }
    }

    private Quaternionf resolveViewOrientation() {
        return projectionResolver.resolveViewRotation(forward, up);
    }

    private int getSubsystemColorByDurability(float durability, float maxDurability) {
        float ratio = Math.clamp(durability / maxDurability, 0.0f, 1.0f);
        if (ratio >= 1.0f) {
            return SUBSYSTEM_HP_FULL.getRGB();
        } else if (ratio >= 0.8f) {
            return SUBSYSTEM_HP_80.getRGB();
        } else if (ratio >= 0.6f) {
            return SUBSYSTEM_HP_60.getRGB();
        } else if (ratio >= 0.4f) {
            return SUBSYSTEM_HP_40.getRGB();
        } else if (ratio >= 0.2f) {
            return SUBSYSTEM_HP_20.getRGB();
        } else if (ratio > 0.0f) {
            return SUBSYSTEM_HP_0.getRGB();
        } else return SUBSYSTEM_HP_DESTROYED.getRGB();
    }

    private int getSubPartColorByDurability(float durability, float maxDurability) {
        float ratio = Math.clamp(durability / maxDurability, 0.0f, 1.0f);
        if (ratio >= 0.5f) {
            return BAR_HP_FULL.getRGB();
        } else if (ratio >= 0.2f) {
            return BAR_HP_60.getRGB();
        } else if (ratio > 0.0f) {
            return BAR_HP_0.getRGB();
        } else return BAR_HP_DESTROYED.getRGB();
    }

    private float nextRandomNegPos1() {
        return random.nextFloat() * 2 - 1;
    }
}