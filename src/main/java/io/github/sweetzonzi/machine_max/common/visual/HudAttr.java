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

/**
 * HUD 属性类。定义了 HUD 组件的模型、动画、纹理、变换、
 * 文本覆盖、裁剪区域等全部渲染参数。
 * 通过 JSON 内容包加载，存储在 {@link io.github.sweetzonzi.machine_max.external.MMDynamicRes#CUSTOM_HUD} 中。
 */
@Getter
@Setter
public class HudAttr {
    public ModelIndex modelIndex;
    public ResourceLocation animation;
    public ResourceLocation texture;
    public Transform transform = new Transform();
    public Transform lastTransform;
    public Vec3i color;
    public int transparency;
    public boolean perspective;

    /**
     * 炮镜模式下 HUD 元素的跟随行为。
     * 控制正交元素的原点位置和旋转变换，以及透视元素的摄像机基准。
     * 仅在 SightHud 渲染管线中生效，普通 CustomHud 忽略（全部视为 SCREEN_FIXED）。
     */
    public enum ScopeBehavior {
        /** 屏幕固定：原点=屏幕中心，无旋转 */
        SCREEN_FIXED,
        /** 跟随位置：原点=炮镜投影中心，不旋转（默认） */
        FOLLOW_POSITION,
        /** 跟随姿态：原点=炮镜投影中心，叠加 scope 的 pitch/yaw 偏移旋转 */
        FOLLOW_TRANSFORM
    }

    /** 炮镜行为，默认 FOLLOW_POSITION */
    public ScopeBehavior scopeBehavior = ScopeBehavior.FOLLOW_POSITION;
    /** 炮镜变焦时是否忽略缩放。true=元素尺寸不随 zoom 变化（遮罩、数显等用），false=随 zoom 放大（分划标记用） */
    public boolean ignoreZoom = false;

    /** 裁剪区域参数 */
    public ScissorParams scissor = ScissorParams.DEFAULT;

    public Map<String, TextParams> textAttr;

    /**
     * 裁剪区域参数记录。控制 GUI 渲染时的裁剪矩形。
     */
    public record ScissorParams(
            boolean enable,
            int x,
            int y,
            int width,
            int height
    ) {
        public static final ScissorParams DEFAULT = new ScissorParams(false, 0, 0, 0, 0);

        public static final Codec<ScissorParams> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enable", false).forGetter(ScissorParams::enable),
                Codec.INT.optionalFieldOf("x", 0).forGetter(ScissorParams::x),
                Codec.INT.optionalFieldOf("y", 0).forGetter(ScissorParams::y),
                Codec.INT.optionalFieldOf("width", 0).forGetter(ScissorParams::width),
                Codec.INT.optionalFieldOf("height", 0).forGetter(ScissorParams::height)
        ).apply(instance, ScissorParams::new));
    }

    /**
     * 文本参数记录。描述一个附着在骨骼上的文本标签。
     */
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
//                ResourceLocation.STREAM_CODEC.fieldOf("font").forGetter(TextParams::font),
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

    public static final Codec<HudAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("model").forGetter(HudAttr::getModel),
            ResourceLocation.CODEC.fieldOf("animation").forGetter(HudAttr::getAnimation),
            ResourceLocation.CODEC.fieldOf("texture").forGetter(HudAttr::getTexture),
            Vec3.CODEC.optionalFieldOf("offset", Vec3.ZERO).forGetter(HudAttr::getOffset),
            Vec3.CODEC.optionalFieldOf("rotation", Vec3.ZERO).forGetter(HudAttr::getRotation),
            Vec3.CODEC.optionalFieldOf("scale", new Vec3(1, 1, 1)).forGetter(HudAttr::getScale),
            Vec3i.CODEC.optionalFieldOf("color", new Vec3i(255, 255, 255)).forGetter(HudAttr::getColor),
            Codec.INT.optionalFieldOf("alpha", 255).forGetter(HudAttr::getTransparency),
            Codec.BOOL.optionalFieldOf("perspective", true).forGetter(HudAttr::isPerspective),
            Codec.STRING.xmap(
                    s -> ScopeBehavior.valueOf(s.toUpperCase()),
                    v -> v.name().toLowerCase()
            ).optionalFieldOf("scope_behavior", ScopeBehavior.FOLLOW_POSITION).forGetter(HudAttr::getScopeBehavior),
            Codec.BOOL.optionalFieldOf("ignore_zoom", false).forGetter(HudAttr::isIgnoreZoom),
            ScissorParams.CODEC.optionalFieldOf("scissor", ScissorParams.DEFAULT).forGetter(HudAttr::getScissor),
            TextParams.MAP_CODEC.optionalFieldOf("texts", Map.of()).forGetter(HudAttr::getTextAttr)
    ).apply(instance, HudAttr::new));

    public HudAttr(ResourceLocation model, ResourceLocation animation, ResourceLocation texture,
                   Vec3 offset, Vec3 rotation, Vec3 scale,
                   Vec3i color, int transparency,
                   boolean perspective,
                   ScopeBehavior scopeBehavior,
                   boolean ignoreZoom,
                   ScissorParams scissor,
                   Map<String, TextParams> textAttr) {
        this.modelIndex = new ModelIndex("hud", model);
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
        this.scopeBehavior = scopeBehavior;
        this.ignoreZoom = ignoreZoom;
        this.scissor = scissor;
        this.textAttr = textAttr;
    }

    public HudAttr(ModelIndex model, ResourceLocation animation, ResourceLocation texture) {
        this.modelIndex = model;
        this.animation = animation;
        this.texture = texture;
        this.color = new Vec3i(255, 255, 255);
        this.transparency = 255;
        this.perspective = true;
        this.scopeBehavior = ScopeBehavior.FOLLOW_POSITION;
        this.ignoreZoom = false;
        this.scissor = ScissorParams.DEFAULT;
        this.textAttr = Map.of();
    }

    private ResourceLocation getModel() {
        return modelIndex.getLocation();
    }

    private String getType() {
        return modelIndex.getType();
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
