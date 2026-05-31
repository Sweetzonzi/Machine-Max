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
 * 通过 {@link DestroyableObject#getWorldPositionMatrix} 获取帧间插值位置，
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

        var buffer = bufferSource.getBuffer(MMRenderTypes.TRACER_LINE);

        // 复用 Vector3f 避免热路径重复分配
        Vector3f tmpPos = new Vector3f();

        for (int i = 0; i < pm.count; i++) {
            if (!pm.alive[i]) continue;

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
            if (pm.lifetime[i] < 10) {
                a *= pm.lifetime[i] / 10f;
            }

            // 方向：使用 SoA 速度矢量推算下一帧位置
            float dirX = pm.velX[i] * 0.05f;
            float dirY = pm.velY[i] * 0.05f;
            float dirZ = pm.velZ[i] * 0.05f;

            float endX = tmpPos.x + dirX;
            float endY = tmpPos.y + dirY;
            float endZ = tmpPos.z + dirZ;

            buffer.addVertex(poseStack.last().pose(), tmpPos.x, tmpPos.y, tmpPos.z)
                    .setColor(r, g, b, a);
            buffer.addVertex(poseStack.last().pose(), endX, endY, endZ)
                    .setColor(r, g, b, a);
        }

        poseStack.popPose();
    }
}
