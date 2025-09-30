package io.github.sweetzonzi.machine_max.common.visual;

import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Transform;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.*;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AnimatableParams {
    public ModelIndex modelIndex;
    public ResourceLocation animation;
    public ResourceLocation texture;
    public Transform transform = new Transform();
    public Transform lastTransform;
    public Vec3i color;
    public int transparency;
    public boolean perspective;
    public Map<String, TextParams> textAttr;
    public boolean enableScissor;
    public int scissorX;
    public int scissorY;
    public int scissorWidth;
    public int scissorHeight;

    public record TextParams(
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
        public static final Codec<TextParams> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("key").forGetter(TextParams::key),
                Codec.BOOL.optionalFieldOf("centered", true).forGetter(TextParams::centered),
                Codec.BOOL.optionalFieldOf("shadow", false).forGetter(TextParams::shadow),
//                ResourceLocation.CODEC.fieldOf("font").forGetter(TextParams::font),
                Vec3.CODEC.optionalFieldOf("scale", new Vec3(1, 1, 1)).forGetter(TextParams::scale),
                Vec3i.CODEC.optionalFieldOf("color", new Vec3i(255, 255, 255)).forGetter(TextParams::color),
                Codec.INT.optionalFieldOf("transparency", 255).forGetter(TextParams::transparency),
                Vec3i.CODEC.optionalFieldOf("background_color", new Vec3i(0, 0, 0)).forGetter(TextParams::backgroundColor),
                Codec.INT.optionalFieldOf("background_transparency", 0).forGetter(TextParams::backgroundTransparency),
                Codec.STRING.listOf().optionalFieldOf("molang_args", List.of()).forGetter(TextParams::molangArgs),
                Codec.INT.optionalFieldOf("significand", 0).forGetter(TextParams::significand)
        ).apply(instance, TextParams::new));

        public static final Codec<Map<String, TextParams>> MAP_CODEC = Codec.unboundedMap(Codec.STRING, TextParams.CODEC);

        public int getColor() {
            return new Color(color.getX(), color.getY(), color.getZ(), transparency).getRGB();
        }

        public int getBackgroundColor() {
            return new Color(backgroundColor.getX(), backgroundColor.getY(), backgroundColor.getZ(), backgroundTransparency).getRGB();
        }
    }

    public static final Codec<AnimatableParams> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("model").forGetter(AnimatableParams::getModel),
            ResourceLocation.CODEC.fieldOf("animation").forGetter(AnimatableParams::getAnimation),
            ResourceLocation.CODEC.fieldOf("texture").forGetter(AnimatableParams::getTexture),
            Vec3.CODEC.optionalFieldOf("offset", Vec3.ZERO).forGetter(AnimatableParams::getOffset),
            Vec3.CODEC.optionalFieldOf("rotation", Vec3.ZERO).forGetter(AnimatableParams::getRotation),
            Vec3.CODEC.optionalFieldOf("scale", new Vec3(20, 20, 20)).forGetter(AnimatableParams::getScale),
            Vec3i.CODEC.optionalFieldOf("color", new Vec3i(255, 255, 255)).forGetter(AnimatableParams::getColor),
            Codec.INT.optionalFieldOf("alpha", 255).forGetter(AnimatableParams::getTransparency),
            Codec.BOOL.optionalFieldOf("perspective", true).forGetter(AnimatableParams::isPerspective),
            TextParams.MAP_CODEC.optionalFieldOf("texts", Map.of()).forGetter(AnimatableParams::getTextAttr),
            Codec.BOOL.optionalFieldOf("enable_scissor", false).forGetter(AnimatableParams::isEnableScissor),
            Codec.INT.optionalFieldOf("scissor_x", 0).forGetter(AnimatableParams::getScissorX),
            Codec.INT.optionalFieldOf("scissor_y", 0).forGetter(AnimatableParams::getScissorY),
            Codec.INT.optionalFieldOf("scissor_width", 0).forGetter(AnimatableParams::getScissorWidth),
            Codec.INT.optionalFieldOf("scissor_height", 0).forGetter(AnimatableParams::getScissorHeight)
    ).apply(instance, AnimatableParams::new));

    public AnimatableParams(ResourceLocation model, ResourceLocation animation, ResourceLocation texture,
                            Vec3 offset, Vec3 rotation, Vec3 scale,
                            Vec3i color, int transparency,
                            boolean perspective,
                            Map<String, TextParams> textAttr,
                            boolean enableScissor, int scissorX, int scissorY, int scissorWidth, int scissorHeight) {
        this.modelIndex = new ModelIndex(model);
        this.animation = animation;
        this.texture = texture;
        this.transform.setTranslation(PhysicsHelperKt.toBVector3f(offset));
        Vector3f rot = SparkMathKt.toRadians(rotation).toVector3f();
        Quaternionf quaternionf = new Quaternionf().rotationZYX(rot.z(), rot.y(), rot.x());
        this.transform.setRotation(SparkMathKt.toBQuaternion(quaternionf));
        this.transform.setScale(PhysicsHelperKt.toBVector3f(scale));
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

    public AnimatableParams(ResourceLocation model, ResourceLocation animation, ResourceLocation texture,
                            Vec3 offset, Vec3 rotation, Vec3 scale,
                            boolean perspective,
                            Map<String, TextParams> textAttr) {
        this.modelIndex = new ModelIndex(model);
        this.animation = animation;
        this.texture = texture;
        this.transform.setTranslation(PhysicsHelperKt.toBVector3f(offset));
        Vector3f rot = SparkMathKt.toRadians(rotation).toVector3f();
        Quaternionf quaternionf = new Quaternionf().rotationZYX(rot.z(), rot.y(), rot.x());
        this.transform.setRotation(SparkMathKt.toBQuaternion(quaternionf));
        this.transform.setScale(PhysicsHelperKt.toBVector3f(scale));
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

    public AnimatableParams(ResourceLocation model, ResourceLocation animation, ResourceLocation texture,
                            Vec3 offset, Vec3 rotation, double scale,
                            boolean perspective,
                            Map<String, TextParams> textAttr) {
        this.modelIndex = new ModelIndex(model);
        this.animation = animation;
        this.texture = texture;
        this.transform.setTranslation(PhysicsHelperKt.toBVector3f(offset));
        Vector3f rot = SparkMathKt.toRadians(rotation).toVector3f();
        Quaternionf quaternionf = new Quaternionf().rotationZYX(rot.z(), rot.y(), rot.x());
        this.transform.setRotation(SparkMathKt.toBQuaternion(quaternionf));
        this.transform.setScale((float) scale);
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

    public AnimatableParams(ResourceLocation model, ResourceLocation animation, ResourceLocation texture,
                            Vec3 offset, double scale,
                            boolean perspective,
                            Map<String, TextParams> textAttr) {
        this.modelIndex = new ModelIndex(model);
        this.animation = animation;
        this.texture = texture;
        this.transform.setTranslation(PhysicsHelperKt.toBVector3f(offset));
        this.transform.setScale((float) scale);
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

    public AnimatableParams(ResourceLocation model, ResourceLocation animation, ResourceLocation texture,
                            Vec3 offset,
                            boolean perspective,
                            Map<String, TextParams> textAttr) {
        this.modelIndex = new ModelIndex(model);
        this.animation = animation;
        this.texture = texture;
        this.transform.setTranslation(PhysicsHelperKt.toBVector3f(offset));
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

    public AnimatableParams(ResourceLocation model, ResourceLocation animation, ResourceLocation texture) {
        this.modelIndex = new ModelIndex(model);
        this.animation = animation;
        this.texture = texture;
        this.color = new Vec3i(255, 255, 255);
        this.transparency = 255;
        this.perspective = true;
        this.textAttr = Map.of();
        this.enableScissor = false;
        this.scissorX = 0;
        this.scissorY = 0;
        this.scissorWidth = 0;
        this.scissorHeight = 0;
    }

    private ResourceLocation getModel() {
        return modelIndex.getLocation();
    }

    private Vec3 getOffset() {
        return SparkMathKt.toVec3(getTransform(1).getTranslation());
    }

    private Vec3 getRotation() {
        return SparkMathKt.toVec3(SparkMathKt.toQuaternionf(getTransform(1).getRotation()).getEulerAnglesXYZ(new Vector3f()));
    }

    private Vec3 getScale() {
        return SparkMathKt.toVec3(getTransform(1).getScale());
    }

    public Vector3f getOffset(float partialTick) {
        return SparkMathKt.toVector3f(getTransform(partialTick).getTranslation());
    }

    public Vector3f getRotation(float partialTick) {
        return SparkMathKt.toQuaternionf(getTransform(partialTick).getRotation()).getEulerAnglesXYZ(new Vector3f());
    }

    public Quaternionf getQuaternion(float partialTick) {
        return SparkMathKt.toQuaternionf(getTransform(partialTick).getRotation());
    }

    public Vector3f getScale(float partialTick) {
        return SparkMathKt.toVector3f(getTransform(partialTick).getScale());
    }

    public void setTransform(Transform transform) {
        if (this.lastTransform == null) {
            this.lastTransform = transform;
        } else this.lastTransform = this.transform;
        this.transform = transform;
    }

    public Transform getTransform(float partialTick) {
        if (this.lastTransform != null) {
            return SparkMathKt.lerp(lastTransform, transform, partialTick);
        } else {
            return transform;
        }
    }

}
