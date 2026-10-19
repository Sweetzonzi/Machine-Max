package io.github.sweetzonzi.machine_max.common.visual;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * AUI 页面载体：样式的“外观”是一个 HTML 页面。
 * <p>
 * {@code page} 是 AUI 页面键（{@code <命名空间>/<相对路径>}），由内容包
 * {@code documents/} 目录经 {@code DocumentModule} 写入客户端资源包，或由模组
 * JAR 的 {@code assets/apricityui/apricity/} 提供。运行时元素为每个条目创建一个
 * 独立 Document。正交模式按 {@link HudElement#anchor()}、{@link HudElement#offset()}
 * 与 {@link HudElement#scale()} 的 X/Y 分量投放到屏幕；透视模式以页面内容中心为局部原点，
 * 按 offset、rotation 与 scale 合成相机空间平面，由 HUD 的透视投影流程绘制。
 * <p>
 * {@code defaults} 是写进页面根元素的 CSS 变量，可选；相同变量在页面样式表里
 * 重新声明时以页面为准。
 */
public record AuiHudStyle(
        String page,
        Map<String, String> defaults
) implements HudStyle {

    /** AUI 载体的 {@code type} 值。 */
    public static final ResourceLocation AUI_HUD = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "aui_hud");

    public static final MapCodec<AuiHudStyle> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("page").forGetter(AuiHudStyle::page),
            Codec.unboundedMap(Codec.STRING, Codec.STRING)
                    .optionalFieldOf("defaults", Map.of())
                    .forGetter(AuiHudStyle::defaults)
    ).apply(instance, AuiHudStyle::new));

    @Override
    public ResourceLocation type() {
        return AUI_HUD;
    }

    @Override
    public MapCodec<? extends HudStyle> codec() {
        return CODEC;
    }
}
