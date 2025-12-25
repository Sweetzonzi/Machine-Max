package io.github.sweetzonzi.machine_max.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

import java.util.OptionalDouble;

import static net.minecraft.client.renderer.RenderStateShard.*;

public class MMRenderTypes {

    /**
     * 永远显示在最前的彩色线框（无深度测试）
     */
    public static final RenderType LINES_ALWAYS_VISIBLE = RenderType.create(
            "machine_max_lines_always_visible",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
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
