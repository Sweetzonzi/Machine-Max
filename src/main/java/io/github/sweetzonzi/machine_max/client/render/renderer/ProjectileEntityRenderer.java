package io.github.sweetzonzi.machine_max.client.render.renderer;

import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelInstance;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.common.entity.MMProjectileEntity;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.awt.*;

public class ProjectileEntityRenderer extends EntityRenderer<MMProjectileEntity> {

    public ProjectileEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(MMProjectileEntity entity) {
        ModelController controller = entity.getModelController();
        if (controller != null) {
            return controller.getTextureLocation();
        }
        return ResourceLocation.withDefaultNamespace("textures/misc/underwater.png");
    }

    @Override
    public void render(@NotNull MMProjectileEntity entity, float entityYaw, float partialTick,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight) {
        if (entity.getProjectile() == null) return;

        ModelController modelController = entity.getModelController();
        if (modelController == null) return;

        ModelInstance modelInstance = modelController.getModel();
        if (modelInstance == null) return;

        ResourceLocation texture = getTextureLocation(entity);

        DestroyableObject projectile = (DestroyableObject) entity.getProjectile();
        Matrix4f worldMatrix = projectile.getWorldPositionMatrix(partialTick);

        Vec3 pos = entity.getPosition(partialTick);
        BlockPos blockPos = BlockPos.containing(pos);
        int blockLight = this.getBlockLightLevel(entity, blockPos);
        int skyLight = this.getSkyLightLevel(entity, blockPos);
        int light = LightTexture.pack(blockLight, skyLight);
        int overlay = OverlayTexture.NO_OVERLAY;
        int color = Color.WHITE.getRGB();

        poseStack.pushPose();
        poseStack.translate(-pos.x, -pos.y, -pos.z);
        poseStack.pushPose();
        poseStack.mulPose(worldMatrix);

        for (OBone bone : modelInstance.getOrigin().getBones().values()) {
            ModelRenderHelperKt.render(
                    bone,
                    modelInstance.getPose(),
                    poseStack,
                    bufferSource.getBuffer(RenderType.entityTranslucent(texture)),
                    light,
                    overlay,
                    color,
                    partialTick,
                    false
            );
        }

        poseStack.popPose();
        poseStack.popPose();
    }
}
