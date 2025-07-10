package io.github.sweetzonzi.machinemax.client.gui.renderable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.List;
import java.util.Map;

@Getter
public class RenderableAttr {
    public final ResourceLocation model;
    public final ResourceLocation animation;
    public final ResourceLocation texture;
    public final Vec3 offset;
    public final Vec3 scale;
    public final Vec3 rotation;
    public final Vec3i color;
    public final int transparency;
    public final boolean perspective;
    public final Map<String, TextAttr> textAttr;
    public final boolean enableScissor;
    public final int scissorX;
    public final int scissorY;
    public final int scissorWidth;
    public final int scissorHeight;

    public record TextAttr(
            String key,
            boolean centered,
            boolean shadow,
//            ResourceLocation font,//TODO:自选字体
            Vec3 scale,
            Vec3i color,
            int transparency,
            Vec3i backgroundColor,
            int backgroundTransparency,
            List<String> molangArgs,
            int significand
    ) {
        public static final Codec<TextAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("key").forGetter(TextAttr::key),
                Codec.BOOL.optionalFieldOf("centered", true).forGetter(TextAttr::centered),
                Codec.BOOL.optionalFieldOf("shadow", false).forGetter(TextAttr::shadow),
//                ResourceLocation.CODEC.fieldOf("font").forGetter(TextAttr::font),
                Vec3.CODEC.optionalFieldOf("scale", new Vec3(1, 1, 1)).forGetter(TextAttr::scale),
                Vec3i.CODEC.optionalFieldOf("color", new Vec3i(255, 255, 255)).forGetter(TextAttr::color),
                Codec.INT.optionalFieldOf("transparency", 255).forGetter(TextAttr::transparency),
                Vec3i.CODEC.optionalFieldOf("background_color", new Vec3i(0, 0, 0)).forGetter(TextAttr::backgroundColor),
                Codec.INT.optionalFieldOf("background_transparency", 0).forGetter(TextAttr::backgroundTransparency),
                Codec.STRING.listOf().optionalFieldOf("molang_args", List.of()).forGetter(TextAttr::molangArgs),
                Codec.INT.optionalFieldOf("significand", 0).forGetter(TextAttr::significand)
        ).apply(instance, TextAttr::new));

        public static final Codec<Map<String, TextAttr>> MAP_CODEC = Codec.unboundedMap(Codec.STRING, TextAttr.CODEC);

        public int getColor() {
            return new Color(color.getX(), color.getY(), color.getZ(), transparency).getRGB();
        }

        public int getBackgroundColor() {
            return new Color(backgroundColor.getX(), backgroundColor.getY(), backgroundColor.getZ(), backgroundTransparency).getRGB();
        }
    }

    public static final Codec<RenderableAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("model").forGetter(RenderableAttr::getModel),
            ResourceLocation.CODEC.fieldOf("animation").forGetter(RenderableAttr::getAnimation),
            ResourceLocation.CODEC.fieldOf("texture").forGetter(RenderableAttr::getTexture),
            Vec3.CODEC.optionalFieldOf("offset", Vec3.ZERO).forGetter(RenderableAttr::getOffset),
            Vec3.CODEC.optionalFieldOf("rotation", Vec3.ZERO).forGetter(RenderableAttr::getRotation),
            Vec3.CODEC.optionalFieldOf("scale", new Vec3(20, 20, 20)).forGetter(RenderableAttr::getScale),
            Vec3i.CODEC.optionalFieldOf("color", new Vec3i(255, 255, 255)).forGetter(RenderableAttr::getColor),
            Codec.INT.optionalFieldOf("alpha", 255).forGetter(RenderableAttr::getTransparency),
            Codec.BOOL.optionalFieldOf("perspective", true).forGetter(RenderableAttr::isPerspective),
            TextAttr.MAP_CODEC.optionalFieldOf("texts", Map.of()).forGetter(RenderableAttr::getTextAttr),
            Codec.BOOL.optionalFieldOf("enable_scissor", false).forGetter(RenderableAttr::isEnableScissor),
            Codec.INT.optionalFieldOf("scissor_x", 0).forGetter(RenderableAttr::getScissorX),
            Codec.INT.optionalFieldOf("scissor_y", 0).forGetter(RenderableAttr::getScissorY),
            Codec.INT.optionalFieldOf("scissor_width", 0).forGetter(RenderableAttr::getScissorWidth),
            Codec.INT.optionalFieldOf("scissor_height", 0).forGetter(RenderableAttr::getScissorHeight)
    ).apply(instance, RenderableAttr::new));

    public RenderableAttr(ResourceLocation model, ResourceLocation animation, ResourceLocation texture,
                          Vec3 offset, Vec3 rotation, Vec3 scale,
                          Vec3i color, int transparency,
                          boolean perspective,
                          Map<String, TextAttr> textAttr,
                          boolean enableScissor, int scissorX, int scissorY, int scissorWidth, int scissorHeight) {
        this.model = model;
        this.animation = animation;
        this.texture = texture;
        this.offset = offset;
        this.scale = scale;
        this.rotation = rotation;
        this.color = color;
        this.transparency = transparency;
        this.perspective = perspective;
        this.textAttr = textAttr;
        this.enableScissor = enableScissor;
        this.scissorX = scissorX;
        this.scissorY = scissorY;
        this.scissorWidth = scissorWidth;
        this.scissorHeight = scissorHeight;
    }

    public RenderableAttr(ResourceLocation model, ResourceLocation animation, ResourceLocation texture,
                          Vec3 offset, Vec3 rotation, Vec3 scale,
                          boolean perspective,
                          Map<String, TextAttr> textAttr) {
        this.model = model;
        this.animation = animation;
        this.texture = texture;
        this.offset = offset;
        this.rotation = rotation;
        this.scale = scale;
        this.color = new Vec3i(255, 255, 255);
        this.transparency = 255;
        this.perspective = perspective;
        this.textAttr = textAttr;
        this.enableScissor = false;
        this.scissorX = 0;
        this.scissorY = 0;
        this.scissorWidth = 0;
        this.scissorHeight = 0;
    }

    public RenderableAttr(ResourceLocation model, ResourceLocation animation, ResourceLocation texture,
                          Vec3 offset, Vec3 rotation, double scale,
                          boolean perspective,
                          Map<String, TextAttr> textAttr) {
        this.model = model;
        this.animation = animation;
        this.texture = texture;
        this.offset = offset;
        this.rotation = rotation;
        this.scale = new Vec3(scale, scale, scale);
        this.color = new Vec3i(255, 255, 255);
        this.transparency = 255;
        this.perspective = perspective;
        this.textAttr = textAttr;
        this.enableScissor = false;
        this.scissorX = 0;
        this.scissorY = 0;
        this.scissorWidth = 0;
        this.scissorHeight = 0;
    }

    public RenderableAttr(ResourceLocation model, ResourceLocation animation, ResourceLocation texture,
                          Vec3 offset, double scale,
                          boolean perspective,
                          Map<String, TextAttr> textAttr) {
        this.model = model;
        this.animation = animation;
        this.texture = texture;
        this.offset = offset;
        this.rotation = Vec3.ZERO;
        this.scale = new Vec3(scale, scale, scale);
        this.color = new Vec3i(255, 255, 255);
        this.transparency = 255;
        this.perspective = perspective;
        this.textAttr = textAttr;
        this.enableScissor = false;
        this.scissorX = 0;
        this.scissorY = 0;
        this.scissorWidth = 0;
        this.scissorHeight = 0;
    }

    public RenderableAttr(ResourceLocation model, ResourceLocation animation, ResourceLocation texture,
                          Vec3 offset,
                          boolean perspective,
                          Map<String, TextAttr> textAttr) {
        this.model = model;
        this.animation = animation;
        this.texture = texture;
        this.offset = offset;
        this.rotation = Vec3.ZERO;
        this.scale = new Vec3(20, 20, 20);
        this.color = new Vec3i(255, 255, 255);
        this.transparency = 255;
        this.perspective = perspective;
        this.textAttr = textAttr;
        this.enableScissor = false;
        this.scissorX = 0;
        this.scissorY = 0;
        this.scissorWidth = 0;
        this.scissorHeight = 0;
    }

    public RenderableAttr(ResourceLocation model, ResourceLocation animation, ResourceLocation texture,
                          boolean perspective) {
        this.model = model;
        this.animation = animation;
        this.texture = texture;
        this.offset = Vec3.ZERO;
        this.rotation = Vec3.ZERO;
        this.scale = new Vec3(20, 20, 20);
        this.color = new Vec3i(255, 255, 255);
        this.transparency = 255;
        this.perspective = perspective;
        this.textAttr = Map.of();
        this.enableScissor = false;
        this.scissorX = 0;
        this.scissorY = 0;
        this.scissorWidth = 0;
        this.scissorHeight = 0;
    }
}
