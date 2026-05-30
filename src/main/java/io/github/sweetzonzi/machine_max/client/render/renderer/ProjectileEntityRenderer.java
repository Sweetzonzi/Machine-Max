package io.github.sweetzonzi.machine_max.client.render.renderer;

import io.github.sweetzonzi.machine_max.common.entity.ProjectileEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 投射物实体的空渲染器（阶段一占位）。
 * <p>
 * 投射物的视觉表现当前由 {@code ClientProjectileRenderer} 以 Debug 线条绘制，
 * 此渲染器仅确保 EntityTracker 客户端初始化正常。阶段三引入 Blockbench 模型后替换。
 */
public class ProjectileEntityRenderer extends EntityRenderer<ProjectileEntity> {

    public ProjectileEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ProjectileEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/underwater.png");
    }
}
