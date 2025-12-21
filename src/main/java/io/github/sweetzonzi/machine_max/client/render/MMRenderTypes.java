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

    public static RenderType alwaysVisibleLines() {
        return LINES_ALWAYS_VISIBLE;
    }
}
