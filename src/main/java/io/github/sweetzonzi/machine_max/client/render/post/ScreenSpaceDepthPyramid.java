package io.github.sweetzonzi.machine_max.client.render.post;

import java.nio.ByteBuffer;
import static org.lwjgl.opengl.GL32C.*;
import org.lwjgl.opengl.GL33C;

/**
 * 每帧共享的屏幕深度范围层级。最细格覆盖2×2原始像素，使用RG32F与向上补齐的2幂尺寸。
 * 归并先绘入独立scratch目标，再拷入层级的对应mip，输入采样和输出附件不会指向同一纹理。
 */
final class ScreenSpaceDepthPyramid implements AutoCloseable {
    static final String VERTEX = """
            #version 150
            out vec2 texCoord;
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                texCoord = p;
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private final int program;
    private final int vao;
    private final String fragment;
    private int texture, scratch, framebuffer;
    private int width, height, levels;

    ScreenSpaceDepthPyramid(String fragment) {
        this.fragment = fragment;
        PostProcessingGpuState before = new PostProcessingGpuState();
        try {
            program = createProgram(fragment);
            vao = glGenVertexArrays();
        } finally {
            before.restore();
        }
    }

    int texture() { return texture; }
    int levels() { return levels; }
    String fragment() { return fragment; }

    void build(int depthTexture, int screenWidth, int screenHeight, float depthA, float depthB) {
        PostProcessingGpuState before = new PostProcessingGpuState();
        try {
            glActiveTexture(GL_TEXTURE0);
            if (before.samplersSupported) GL33C.glBindSampler(0, 0);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            int w = powerOfTwo((screenWidth + 1) / 2);
            int h = powerOfTwo((screenHeight + 1) / 2);
            if (w != width || h != height) resize(w, h);
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glBindVertexArray(vao);
            for (int capability : PostProcessingGpuState.CAPABILITIES) glDisable(capability);
            glColorMask(true, true, true, true);
            glUseProgram(program);
            glUniform1i(glGetUniformLocation(program, "InputSampler"), 0);
            glUniform1f(glGetUniformLocation(program, "DepthA"), depthA);
            glUniform1f(glGetUniformLocation(program, "DepthB"), depthB);
            for (int level = 0; level < levels; level++) {
                int lw = Math.max(1, width >> level), lh = Math.max(1, height >> level);
                glViewport(0, 0, lw, lh);
                glBindTexture(GL_TEXTURE_2D, level == 0 ? depthTexture : texture);
                glUniform1i(glGetUniformLocation(program, "SourceLevel"), level - 1);
                glDrawArrays(GL_TRIANGLES, 0, 3);
                glBindTexture(GL_TEXTURE_2D, texture);
                glCopyTexSubImage2D(GL_TEXTURE_2D, level, 0, 0, 0, 0, lw, lh);
            }
        } finally {
            before.restore();
        }
    }

    private void resize(int w, int h) {
        width = w; height = h;
        levels = 32 - Integer.numberOfLeadingZeros(Math.max(w, h));
        if (texture == 0) texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        parameters();
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, levels - 1);
        for (int level = 0; level < levels; level++) {
            glTexImage2D(GL_TEXTURE_2D, level, GL_RG32F, Math.max(1, w >> level), Math.max(1, h >> level),
                    0, GL_RG, GL_FLOAT, (ByteBuffer) null);
        }
        if (scratch == 0) scratch = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, scratch);
        parameters();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RG32F, w, h, 0, GL_RG, GL_FLOAT, (ByteBuffer) null);
        if (framebuffer == 0) framebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, scratch, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Screen depth hierarchy framebuffer is incomplete");
        }
    }

    private static int powerOfTwo(int n) { return n <= 1 ? 1 : Integer.highestOneBit(n - 1) << 1; }

    private static void parameters() {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    private static int createProgram(String fragment) {
        int vertex = 0, pixel = 0, result = 0;
        try {
            vertex = compile(GL_VERTEX_SHADER, VERTEX);
            pixel = compile(GL_FRAGMENT_SHADER, fragment);
            result = glCreateProgram();
            glAttachShader(result, vertex); glAttachShader(result, pixel);
            glBindFragDataLocation(result, 0, "depthRange");
            glLinkProgram(result);
            if (glGetProgrami(result, GL_LINK_STATUS) == GL_FALSE) throw new IllegalStateException(glGetProgramInfoLog(result));
            return result;
        } catch (RuntimeException e) {
            if (result != 0) glDeleteProgram(result);
            throw e;
        } finally {
            if (vertex != 0) glDeleteShader(vertex);
            if (pixel != 0) glDeleteShader(pixel);
        }
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source); glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String message = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException(message);
        }
        return shader;
    }

    private void releaseTargets() {
        if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
        if (scratch != 0) glDeleteTextures(scratch);
        if (texture != 0) glDeleteTextures(texture);
        framebuffer = scratch = texture = 0;
    }

    @Override public void close() {
        releaseTargets();
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
