package io.github.sweetzonzi.machine_max.client.render.post;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;
import java.nio.ByteBuffer;
import java.util.Arrays;
import static org.lwjgl.opengl.GL32C.*;

/** 隔离深度层级与单帧诊断的原生GL绘制，保存并恢复引擎正在使用的绑定和开关。 */
final class PostProcessingGpuState {
    static final int[] CAPABILITIES = {GL_DEPTH_TEST, GL_BLEND, GL_CULL_FACE, GL_SCISSOR_TEST, GL_STENCIL_TEST, GL_DITHER, GL_RASTERIZER_DISCARD};
    static final int[] PACK_PARAMETERS = {GL_PACK_ALIGNMENT, GL_PACK_ROW_LENGTH, GL_PACK_SKIP_PIXELS, GL_PACK_SKIP_ROWS, GL_PACK_SWAP_BYTES, GL_PACK_LSB_FIRST};
    private final int draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
    private final int program = glGetInteger(GL_CURRENT_PROGRAM), vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
    private final int active = glGetInteger(GL_ACTIVE_TEXTURE), packBuffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
    private final int unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
    final boolean samplersSupported = GL.getCapabilities().OpenGL33 || GL.getCapabilities().GL_ARB_sampler_objects;
    private final int[] samplers = new int[3];
    private final int depthFunc = glGetInteger(GL_DEPTH_FUNC);
    private final boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
    private final int[] viewport = new int[4], scissor = new int[4], textures = new int[4], pack = new int[PACK_PARAMETERS.length];
    private final boolean[] enabled = new boolean[CAPABILITIES.length];
    private final ByteBuffer colorMask = BufferUtils.createByteBuffer(4);

    PostProcessingGpuState() {
        glGetIntegerv(GL_VIEWPORT, viewport); glGetIntegerv(GL_SCISSOR_BOX, scissor); glGetBooleanv(GL_COLOR_WRITEMASK, colorMask);
        for (int i = 0; i < CAPABILITIES.length; i++) enabled[i] = glIsEnabled(CAPABILITIES[i]);
        for (int i = 0; i < PACK_PARAMETERS.length; i++) pack[i] = glGetInteger(PACK_PARAMETERS[i]);
        int[] units = {GL_TEXTURE0, GL_TEXTURE1, GL_TEXTURE2, active};
        for (int i = 0; i < units.length; i++) { glActiveTexture(units[i]); textures[i] = glGetInteger(GL_TEXTURE_BINDING_2D); }
        if (samplersSupported) for (int i = 0; i < 3; i++) samplers[i] = glGetIntegeri(GL33C.GL_SAMPLER_BINDING, i);
        glActiveTexture(active);
    }

    void restore() {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw); glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
        glUseProgram(program); glBindVertexArray(vao);
        int[] units = {GL_TEXTURE0, GL_TEXTURE1, GL_TEXTURE2, active};
        for (int i = 0; i < units.length; i++) { glActiveTexture(units[i]); glBindTexture(GL_TEXTURE_2D, textures[i]); }
        glActiveTexture(active);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, packBuffer);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        if (samplersSupported) for (int i = 0; i < 3; i++) GL33C.glBindSampler(i, samplers[i]);
        for (int i = 0; i < PACK_PARAMETERS.length; i++) glPixelStorei(PACK_PARAMETERS[i], pack[i]);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]); glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
        glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0);
        glDepthMask(depthMask); glDepthFunc(depthFunc);
        for (int i = 0; i < CAPABILITIES.length; i++) { if (enabled[i]) glEnable(CAPABILITIES[i]); else glDisable(CAPABILITIES[i]); }
    }

    boolean matches(PostProcessingGpuState other) {
        return draw == other.draw && read == other.read && program == other.program && vao == other.vao
                && active == other.active && packBuffer == other.packBuffer && unpackBuffer == other.unpackBuffer
                && depthFunc == other.depthFunc && depthMask == other.depthMask && Arrays.equals(samplers, other.samplers)
                && Arrays.equals(viewport, other.viewport) && Arrays.equals(scissor, other.scissor)
                && Arrays.equals(textures, other.textures) && Arrays.equals(pack, other.pack)
                && Arrays.equals(enabled, other.enabled) && colorMask.equals(other.colorMask);
    }
}
