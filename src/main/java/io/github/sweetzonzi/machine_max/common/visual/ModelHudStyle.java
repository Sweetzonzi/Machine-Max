package io.github.sweetzonzi.machine_max.common.visual;

import cn.solarmoon.spark_core.animation.model.ModelIndex;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;
import java.util.Map;

/**
 * 模型载体样式：基岩模型 + 动画 + 纹理 + 文本槽 + 裁剪。
 * <p>
 * 投放（offset/rotation/scale/perspective/scope_behavior/ignore_zoom）不在样式里，
 * 由引用它的 {@link HudElement} 提供。
 */
public record ModelHudStyle(
        ResourceLocation model,
        ResourceLocation animation,
        ResourceLocation texture,
        Vec3i color,
        int alpha,
        HudStyle.ScissorParams scissor,
        Map<String, HudStyle.TextParams> texts
) implements HudStyle {

    public static final MapCodec<ModelHudStyle> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("model").forGetter(ModelHudStyle::model),
            ResourceLocation.CODEC.fieldOf("animation").forGetter(ModelHudStyle::animation),
            ResourceLocation.CODEC.fieldOf("texture").forGetter(ModelHudStyle::texture),
            Vec3i.CODEC.optionalFieldOf("color", new Vec3i(255, 255, 255)).forGetter(ModelHudStyle::color),
            com.mojang.serialization.Codec.INT.optionalFieldOf("alpha", 255).forGetter(ModelHudStyle::alpha),
            HudStyle.ScissorParams.CODEC.optionalFieldOf("scissor", HudStyle.ScissorParams.DEFAULT).forGetter(ModelHudStyle::scissor),
            HudStyle.TextParams.MAP_CODEC.optionalFieldOf("texts", Map.of()).forGetter(ModelHudStyle::texts)
    ).apply(instance, ModelHudStyle::new));

    /** 模型索引；HUD 模型的类型段固定为 {@code hud}。 */
    public ModelIndex modelIndex() {
        return new ModelIndex("hud", model);
    }

    /** 整体染色（含 alpha）。 */
    public int colorArgb() {
        return new Color(color.getX(), color.getY(), color.getZ(), alpha).getRGB();
    }

    @Override
    public ResourceLocation type() {
        return MODEL_HUD;
    }

    @Override
    public MapCodec<? extends HudStyle> codec() {
        return CODEC;
    }
}
