package io.github.sweetzonzi.machine_max.common.visual;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * HUD 样式：只描述“长什么样”，不含任何投放（位置、旋转、缩放、投影、炮镜行为）。
 * <p>
 * 样式是命名资源，由内容包 {@code huds/*.json} 定义，读入
 * {@link io.github.sweetzonzi.machine_max.external.MMDynamicRes#HUD_STYLES}。
 * 运行时由 {@link HudElement} 通过 {@link HudElement#style()} 引用。
 * <p>
 * {@code type} 字段决定载体类型；V1 只有模型载体 {@link ModelHudStyle}。
 */
public interface HudStyle {

    /** 模型载体的 {@code type} 值。 */
    ResourceLocation MODEL_HUD = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "model_hud");

    /** 载体判别字段 {@code type} 的取值。 */
    ResourceLocation type();

    /** 本载体在 {@code type} 分派中使用的 codec。 */
    MapCodec<? extends HudStyle> codec();

    /**
     * 按 {@code type} 分派样式载体。
     * <p>
     * 未知类型在解码阶段抛出，由内容包加载模块按文件记录诊断。
     */
    static MapCodec<? extends HudStyle> codecFor(ResourceLocation type) {
        if (MODEL_HUD.equals(type)) {
            return ModelHudStyle.CODEC;
        }
        throw new IllegalArgumentException("Unknown HUD style type: " + type);
    }

    Codec<HudStyle> CODEC = ResourceLocation.CODEC.dispatch("type", HudStyle::type, HudStyle::codecFor);

    /**
     * 裁剪区域参数。它裁剪的是这份样式自身的绘制区；
     * x/y 是相对屏幕中心的坐标。
     */
    record ScissorParams(
            boolean enable,
            int x,
            int y,
            int width,
            int height
    ) {
        public static final ScissorParams DEFAULT = new ScissorParams(false, 0, 0, 0, 0);

        public static final Codec<ScissorParams> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enable", false).forGetter(ScissorParams::enable),
                Codec.INT.optionalFieldOf("x", 0).forGetter(ScissorParams::x),
                Codec.INT.optionalFieldOf("y", 0).forGetter(ScissorParams::y),
                Codec.INT.optionalFieldOf("width", 0).forGetter(ScissorParams::width),
                Codec.INT.optionalFieldOf("height", 0).forGetter(ScissorParams::height)
        ).apply(instance, ScissorParams::new));
    }

    /**
     * 文本槽参数。键是模型里的骨骼/定位器名。
     */
    record TextParams(
            String key,
            boolean centered,
            boolean shadow,
            Vec3 scale,
            Vec3i color,
            int transparency,
            Vec3i backgroundColor,
            int backgroundTransparency,
            List<String> molangArgs,
            int significand
    ) {
        public static final Codec<TextParams> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("key").forGetter(TextParams::key),
                Codec.BOOL.optionalFieldOf("centered", true).forGetter(TextParams::centered),
                Codec.BOOL.optionalFieldOf("shadow", false).forGetter(TextParams::shadow),
                Vec3.CODEC.optionalFieldOf("scale", new Vec3(1, 1, 1)).forGetter(TextParams::scale),
                Vec3i.CODEC.optionalFieldOf("color", new Vec3i(255, 255, 255)).forGetter(TextParams::color),
                Codec.INT.optionalFieldOf("transparency", 255).forGetter(TextParams::transparency),
                Vec3i.CODEC.optionalFieldOf("background_color", new Vec3i(0, 0, 0)).forGetter(TextParams::backgroundColor),
                Codec.INT.optionalFieldOf("background_transparency", 0).forGetter(TextParams::backgroundTransparency),
                Codec.STRING.listOf().optionalFieldOf("molang_args", List.of()).forGetter(TextParams::molangArgs),
                Codec.INT.optionalFieldOf("significand", 0).forGetter(TextParams::significand)
        ).apply(instance, TextParams::new));

        public static final Codec<Map<String, TextParams>> MAP_CODEC = Codec.unboundedMap(Codec.STRING, CODEC);

        public int colorArgb() {
            return new Color(color.getX(), color.getY(), color.getZ(), transparency).getRGB();
        }

        public int backgroundColorArgb() {
            return new Color(backgroundColor.getX(), backgroundColor.getY(), backgroundColor.getZ(), backgroundTransparency).getRGB();
        }
    }
}
