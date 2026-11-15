package io.github.sweetzonzi.machine_max.probe;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * 手工 GPU 探针：隐藏 GLFW 窗口，编译生产 GLSL，在合成深度图上核对解析几何与有限预算。
 * 不启动 Minecraft；不测世界深度快照、Iris 接线或游戏画面。
 * 运行：gradlew --offline probeTest --tests '*ScreenSpaceLightProbe'。
 * 环境变量 MM_S3_BENCHMARK=1 时额外测 1080p / 3840×2019 的完整片元程序。
 */
class ScreenSpaceLightProbe {
    /** 允许只带 LWJGL/JUnit 的独立 JVM 运行，避免探针依赖游戏源码编译。 */
    public static void main(String[] args) throws Exception {
        new ScreenSpaceLightProbe().tracesFiniteSurfacesOnGpu();
    }
    private static final Path SHADER = Path.of("src/main/resources/assets/machine_max/shaders/program/screen_space_light.fsh");
    private static final Path OUTPUT = Path.of("build/s3-diagnostics");
    private static final String VERTEX = """
            #version 150
            out vec2 texCoord;
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                texCoord = p;
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String TRACE_MAIN = """
            uniform float ProbeReceiverZ;
            uniform float ProbeSceneReceiver;
            uniform vec3 ProbeLight;
            void main() {
                float receiverZ = ProbeReceiverZ;
                if (ProbeSceneReceiver > 0.5) {
                    if (!forwardDepth(texCoord, receiverZ)) { fragColor = vec4(2.0, 0.0, 0.0, 1.0); return; }
                }
                vec3 p = viewPos(texCoord, receiverZ);
                vec3 normal = vec3(0.0, 0.0, 1.0);
                bool hasPlane = true;
                if (ProbeSceneReceiver > 0.5) hasPlane = viewNormal(texCoord, receiverZ, normal);
                int steps;
                int reason;
                int available = CapturePixelBudget > 0.0 ? int(CapturePixelBudget) : OCCL_MAX_STEPS;
                int state = traceLightRay(p, ProbeLight, length(p - ProbeLight), normal, hasPlane, available, steps, reason);
                fragColor = vec4(float(state), float(steps), float(reason), 1.0);
            }
            """;

    private static Hierarchy hierarchy;

    @Test
    void tracesFiniteSurfacesOnGpu() throws Exception {
        Files.createDirectories(OUTPUT);
        GLFWErrorCallback errors = GLFWErrorCallback.createPrint(System.err);
        errors.set();
        long window = 0;
        try {
            assertTrue(glfwInit(), "GLFW 初始化失败；本探针需要可用的本机图形环境");
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            window = glfwCreateWindow(32, 32, "S3 GPU probe", 0, 0);
            assertNotEquals(0, window);
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("GPU: " + glGetString(GL_RENDERER) + "; GL: " + glGetString(GL_VERSION));
            int vao = glGenVertexArrays();
            glBindVertexArray(vao);
            hierarchy = new Hierarchy();
            String source = Files.readString(SHADER);
            int production = program(source);
            glUseProgram(0); glDeleteProgram(production); // 完整 main 也必须编译、链接成功。
            String captureSource = source.replaceFirst("#version 150", "#version 150\n#define MM_LIGHT_CAPTURE 1");
            int captureMain = program(captureSource);
            glUseProgram(0); glDeleteProgram(captureMain);
            String functions = source.substring(0, source.indexOf("void main()"));
            verifiesHierarchy(functions, captureSource);
            verifiesShadowRange(functions);
            String ddaFunctions = withBudget(functions.replace("const float CaptureTraversal = 0.0;", "const float CaptureTraversal = 1.0;"), 128);
            int trace = program(ddaFunctions + TRACE_MAIN);
            try {
                float[] standard = renderTrace(trace, 256, 64, 20, 5, 0, 32, 96, 104, 10, false);
                double front = 10 + parameter(source, "OCCL_BIAS_M") + parameter(source, "OCCL_BIAS_REL_Z") * 10;
                verifySingleBand(standard, 256, 32, 105, 180, front,
                        front + parameter(source, "OCCL_THICKNESS_M"), 20, 5, 96, 104);
                saveStates("thin-blocker", standard, 256, 64);
                float[] reversed = renderTrace(trace, 256, 64, 20, 5, 0, 32, 96, 104, 10, true);
                for (int x = 105; x < 180; x++) {
                    assertEquals(state(standard, 256, x, 32), state(reversed, 256, x, 32), "深度方向不应改变命中 x=" + x);
                }
                float[] foreground = renderTrace(trace, 256, 64, 20, 12, 0, 32, 96, 104, 10, false);
                assertNoHits(foreground, "遮挡物在整段光线前面，应允许射线从有限层后方穿过");
                float[] coplanar = renderTrace(trace, 256, 64, 20, 20, 0, 32, 0, 256, 20, false);
                assertNoHits(coplanar, "同深度不能贡献自遮挡");
                float[] mirror = renderTrace(trace, 256, 64, 20, 5, 256, 32, 152, 160, 10, false);
                for (int x = 105; x < 180; x++) {
                    assertEquals(state(standard, 256, x, 32), state(mirror, 256, 255 - x, 32), "左右行进应对称 x=" + x);
                }
                float[] outside = renderTrace(trace, 256, 64, 20, 5, -300, 32, 96, 104, 10, false);
                assertEquals(2, state(outside, 256, 110, 32), "视口外的未检查线段属于未知");
                assertTrue((Math.round(outside[(32 * 256 + 110) * 4 + 2]) & 2) != 0, "裁剪原因必须保留");
                float[] behind = renderTrace(trace, 256, 64, 20, -1, 128, 32, 0, 0, 10, false);
                assertEquals(2, state(behind, 256, 128, 32), "近裁面截断属于未知");
                float[] collapsed = renderTrace(trace, 256, 64, 20, 5, 128.5f, 32.5f, 0, 256, 10, false);
                assertEquals(0, state(collapsed, 256, 128, 32), "投影退化为一点时仍检测整段深度");
                assertEquals(1, Math.round(collapsed[(32 * 256 + 128) * 4 + 1]));
                float[] missing = renderTrace(trace, 256, 64, 20, 5, 0, 32, 0, 256, 0, false);
                assertEquals(2, state(missing, 256, 150, 32), "缺深度属于未知");
                assertTrue((Math.round(missing[(32 * 256 + 150) * 4 + 2]) & 4) != 0, "缺深度原因必须保留");
                // 垂直线段与横向的解析几何相同，交换 x/y 后核对，覆盖纯竖直 DDA。
                float[] vertical = renderTrace(trace, 64, 256, 20, 5, 32, 0, 96, 104, 10, false);
                for (int x = 105; x < 180; x++) {
                    assertEquals(state(standard, 256, x, 32), state(vertical, 64, 32, x), "交换轴应保持命中 x=" + x);
                }
                for (float lightHeight : new float[]{1, -1.9f}) {
                    float[] depth = new float[256 * 128];
                    for (int y = 0; y < 128; y++) {
                        float z = y < 64 ? 2 * 128 / (64 - y - .5f) : 0;
                        Arrays.fill(depth, y * 256, (y + 1) * 256, z == 0 ? 1 : 1 - .05f / z);
                    }
                    float[] floor = renderTraceDepth(trace, 256, 128, 20, 20, 128,
                            64 + 128 * lightHeight / 20, -1, -.05f, depth, true);
                    saveStates("floor-height-" + lightHeight, floor, 256, 128);
                    assertNoHits(floor, "平坦地面上方的光源不能产生自遮挡 height=" + lightHeight);
                }
                for (float scale : new float[]{.5f, 1.5f, 3}) {
                    float[] scaled = renderTrace(trace, 256, 64, 20 * scale, 5 * scale, 0, 32, 96, 104, 10 * scale, false);
                    double z = 10 * scale;
                    double frontScaled = z + parameter(source, "OCCL_BIAS_M") + parameter(source, "OCCL_BIAS_REL_Z") * z;
                    verifySingleBand(scaled, 256, 32, 105, 180, frontScaled,
                            frontScaled + parameter(source, "OCCL_THICKNESS_M"), 20 * scale, 5 * scale, 96, 104);
                }
                float[] diagonal = renderTrace(trace, 256, 256, 20, 5, 0, 0, 96, 104, 10, false);
                verifySingleBand(diagonal, 256, 220, 105, 180, front,
                        front + parameter(source, "OCCL_THICKNESS_M"), 20, 5, 96, 104);
                float[] prefix = renderTrace(trace, 512, 64, 20, 5, 0, 32, 336, 348, 10.5f, false);
                assertEquals(2, state(prefix, 512, 480, 32));
                assertEquals(1, Math.round(prefix[(32 * 512 + 480) * 4 + 2]), "逐像素前缀应报告预算耗尽");
                int comparison = program(captureSource.substring(0, captureSource.indexOf("void main()")) + TRACE_MAIN);
                try {
                    glUseProgram(comparison);
                    glUniform1f(glGetUniformLocation(comparison, "CaptureSamples"), 64);
                    glUniform1f(glGetUniformLocation(comparison, "CaptureRayBudget"), 64);
                    float[] points = renderTrace(comparison, 512, 64, 20, 5, 0, 32, 336, 348, 10.5f, false);
                    assertEquals(0, state(points, 512, 480, 32), "64点全路径采样应能检测这个前缀预算之外的有限层");
                    assertTrue(points[(32 * 512 + 480) * 4 + 1] <= 64, "点采样命中后必须早退");
                    glUniform1f(glGetUniformLocation(comparison, "CaptureSamples"), 0);
                    glUniform1f(glGetUniformLocation(comparison, "CaptureTraversal"), 1);
                    glUniform1f(glGetUniformLocation(comparison, "CaptureRayBudget"), 1024);
                    glUniform1f(glGetUniformLocation(comparison, "CapturePixelBudget"), 1024);
                    float[] complete = renderTrace(comparison, 512, 64, 20, 5, 0, 32, 336, 348, 10.5f, false);
                    assertEquals(0, state(complete, 512, 480, 32), "完整DDA参考也应命中同一有限层");
                } finally {
                    glUseProgram(0); glDeleteProgram(comparison);
                }
                System.out.println("same ray: prefix128=budget-unknown, uniform64=hit, complete-DDA=hit");
            } finally {
                glUseProgram(0); glDeleteProgram(trace);
            }
            for (int budget : new int[]{64, 128, 256}) {
                int variant = program(withBudget(ddaFunctions, budget) + TRACE_MAIN);
                try {
                    float[] empty = renderTrace(variant, 384, 64, 20, 5, 0, 32, 0, 0, 10, false);
                    assertEquals(2, state(empty, 384, 350, 32), "预算耗尽必须报告未知");
                    assertEquals(budget, Math.round(empty[(32 * 384 + 350) * 4 + 1]));
                    long unknown = 0;
                    for (int x = 0; x < 384; x++) if (state(empty, 384, x, 32) == 2) unknown++;
                    System.out.printf(Locale.ROOT, "budget=%d empty-wall unknown=%d/384%n", budget, unknown);
                } finally {
                    glUseProgram(0); glDeleteProgram(variant);
                }
            }
            int shared = program(withBudget(functions, 1) + """
                    uniform float ProbeReceiverZ;
                    uniform vec3 ProbeLight;
                    void main() {
                        vec3 p = viewPos(texCoord, ProbeReceiverZ);
                        int available = OCCL_PIXEL_STEPS;
                        int total = 0;
                        int unknown = 0;
                        for (int i = 0; i < 16; i++) {
                            int used;
                            int reason;
                            int state = traceLightRay(p, ProbeLight, length(p - ProbeLight),
                                    vec3(0.0, 0.0, 1.0), true, available, used, reason);
                            total += used;
                            available -= used;
                            if (state == RAY_UNKNOWN) unknown++;
                        }
                        fragColor = vec4(float(total), float(unknown), 0.0, 1.0);
                    }
                    """);
            try {
                float[] many = renderTrace(shared, 384, 64, 20, 5, 0, 32, 0, 0, 10, false);
                int limit = 1;
                for (int i = 0; i < many.length; i += 4) assertTrue(many[i] <= limit, "多灯不得超过每像素总预算");
                assertEquals(limit, Math.round(many[(32 * 384 + 350) * 4]));
                assertEquals(16, Math.round(many[(32 * 384 + 350) * 4 + 1]));
                System.out.println("16 lights share pixel budget=" + limit);
            } finally {
                glUseProgram(0); glDeleteProgram(shared);
            }
            if ("1".equals(System.getenv("MM_S3_BENCHMARK"))) benchmark(source);
            verifiesDepthStencilReadback();
            assertEquals(GL_NO_ERROR, glGetError());
            glDeleteVertexArrays(vao);
            System.out.println("PASS: finite-depth / single-band / standard-reversed / coplanar / mirrored / clipped / degenerate / budget");
        } finally {
            if (hierarchy != null) { hierarchy.close(); hierarchy = null; }
            if (window != 0) glfwDestroyWindow(window);
            glfwTerminate();
            glfwSetErrorCallback(null);
            errors.free();
        }
    }

    private static void verifiesShadowRange(String functions) {
        int fade = program(functions + """
                void main() {
                    float distancePC = (gl_FragCoord.x - 0.5) / 64.0;
                    int steps;
                    int reason;
                    int state = traceLightRay(vec3(0, 0, -20), vec3(0, 0, -20 + distancePC), distancePC,
                            vec3(0, 0, 1), true, 0, steps, reason);
                    fragColor = vec4(surfaceShadowWeight(distancePC), float(state), float(steps), float(reason));
                }
                """);
        int trace = program(functions + TRACE_MAIN);
        try {
            for (float range : new float[]{0, 8, 16}) {
                float[] pixels = renderTraceDepth(fade, 1025, 1, 20, 5, 0, 0, -1, -.05f,
                        new float[1025], false, range);
                float previous = 1;
                for (int x = 0; x <= 1024; x++) {
                    int offset = x * 4;
                    float distance = x / 64f;
                    float weight = pixels[offset];
                    assertTrue(weight >= 0 && weight <= 1 && weight <= previous, "阴影权重须有界并随距离单调减弱");
                    previous = weight;
                    boolean outside = range == 0 || distance >= range;
                    assertEquals(outside ? 1 : 2, Math.round(pixels[offset + 1]), "范围判断必须先于预算判断");
                    assertEquals(0, Math.round(pixels[offset + 2]), "禁用或范围外不得执行深度查询");
                    assertEquals(outside ? 0 : 1, Math.round(pixels[offset + 3]));
                }
                if (range > 0) {
                    int start = Math.round(range * .75f * 64), end = Math.round(range * 64);
                    int middle = Math.round(range * .875f * 64);
                    assertEquals(1, pixels[start * 4], 1e-6);
                    assertEquals(.5, pixels[middle * 4], 1e-6);
                    assertEquals(0, pixels[end * 4], 1e-6);
                    assertTrue(pixels[start * 4] - pixels[(start + 1) * 4] < .001);
                    assertTrue(pixels[(end - 1) * 4] - pixels[end * 4] < .001);
                } else for (int x = 0; x <= 1024; x++) assertEquals(0, pixels[x * 4]);
            }
            float[] depth = new float[256 * 64];
            for (int y = 0; y < 64; y++) for (int x = 0; x < 256; x++)
                depth[y * 256 + x] = 1 - .05f / (x >= 96 && x < 104 ? 10 : 20);
            float[] inside = renderTraceDepth(trace, 256, 64, 20, 5, 0, 32, -1, -.05f, depth, false, 32);
            assertEquals(0, state(inside, 256, 150, 32), "范围内必须保留整条路径上的遮挡，不能只查受光点附近0.5m");
            float[] outside = renderTraceDepth(trace, 256, 64, 20, 5, 0, 32, -1, -.05f, depth, false, 8);
            assertEquals(1, state(outside, 256, 150, 32));
            assertEquals(0, Math.round(outside[(32 * 256 + 150) * 4 + 1]));
            System.out.println("PASS: range 0/8/16, smooth fade endpoints/midpoint, zero-query cull, full in-range path");
        } finally {
            glUseProgram(0); glDeleteProgram(fade); glDeleteProgram(trace);
        }
    }

    /** 使用生产构建器；反射只跨测试包，不为测试扩张客户端类型的可见性。 */
    private static final class Hierarchy implements AutoCloseable {
        private final Object instance;
        private final java.lang.reflect.Method build, texture, levels, close;
        Hierarchy() throws Exception {
            Class<?> type = Class.forName("io.github.sweetzonzi.machine_max.client.render.post.ScreenSpaceDepthPyramid");
            var constructor = type.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            instance = constructor.newInstance(Files.readString(Path.of("src/main/resources/assets/machine_max/shaders/program/screen_space_depth_reduce.fsh")));
            build = type.getDeclaredMethod("build", int.class, int.class, int.class, float.class, float.class);
            texture = type.getDeclaredMethod("texture"); levels = type.getDeclaredMethod("levels"); close = type.getDeclaredMethod("close");
            for (var method : new java.lang.reflect.Method[]{build, texture, levels, close}) method.setAccessible(true);
        }
        void build(int input, int w, int h, float a, float b) {
            try { build.invoke(instance, input, w, h, a, b); } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        }
        int texture() { try { return (int) texture.invoke(instance); } catch (ReflectiveOperationException e) { throw new RuntimeException(e); } }
        int levels() { try { return (int) levels.invoke(instance); } catch (ReflectiveOperationException e) { throw new RuntimeException(e); } }
        public void close() { try { close.invoke(instance); } catch (ReflectiveOperationException e) { throw new RuntimeException(e); } }
    }

    private static void verifiesHierarchy(String functions, String captureSource) {
        int trace = program(functions + TRACE_MAIN);
        try {
            float[] longRay = renderTrace(trace, 512, 64, 20, 5, 0, 32, 336, 348, 10.5f, false);
            assertEquals(0, state(longRay, 512, 480, 32), "分层遍历必须找到128像素前缀以外的相交");
            assertTrue(longRay[(32 * 512 + 480) * 4 + 1] < 128, "粗格跨越应给细查留下预算");
            float rayZ = (float) (1.0 / (.05 + .15 * (1 - 1505.5 / 1900.5)));
            float thinZ = (rayZ - .03f - .125f) / 1.001f;
            float[] thin = renderTrace(trace, 2048, 64, 20, 5, 0, 32, 1505, 1506, thinZ, false);
            assertEquals(0, state(thin, 2048, 1900, 32), "粗层必须覆盖稀疏采样点之间的单像素遮挡");
            int sparse = program(captureSource.substring(0, captureSource.indexOf("void main()")) + TRACE_MAIN);
            try {
                glUseProgram(sparse);
                glUniform1f(glGetUniformLocation(sparse, "CaptureSamples"), 64);
                glUniform1f(glGetUniformLocation(sparse, "CaptureRayBudget"), 64);
                float[] missed = renderTrace(sparse, 2048, 64, 20, 5, 0, 32, 1505, 1506, thinZ, false);
                assertNotEquals(0, state(missed, 2048, 1900, 32), "该夹具必须表达点间漏采，不能退化成两种遍历都命中");
            } finally { glUseProgram(0); glDeleteProgram(sparse); }
            float[] standard = renderTrace(trace, 256, 64, 20, 5, 0, 32, 96, 104, 10, false);
            double front = 10.0 + .03 + .001 * 10.0;
            verifySingleBand(standard, 256, 32, 105, 180, front, front + .25, 20, 5, 96, 104);
            float[] reversed = renderTrace(trace, 256, 64, 20, 5, 0, 32, 96, 104, 10, true);
            for (int x = 105; x < 180; x++) assertEquals(state(standard, 256, x, 32), state(reversed, 256, x, 32));
            assertNoHits(renderTrace(trace, 256, 64, 20, 12, 0, 32, 96, 104, 10, false), "有限层后方穿越不能命中");
            assertNoHits(renderTrace(trace, 256, 64, 20, 20, 0, 32, 0, 256, 20, false), "同平面不能自遮挡");
            int w = 513, h = 67;
            float[] depth = new float[w * h]; Arrays.fill(depth, 1 - .05f / 20);
            // 同一粗格的邻行存在遮挡物，光线所在行为空，粗层不能直接确认命中。
            for (int x = 336; x < 348; x++) depth[33 * w + x] = 1 - .05f / 10.5f;
            float[] beside = renderTraceDepth(trace, w, h, 20, 5, 0, 32, -1, -.05f, depth, false);
            assertNotEquals(0, state(beside, w, 480, 32), "光线路径外的遮挡物只能触发细查，不能产生阴影");
            // 非2幂尺寸最右下角的单像素必须进入顶层范围，缺深度标记必须保留。
            depth[(h - 1) * w + w - 1] = 1 - .05f / 5;
            depth[0] = 1;
            int input = texture(w, h, GL_RED, depth);
            verifiesHierarchyState(input, w, h);
            hierarchy.build(input, w, h, -1, -.05f);
            glBindTexture(GL_TEXTURE_2D, hierarchy.texture());
            float[] range = new float[2]; glGetTexImage(GL_TEXTURE_2D, hierarchy.levels() - 1, GL_RG, GL_FLOAT, range);
            assertTrue(range[0] < 0, "顶层必须保留缺深度标记");
            assertEquals(1.0 / 20, Math.abs(range[0]), 1e-4, "归并不得遗失最小倒数深度");
            assertEquals(1.0 / 5, range[1], 1e-4, "奇数尺寸最后一个像素不得遗失");
            glDeleteTextures(input);
            System.out.println("PASS: hierarchy long-ray / between-sample single pixel / finite layer / reversed depth / off-ray rejection / odd-size extrema");
        } finally { glUseProgram(0); glDeleteProgram(trace); }
    }

    private static void verifiesHierarchyState(int input, int w, int h) {
        try {
            Class<?> type = Class.forName("io.github.sweetzonzi.machine_max.client.render.post.PostProcessingGpuState");
            var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
            var restore = type.getDeclaredMethod("restore"); restore.setAccessible(true);
            var matches = type.getDeclaredMethod("matches", type); matches.setAccessible(true);
            Object original = constructor.newInstance();
            int sampler = glGenSamplers();
            try {
                glBindSampler(0, sampler);
                glActiveTexture(GL_TEXTURE3);
                glViewport(3, 5, 7, 9); glScissor(1, 2, 4, 8);
                glEnable(GL_SCISSOR_TEST); glEnable(GL_DEPTH_TEST); glEnable(GL_BLEND); glEnable(GL_CULL_FACE);
                glColorMask(false, true, false, true); glDepthMask(false); glDepthFunc(GL_GREATER);
                Object before = constructor.newInstance();
                hierarchy.build(input, w, h, -1, -.05f);
                assertEquals(true, matches.invoke(before, constructor.newInstance()), "生产层级构建必须恢复进入时的原生GL状态");
            } finally {
                restore.invoke(original);
                glDeleteSamplers(sampler);
            }
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }

    private static String withBudget(String source, int budget) {
        return source.replaceFirst("OCCL_MAX_STEPS\\s*=\\s*\\d+", "OCCL_MAX_STEPS = " + budget)
                .replaceFirst("OCCL_PIXEL_STEPS\\s*=\\s*\\d+", "OCCL_PIXEL_STEPS = " + budget);
    }

    private static void verifiesDepthStencilReadback() {
        int depth = glGenTextures(), fbo = glGenFramebuffers();
        try {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, depth);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH32F_STENCIL8, 2, 2, 0,
                    GL_DEPTH_STENCIL, GL_FLOAT_32_UNSIGNED_INT_24_8_REV, (java.nio.ByteBuffer) null);
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, depth, 0);
            glDrawBuffer(GL_NONE); glReadBuffer(GL_NONE);
            assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
            glClearBufferfi(GL_DEPTH_STENCIL, 0, .5f, 7);
            float[] values = new float[4];
            glGetTexImage(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, GL_FLOAT, values);
            assertEquals(GL_NO_ERROR, glGetError(), "诊断必须能从带模板的深度纹理读回float深度");
            for (float value : values) assertEquals(.5f, value);
            System.out.println("PASS: GL_DEPTH32F_STENCIL8 -> float depth readback");
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glDeleteFramebuffers(fbo); glDeleteTextures(depth);
        }
    }

    private static double parameter(String source, String name) {
        var match = java.util.regex.Pattern.compile("\\b" + name + "\\s*=\\s*([0-9.eE+-]+)").matcher(source);
        assertTrue(match.find(), "缺少标定参数 " + name);
        return Double.parseDouble(match.group(1));
    }

    private static int program(String fragment) {
        int vertex = compile(GL_VERTEX_SHADER, VERTEX);
        int pixel = compile(GL_FRAGMENT_SHADER, fragment);
        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, pixel);
        glBindFragDataLocation(program, 0, "fragColor");
        glLinkProgram(program);
        String log = glGetProgramInfoLog(program);
        glDeleteShader(vertex);
        glDeleteShader(pixel);
        assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), log);
        return program;
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), glGetShaderInfoLog(shader));
        return shader;
    }

    private static int texture(int w, int h, int format, float[] values) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, format == GL_RED ? GL_R32F : GL_RGBA32F, w, h, 0, format, GL_FLOAT, values);
        return id;
    }

    private static float[] renderTrace(int program, int w, int h, float receiverZ, float lightZ,
                                       float lightX, float lightY, int block0, int block1, float blockerZ,
                                       boolean reversed) {
        float a = reversed ? 0 : -1;
        float b = reversed ? .05f : -.05f;
        float[] depth = new float[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int axis = w < h ? y : x;
            float z = axis >= block0 && axis < block1 ? blockerZ : receiverZ;
            depth[y * w + x] = z == 0 ? (reversed ? 0 : 1) : b / z - a;
        }
        return renderTraceDepth(program, w, h, receiverZ, lightZ, lightX, lightY, a, b, depth, false);
    }

    private static float[] renderTraceDepth(int program, int w, int h, float receiverZ, float lightZ,
                                            float lightX, float lightY, float a, float b, float[] depth,
                                            boolean sceneReceiver) {
        return renderTraceDepth(program, w, h, receiverZ, lightZ, lightX, lightY, a, b, depth, sceneReceiver, 10_000);
    }

    private static float[] renderTraceDepth(int program, int w, int h, float receiverZ, float lightZ,
                                            float lightX, float lightY, float a, float b, float[] depth,
                                            boolean sceneReceiver, float shadowRange) {
        glActiveTexture(GL_TEXTURE0);
        int depthTexture = texture(w, h, GL_RED, depth);
        hierarchy.build(depthTexture, w, h, a, b);
        int target = texture(w, h, GL_RGBA, (float[]) null);
        int fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
        assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
        glUseProgram(program);
        glUniform2f(glGetUniformLocation(program, "OutSize"), w, h);
        glUniform1f(glGetUniformLocation(program, "FocalPx"), 128);
        glUniform1f(glGetUniformLocation(program, "DepthA"), a);
        glUniform1f(glGetUniformLocation(program, "DepthB"), b);
        glUniform1f(glGetUniformLocation(program, "ProbeReceiverZ"), receiverZ);
        glUniform1f(glGetUniformLocation(program, "ProbeSceneReceiver"), sceneReceiver ? 1 : 0);
        glUniform1f(glGetUniformLocation(program, "ShadowRange"), shadowRange);
        glUniform3f(glGetUniformLocation(program, "ProbeLight"), (lightX - w * .5f) * lightZ / 128,
                (lightY - h * .5f) * lightZ / 128, -lightZ);
        glUniform1i(glGetUniformLocation(program, "MCDepthSampler"), 0);
        glUniform1i(glGetUniformLocation(program, "DepthHierarchySampler"), 1);
        glUniform1f(glGetUniformLocation(program, "HierarchyLevels"), hierarchy.levels());
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, hierarchy.texture());
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, depthTexture);
        glViewport(0, 0, w, h);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        float[] pixels = new float[w * h * 4];
        glReadPixels(0, 0, w, h, GL_RGBA, GL_FLOAT, pixels);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDeleteFramebuffers(fbo);
        glDeleteTextures(target);
        glDeleteTextures(depthTexture);
        return pixels;
    }

    private static int state(float[] rgba, int width, int x, int y) {
        return Math.round(rgba[(y * width + x) * 4]);
    }

    private static void assertNoHits(float[] rgba, String message) {
        for (int i = 0; i < rgba.length; i += 4) assertNotEquals(0, Math.round(rgba[i]), message);
    }

    private static void verifySingleBand(float[] rgba, int w, int y, int from, int to,
                                         double front, double back, float receiverZ, float lightZ, int block0, int block1) {
        // 解析解由一个有限厚度遮挡层投影到平行接收面；不依赖 DDA 的内部步序。
        double tFront = (1 / front - 1 / receiverZ) / (1 / lightZ - 1 / receiverZ);
        double tBack = (1 / back - 1 / receiverZ) / (1 / lightZ - 1 / receiverZ);
        double expected0 = block0 / (1 - tBack), expected1 = block1 / (1 - tFront);
        int bands = 0, first = -1, last = -1;
        boolean previous = false;
        for (int x = from; x < to; x++) {
            boolean hit = state(rgba, w, x, y) == 0;
            if (hit && !previous) bands++;
            if (hit) { if (first < 0) first = x; last = x; }
            previous = hit;
        }
        assertEquals(1, bands, "单层遮挡的投影必须连续");
        assertTrue(Math.abs(first + .5 - expected0) <= 1, "前边界 " + first + " != " + expected0);
        assertTrue(Math.abs(last + .5 - expected1) <= 1, "后边界 " + last + " != " + expected1);
        System.out.printf(Locale.ROOT, "single-band GPU=[%.1f,%.1f] analytic=[%.4f,%.4f]%n", first + .5, last + .5, expected0, expected1);
    }

    private static void saveStates(String name, float[] rgba, int w, int h) throws Exception {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] colors = {0xff3030, 0x30c050, 0x3050ff};
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) image.setRGB(x, h - 1 - y, colors[state(rgba, w, x, y)]);
        ImageIO.write(image, "png", OUTPUT.resolve(name + ".png").toFile());
    }

    private static void benchmark(String source) throws Exception {
        StringBuilder csv = new StringBuilder("shader,width,height,lights,hierarchy_build_ms,light_gpu_ms\n");
        Path baseline = OUTPUT.resolve("before.fsh");
        for (int budget : new int[]{-1, 0, 64, 128, 256}) {
            Path input = budget == -1 ? OUTPUT.resolve("before-hierarchy.fsh") : baseline;
            if (budget <= 0 && !Files.exists(input)) continue;
            String candidate = budget <= 0 ? Files.readString(input) : withBudget(source, budget);
            int program = program(candidate);
            try {
                for (int[] size : new int[][]{{1920, 1080}, {3840, 2019}}) {
                    for (int lights : new int[]{1, 4, 16}) {
                        double[] ms = benchmarkPass(program, size[0], size[1], lights);
                        String label = budget == -1 ? "major-axis-128" : budget == 0 ? "baseline-7" : "hierarchy-" + budget;
                        csv.append(String.format(Locale.ROOT, "%s,%d,%d,%d,%.4f,%.4f%n", label, size[0], size[1], lights, ms[0], ms[1]));
                    }
                }
            } finally {
                glUseProgram(0); glDeleteProgram(program);
            }
        }
        Files.writeString(OUTPUT.resolve("gpu-hierarchy-benchmark.csv"), csv);
        System.out.print(csv);
    }

    private static double[] benchmarkPass(int program, int w, int h, int lights) {
        float[] depth = new float[w * h];
        Arrays.fill(depth, 1 - .05f / 20);
        for (int y = h / 3; y < h * 2 / 3; y++) Arrays.fill(depth, y * w + w / 3, y * w + w / 3 + w / 16, 1 - .05f / 10);
        glActiveTexture(GL_TEXTURE0);
        int depthTexture = texture(w, h, GL_RED, depth);
        double buildMs = 0.0;
        if (glGetUniformLocation(program, "HierarchyLevels") >= 0) {
            hierarchy.build(depthTexture, w, h, -1, -.05f);
            int buildQuery = glGenQueries();
            glBeginQuery(GL_TIME_ELAPSED, buildQuery);
            hierarchy.build(depthTexture, w, h, -1, -.05f);
            glEndQuery(GL_TIME_ELAPSED);
            buildMs = glGetQueryObjectui64(buildQuery, GL_QUERY_RESULT) / 1.0e6;
            glDeleteQueries(buildQuery);
        }
        float[] color = new float[w * h * 4];
        Arrays.fill(color, .1f);
        glActiveTexture(GL_TEXTURE1);
        int diffuse = texture(w, h, GL_RGBA, color);
        int target = texture(w, h, GL_RGBA, (float[]) null);
        int fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
        glUseProgram(program);
        glUniform2f(glGetUniformLocation(program, "OutSize"), w, h);
        glUniform1f(glGetUniformLocation(program, "FocalPx"), h * .85f);
        glUniform1f(glGetUniformLocation(program, "DepthA"), -1);
        glUniform1f(glGetUniformLocation(program, "DepthB"), -.05f);
        glUniform1f(glGetUniformLocation(program, "LightCount"), lights);
        glUniform1f(glGetUniformLocation(program, "NormalWeight"), .5f);
        glUniform1f(glGetUniformLocation(program, "ShadowRange"), 8);
        float[] view = new float[64], radiance = new float[64], minDistance = new float[16];
        for (int i = 0; i < 16; i++) {
            view[i * 4] = -5 + i * .25f; view[i * 4 + 1] = .2f * (i % 3); view[i * 4 + 2] = -5; view[i * 4 + 3] = 32;
            radiance[i * 4] = 1; radiance[i * 4 + 1] = 1; radiance[i * 4 + 2] = 1; radiance[i * 4 + 3] = 90;
            minDistance[i] = .5f;
        }
        glUniform1fv(glGetUniformLocation(program, "LightView"), view);
        glUniform1fv(glGetUniformLocation(program, "LightColor"), radiance);
        glUniform1fv(glGetUniformLocation(program, "LightMinDist"), minDistance);
        glUniform1i(glGetUniformLocation(program, "MCDepthSampler"), 0);
        glUniform1i(glGetUniformLocation(program, "DiffuseSampler"), 1);
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, depthTexture);
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, diffuse);
        glUniform1i(glGetUniformLocation(program, "DepthHierarchySampler"), 2);
        glUniform1f(glGetUniformLocation(program, "HierarchyLevels"), hierarchy.levels());
        glActiveTexture(GL_TEXTURE2); glBindTexture(GL_TEXTURE_2D, hierarchy.texture());
        glViewport(0, 0, w, h);
        for (int i = 0; i < 3; i++) glDrawArrays(GL_TRIANGLES, 0, 3);
        int query = glGenQueries();
        glBeginQuery(GL_TIME_ELAPSED, query);
        for (int i = 0; i < 5; i++) glDrawArrays(GL_TRIANGLES, 0, 3);
        glEndQuery(GL_TIME_ELAPSED);
        double ms = glGetQueryObjectui64(query, GL_QUERY_RESULT) / 5.0e6;
        glDeleteQueries(query);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDeleteFramebuffers(fbo);
        glDeleteTextures(target); glDeleteTextures(diffuse); glDeleteTextures(depthTexture);
        return new double[]{buildMs, ms};
    }
}
