package io.github.sweetzonzi.machine_max.client.render.renderer;

import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer;
import com.mojang.blaze3d.vertex.*;
import io.github.sweetzonzi.machine_max.client.render.MMRenderTypes;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 投射物曳光渲染器（仅客户端）。
 * <p>
 * 在 {@link RenderLevelStageEvent.Stage#AFTER_TRANSLUCENT_BLOCKS} 阶段绘制射弹运动方向线段。
 * 起点位置与拖尾方向都取自 {@link DestroyableObject#getWorldPositionMatrix}（平移 lerp + 旋转
 * slerp）：投射物的姿态由 {@code ProjectileManager} 写成速度方向，因此方向与位置同源、同
 * {@code partialTick}，制导弹大幅改向时不会逐 tick 跳变。拖尾长度另取 SoA 当前速率乘
 * {@code tracer_length}，速率不做插值。
 * 颜色和透明度取自 JSON 配置的 {@code tracer_color / tracer_alpha}。
 */
public class ClientProjectileRenderer extends VisualEffectRenderer {

    @Override
    public void tick() {
    }

    @Override
    public void physTick(@NotNull PhysicsLevel physicsLevel) {
    }

    @Override
    public RenderLevelStageEvent.Stage getRenderStage() {
        return RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
    }

    @Override
    public void render(@NotNull RenderLevelStageEvent event, @NotNull MultiBufferSource bufferSource, float partialTick) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        ProjectileManager pm = ObjectManager.levelProjectileManagers.get(level);
        if (pm == null) return;

        // 通过 poseStack 处理相机偏移
        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // 复用 Vector3f 避免热路径重复分配
        Vector3f tmpPos = new Vector3f();
        Vector3f tmpDir = new Vector3f();

        for (int i = 0; i < pm.count; i++) {
            // 不检查 alive：已死的条目由 ProjectileManager.deadRetained 在数组里多留一次清理扫描，
            // 这一帧要把它画出来，否则"出膛即命中销毁"的投射物完全没有曳光
            ProjectileType type = pm.getProjectileTypeByIndex(i);
            Vec3i tracerColor = type.getTracerColor();
            int tracerAlpha = type.getTracerAlpha();
            if (tracerAlpha <= 0) continue;

            // 通过 ProjectileManager 便捷方法获取投射物对象并使用插值位姿
            DestroyableObject obj = pm.getProjectile(pm.objId[i]);
            if (obj == null) continue;

            Matrix4f worldMatrix = obj.getWorldPositionMatrix(partialTick);
            worldMatrix.getTranslation(tmpPos);

            // 颜色/透明度：加法混合下 alpha 控制发光强度
            float r = tracerColor.getX() / 255f;
            float g = tracerColor.getY() / 255f;
            float b = tracerColor.getZ() / 255f;
            float a = tracerAlpha / 255f;

            // 即将超时时减弱发光强度
            if (pm.lifetime[i] < 20) {
                a *= pm.lifetime[i] / 20f;
            }

            // 按投射物类型配置获取线宽和拖尾长度
            var buffer = bufferSource.getBuffer(MMRenderTypes.tracerLine(type.getTracerWidth()));

            // 拖尾方向：取帧间插值后的姿态（局部前向 -Z），与起点位置同源、同 partialTick
            worldMatrix.transformDirection(tmpDir.set(0f, 0f, -1f));

            // 拖尾长度：速率仍取 SoA 当前值，不做插值。助推段速率逐 tick 变化会让线长轻微
            // 跳动，这是"方向平滑优先"下明确接受的取舍
            float speed = (float) Math.sqrt(
                    pm.velX[i] * pm.velX[i] + pm.velY[i] * pm.velY[i] + pm.velZ[i] * pm.velZ[i]);
            float length = speed * (float) type.getTracerLength();

            float endX = tmpPos.x + tmpDir.x * length;
            float endY = tmpPos.y + tmpDir.y * length;
            float endZ = tmpPos.z + tmpDir.z * length;

            buffer.addVertex(poseStack.last().pose(), tmpPos.x, tmpPos.y, tmpPos.z)
                    .setColor(r, g, b, a);
            buffer.addVertex(poseStack.last().pose(), endX, endY, endZ)
                    .setColor(r, g, b, a);
        }

        poseStack.popPose();
    }
}
