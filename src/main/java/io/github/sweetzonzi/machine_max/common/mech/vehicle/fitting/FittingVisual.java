package io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * 挂件的外观：单节模型与贴图。
 *
 * <p>两个字段都必填。模型按 {@code ModelIndex("fitting", model)} 从内容包的
 * {@code models/fitting/} 加载，与零件模型共用同一套 {@code OModel}；贴图路径是该模型
 * 使用的完整贴图位置，不从模型路径推导。</p>
 *
 * @param model   单节模型 id，如 {@code machine_max:track_link}
 * @param texture 模型贴图的完整路径，如 {@code machine_max:textures/part/track_link.png}
 */
public record FittingVisual(ResourceLocation model, ResourceLocation texture) {
    public static final Codec<FittingVisual> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("model").forGetter(FittingVisual::model),
            ResourceLocation.CODEC.fieldOf("texture").forGetter(FittingVisual::texture)
    ).apply(instance, FittingVisual::new));
}
