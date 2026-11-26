package io.github.sweetzonzi.machine_max.client.render.renderer;

import cn.solarmoon.spark_core.animation.model.ModelPose;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.common.mech.part.AbstractPart;
import io.github.sweetzonzi.machine_max.common.mech.part.PartStaleness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.HashMap;
import java.util.Map;

/** 将选中的累计做旧贴图叠加绘制在部件现有模型几何上。 */
public final class PartWearRenderer {
    private static ResourceManager cachedResourceManager;
    private static final Map<ResourceLocation, Boolean> textureAvailability = new HashMap<>();

    private PartWearRenderer() {
    }

    /** 按部件陈旧度选择阶段贴图，并使用当前模型姿态绘制叠加层。 */
    public static void render(AbstractPart<?> part, Map<String, OBone> bones, ModelPose pose,
                              float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                              int packedLight, int overlay, int color, boolean translucent) {
        int stage = PartStaleness.stage(part.getStaleness());
        if (stage == 0) return;

        ResourceLocation texture = getStageTexture(part.getModelController().getTextureLocation(), stage);
        if (!textureExists(texture)) return;

        var renderType = translucent ? RenderType.entityTranslucent(texture) : RenderType.entityCutout(texture);
        var consumer = bufferSource.getBuffer(renderType);
        for (OBone bone : bones.values()) {
            if (bone.getShouldGlow()) continue;
            ModelRenderHelperKt.render(bone, pose, poseStack, consumer, packedLight,
                    overlay, color, partialTick, false);
        }
    }

    /** 在底色贴图扩展名之前插入两位数的做旧阶段后缀。 */
    static ResourceLocation getStageTexture(ResourceLocation baseTexture, int stage) {
        String path = baseTexture.getPath();
        String extension = path.endsWith(".png") ? ".png" : "";
        String stem = extension.isEmpty() ? path : path.substring(0, path.length() - extension.length());
        String suffix = stage < 10 ? "_wear_0" + stage : "_wear_" + stage;
        return ResourceLocation.fromNamespaceAndPath(baseTexture.getNamespace(), stem + suffix + extension);
    }

    /** 查询并缓存阶段贴图是否存在；资源管理器更换时清空旧缓存。 */
    private static boolean textureExists(ResourceLocation texture) {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        if (manager != cachedResourceManager) {
            cachedResourceManager = manager;
            textureAvailability.clear();
        }
        return textureAvailability.computeIfAbsent(texture, key -> manager.getResource(key).isPresent());
    }
}
