package io.github.sweetzonzi.machine_max.client.render.renderer;

import cn.solarmoon.spark_core.animation.model.ModelInstance;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.SparkMathKt;
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sweetzonzi.machine_max.client.render.MMRenderTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.BasicSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Brightness;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Map;

/**
 * 载具内构查看渲染器 — 按住 O 键时，渲染玩家当前乘坐载具的子系统 HitBox 耐久着色和连接点状态。
 * <p>子系统配色沿用 {@link io.github.sweetzonzi.machine_max.client.render.gui.hud3d.AssemblyHud3D} 的绿→红渐变。</p>
 * <p>连接点用三根纯色线（X=红, Y=绿, Z=蓝）构成坐标轴十字标记，完整性降低时各轴沿自身方向偏移。</p>
 * <p>在 {@link RenderLevelStageEvent.Stage#AFTER_ENTITIES} 阶段执行，绘制在实体之上。</p>
 */
public class VehicleInspectorRenderer extends VisualEffectRenderer {

    /** 内构查看模式是否激活，由 RawInputHandler 设置 */
    @Getter
    public static volatile boolean inspecting = false;

    /** 十字臂长（世界单位） */
    private static final float CROSS_HALF_SIZE = 0.25f;
    /** 抖动最大范围（世界单位） */
    private static final float JITTER_MAX = 0.25f;

    /** 子系统耐久配色：绿 → 黄绿 → 黄 → 橙 → 红橙 → 红 → 暗灰（与 AssemblyHud3D 一致） */
    private static final Color SUBSYSTEM_HP_FULL = new Color(100, 255, 100);
    private static final Color SUBSYSTEM_HP_80 = new Color(255, 255, 64);
    private static final Color SUBSYSTEM_HP_60 = new Color(255, 255, 0);
    private static final Color SUBSYSTEM_HP_40 = new Color(255, 128, 0);
    private static final Color SUBSYSTEM_HP_20 = new Color(255, 64, 0);
    private static final Color SUBSYSTEM_HP_0 = new Color(255, 0, 0);
    private static final Color SUBSYSTEM_HP_DESTROYED = new Color(50, 50, 50);

    /** RGB 三轴颜色（纯色，不透明） */
    private static final int RED_LINE = new Color(255, 0, 0).getRGB();    // X 轴 — 红
    private static final int GREEN_LINE = new Color(0, 255, 0).getRGB();  // Y 轴 — 绿
    private static final int BLUE_LINE = new Color(0, 0, 255).getRGB();   // Z 轴 — 蓝

    @Override
    public void tick() {
    }

    @Override
    public void physTick(@NotNull PhysicsLevel physicsLevel) {
    }

    @Override
    public @NotNull RenderLevelStageEvent.Stage getRenderStage() {
        return RenderLevelStageEvent.Stage.AFTER_ENTITIES;
    }

    @Override
    public void render(@NotNull RenderLevelStageEvent event, @NotNull MultiBufferSource bufferSource, float partialTick) {
        if (!inspecting) return;
        VehicleCore vehicle = getPlayerVehicle();
        if (vehicle == null) return;

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        for (Part part : vehicle.partMap.values()) {
            for (SubPart subPart : part.subParts.values()) {
                if (subPart.isRemoved() || subPart.isDestroyed()) continue;

                var modelController = subPart.getModelController();
                var modelInstance = modelController.getModel();
                if (modelInstance == null) continue;

                poseStack.pushPose();
                poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

                renderSubsystemHitBoxes(subPart, modelInstance, poseStack, bufferSource, partialTick);
                renderConnectorPoints(subPart, poseStack, bufferSource, partialTick);

                poseStack.popPose();
            }
        }
    }

    /**
     * 从当前客户端玩家获取乘坐的载具。
     */
    @Nullable
    private static VehicleCore getPlayerVehicle() {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;
        if (!(player instanceof IEntityMixin mixin)) return null;
        if (!(mixin.machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat)) return null;
        if (!(seat.owner instanceof SubPart ownerSubPart)) return null;
        return (VehicleCore) ownerSubPart.part.assembly;
    }

    /* ==================== 子系统 HitBox 渲染 ==================== */

