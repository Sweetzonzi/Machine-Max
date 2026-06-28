package io.github.sweetzonzi.machine_max.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;

import java.util.OptionalDouble;
import java.util.function.Function;

import static net.minecraft.client.renderer.RenderStateShard.*;

public class MMRenderTypes {

    /**
     * 永远显示在最前的彩色线框（无深度测试）
     */
    public static final RenderType LINES_ALWAYS_VISIBLE = RenderType.create(
            "machine_max_lines_always_visible",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.LINES,
            1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(OptionalDouble.empty()))
                    .setLayeringState(NO_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .createCompositeState(false)
    );

    /**
     * 永远显示在最前的纯色几何体（无深度测试）
     * <p>
     * 适用于：
     * - 3D HUD 面板
     * - Debug Mesh
     * - 高亮模型覆盖层
     * - 半透明信息层
     */
    public static final RenderType SOLID_ALWAYS_VISIBLE = RenderType.create(
            "machine_max_solid_always_visible",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setLayeringState(NO_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .createCompositeState(false)
    );

    /**
     * 加法混合的纯色几何体（有深度测试）
     * 适用于：
     * - 世界中的能量体
     * - 发光覆盖层
     * - 需要被遮挡的高亮模型
     */
    public static final RenderType ADD_SOLID_DEPTH = RenderType.create(
            "machine_max_additive_solid_depth",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setLayeringState(NO_LAYERING)
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false)
    );

    public static final RenderType ADD_SOLID_ALWAYS_VISIBLE = RenderType.create(
            "machine_max_additive_solid_always_visible",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setLayeringState(NO_LAYERING)
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .createCompositeState(false)
    );

    /**
     * 自发光曳光线（加法混合 + LINES 模式 + 深度测试），按线宽缓存。
     * <p>
     * 使用 {@link Util#memoize} 按 {@code width} 参数缓存 RenderType 实例，
     * 相同宽度返回同一实例，与 vanilla {@code RenderType.outline(ResourceLocation)} 同模式。
     * <p>
     * 适用于投射物曳光效果，自发光但会被障碍物遮挡。
     * 线宽通过 {@link LineStateShard} 传递给 OpenGL glLineWidth。
     *
     * @param width 线宽（像素），默认 2.0
     */
    private static final Function<Double, RenderType> TRACER_LINE = Util.memoize(width ->
        RenderType.create(
            "machine_max_tracer_line",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.LINES,
            1536,
            RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_LINES_SHADER)
                .setLineState(new LineStateShard(OptionalDouble.of(width)))
                .setLayeringState(NO_LAYERING)
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setOutputState(ITEM_ENTITY_TARGET)
                .setWriteMaskState(COLOR_WRITE)
                .setCullState(NO_CULL)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .createCompositeState(false)
        )
    );

    public static RenderType tracerLine(double width) {
        return TRACER_LINE.apply(width);
    }

    public static RenderType alwaysVisibleLines() {
        return LINES_ALWAYS_VISIBLE;
    }

    public static RenderType alwaysVisibleSolid() {
        return SOLID_ALWAYS_VISIBLE;
    }

    public static RenderType additiveSolidDepth() {
        return ADD_SOLID_DEPTH;
    }

    public static RenderType additiveSolidAlwaysVisible() {
        return ADD_SOLID_ALWAYS_VISIBLE;
    }

}
