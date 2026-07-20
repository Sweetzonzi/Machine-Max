package io.github.sweetzonzi.machine_max.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

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

    /**
     * 内构查看剪影 RenderType — 带 UV 采样做 alpha 遮罩，输出不透明耐久样式色。
     * 使用 {@link DefaultVertexFormat#NEW_ENTITY}（含 UV0），片段着色器丢弃透明像素。
     * 关闭混合与上传排序，使用最近邻过滤，避免区域边界颜色插值产生伪边缘。
     */
    private static final Function<ResourceLocation, RenderType> INSPECTOR_SILHOUETTE = Util.memoize(texture ->
            RenderType.create(
                    "machine_max_inspector_silhouette",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS,
                    1536,
                    false, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> MMRenderTypes.inspectorShader))
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(NO_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_DEPTH_WRITE)
                            .setCullState(NO_CULL)
                            .createCompositeState(false)
            )
    );

    /** 内构查看核心着色器实例，在 {@link RegisterShadersEvent} 中赋值 */
    public static volatile ShaderInstance inspectorShader;

    /** 按纹理获取内构查看剪影 RenderType */
    public static RenderType inspectorSilhouette(ResourceLocation texture) {
        return INSPECTOR_SILHOUETTE.apply(texture);
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