    /**
     * 渲染 SubPart 上所有子系统对应的 HitBox 骨骼，颜色由子系统耐久度决定。
     */
    private void renderSubsystemHitBoxes(SubPart subPart, ModelInstance modelInstance,
                                         PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        Map<String, OBone> bones = subPart.getBones();
        poseStack.pushPose();
        poseStack.mulPose(subPart.getRenderWorldPositionMatrix(partialTick));
        poseStack.scale(1.01f, 1.01f, 1.01f); // 避免z-fighting
        for (Map.Entry<String, HitBoxAttr> entry : subPart.attr.getHitBoxes().entrySet()) {
            String boneName = entry.getKey();
            String subsystemName = entry.getValue().getSubsystem();
            if (!bones.containsKey(boneName) || subsystemName.isEmpty()) continue;

            AbstractSubsystem subsystem = subPart.subsystems.get(subsystemName);
            if (!(subsystem instanceof BasicSubsystem basicSubsystem) || basicSubsystem.isHidden()) continue;

            int color = getSubsystemColorByDurability(subsystem.getDurability(), subsystem.getMaxDurability());
            ModelRenderHelperKt.render(
                    bones.get(boneName),
                    modelInstance.getPose(),
                    poseStack,
                    bufferSource.getBuffer(RenderType.debugQuads()),
                    Brightness.FULL_BRIGHT.pack(),
                    OverlayTexture.NO_OVERLAY,
                    color,
                    partialTick,
                    true
            );
        }
        poseStack.popPose();
    }

    /* ==================== 连接点渲染 ==================== */

    /**
     * 渲染 SubPart 上所有外部连接点标记。
     * 每个连接点用 9 根线（3 轴 × 3 色 RGB）绘制十字。
     * 完整性 = 100% 时三线重叠呈白色，受损时 RGB 分离形成印刷错位效果。
     */
    private void renderConnectorPoints(SubPart subPart, PoseStack poseStack,
                                       MultiBufferSource bufferSource, float partialTick) {
        var lineBuffer = bufferSource.getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.mulPose(subPart.getWorldPositionMatrix(partialTick));
        for (AbstractConnector connector : subPart.connectors.values()) {
            if (connector.isInternal()) continue;

            float integrity = connector.getIntegrity();
            float maxIntegrity = connector.getBasicIntegrity();
            float damageRatio = maxIntegrity > 0 ? 1f - Math.clamp(integrity / maxIntegrity, 0f, 1f) : 0f;
            float jitter = damageRatio * JITTER_MAX;

            // 应用连接器相对质心的偏移
            poseStack.pushPose();
            poseStack.mulPose(SparkMathKt.toMatrix4f(connector.getOffsetFromMassCenter().toTransformMatrix()));

            // 三轴三色 = 3 根线
            drawAxisCross(lineBuffer, poseStack, jitter);

            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /**
     * 在当前位置绘制三轴十字，每轴一根纯色线。
     * 完整性降低时各轴沿自身方向偏移，形成"结构松动"的视觉错位。
     */
    private void drawAxisCross(VertexConsumer lineBuffer, PoseStack poseStack, float jitter) {
        var matrix = poseStack.last().pose();
        float hs = CROSS_HALF_SIZE;
        // X 轴 — 红，沿 X 偏移
        addLineVertex(lineBuffer, matrix, -hs + jitter, 0, 0, hs + jitter, 0, 0, RED_LINE);
        // Y 轴 — 绿，沿 Y 偏移
        addLineVertex(lineBuffer, matrix, 0, -hs + jitter, 0, 0, hs + jitter, 0, GREEN_LINE);
        // Z 轴 — 蓝，沿 Z 偏移
        addLineVertex(lineBuffer, matrix, 0, 0, -hs + jitter, 0, 0, hs + jitter, BLUE_LINE);
    }

    private static void addLineVertex(VertexConsumer buffer, org.joml.Matrix4f matrix,
                                      float x1, float y1, float z1, float x2, float y2, float z2, int argb) {
        float a = (argb >>> 24) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(0, 0, 1);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(0, 0, 1);
    }

    /* ==================== 子系统耐久配色 ==================== */

    private static int getSubsystemColorByDurability(float durability, float maxDurability) {
        float ratio = maxDurability > 0 ? Math.clamp(durability / maxDurability, 0f, 1f) : 1f;
        if (ratio >= 1.0f)       return SUBSYSTEM_HP_FULL.getRGB();
        else if (ratio >= 0.8f)  return SUBSYSTEM_HP_80.getRGB();
        else if (ratio >= 0.6f)  return SUBSYSTEM_HP_60.getRGB();
        else if (ratio >= 0.4f)  return SUBSYSTEM_HP_40.getRGB();
        else if (ratio >= 0.2f)  return SUBSYSTEM_HP_20.getRGB();
        else if (ratio > 0.0f)   return SUBSYSTEM_HP_0.getRGB();
        else                     return SUBSYSTEM_HP_DESTROYED.getRGB();
    }
}
