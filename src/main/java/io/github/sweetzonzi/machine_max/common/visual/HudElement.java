package io.github.sweetzonzi.machine_max.common.visual;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * HUD 布局中的一个条目：样式引用 + 一组完整投放值。
 * <p>
 * 元素内联在 {@link HudLayout} 里，不是具名资源；同一份样式可以在一个布局中出现多次，
 * 各自带不同的投放。
 * <p>
 * {@code anchor} 只对 AUI 载体生效，默认 {@link HudAnchor#TOP_LEFT}；模型载体忽略该字段。
 */
public record HudElement(
        ResourceLocation style,
        boolean perspective,
        ScopeBehavior scopeBehavior,
        boolean ignoreZoom,
        Vec3 offset,
        Vec3 rotation,
        Vec3 scale,
        HudAnchor anchor
) {

    public static final Codec<HudElement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("style").forGetter(HudElement::style),
            Codec.BOOL.optionalFieldOf("perspective", true).forGetter(HudElement::perspective),
            ScopeBehavior.CODEC.optionalFieldOf("scope_behavior", ScopeBehavior.FOLLOW_POSITION).forGetter(HudElement::scopeBehavior),
            Codec.BOOL.optionalFieldOf("ignore_zoom", false).forGetter(HudElement::ignoreZoom),
            Vec3.CODEC.optionalFieldOf("offset", Vec3.ZERO).forGetter(HudElement::offset),
            Vec3.CODEC.optionalFieldOf("rotation", Vec3.ZERO).forGetter(HudElement::rotation),
            Vec3.CODEC.optionalFieldOf("scale", new Vec3(1, 1, 1)).forGetter(HudElement::scale),
            HudAnchor.CODEC.optionalFieldOf("anchor", HudAnchor.TOP_LEFT).forGetter(HudElement::anchor)
    ).apply(instance, HudElement::new));
}
