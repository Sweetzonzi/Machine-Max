package io.github.tt432.machinemax.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.tt432.eyelib.Eyelib;
import io.github.tt432.eyelib.capability.RenderData;
import io.github.tt432.eyelib.capability.component.AnimationComponent;
import io.github.tt432.eyelib.client.ClientTickHandler;
import io.github.tt432.eyelib.client.animation.BrAnimator;
import io.github.tt432.eyelib.client.loader.BrModelLoader;
import io.github.tt432.eyelib.client.render.RenderHelper;
import io.github.tt432.eyelib.client.render.RenderParams;
import io.github.tt432.eyelib.client.render.bone.BoneRenderInfos;
import io.github.tt432.machinemax.common.entity.MMPartEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.ode4j.math.DQuaternionC;

public class PartEntityRenderer extends EntityRenderer<MMPartEntity> {

    protected PartEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(MMPartEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        if (entity.part == null) return;
        DQuaternionC dq = entity.part.rootBody.getBody().getQuaternion().copy();
        Quaternionf q = new Quaternionf(dq.get1(), dq.get2(), dq.get3(), dq.get0());
        poseStack.pushPose();//开始渲染
        poseStack.mulPose(q);//旋转
        RenderType renderType = RenderType.entityCutout(ResourceLocation.parse("eyelib:entity_cutout"));
        AnimationComponent animationComponent = RenderData.getComponent(entity).getAnimationComponent();//获取已有的动画数据
        animationComponent.setup(null, null);
        BoneRenderInfos infos = BrAnimator.tickAnimation(animationComponent,
                entity.part.molangScope.getScope(), ClientTickHandler.getTick() + partialTick);
        RenderParams renderParams = new RenderParams(//渲染参数
                entity,
                poseStack.last().copy(),
                poseStack,
                renderType,
                null,
                false,
                buffer.getBuffer(renderType),
                packedLight,
                OverlayTexture.NO_OVERLAY//控制受伤变红与tnt爆炸前闪烁，载具不需要这个
        );
        RenderHelper renderHelper = Eyelib.getRenderHelper();
        renderHelper.render(//渲染模型
                renderParams,
                BrModelLoader.getModel(null),
                infos
        );
        poseStack.popPose();//结束渲染
    }

    @Override
    public ResourceLocation getTextureLocation(MMPartEntity entity) {
        return null;
    }

}
