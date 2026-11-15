package io.github.sweetzonzi.machine_max.client.render.post;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.lwjgl.opengl.GL32C.*;

/**
 * 同帧光照诊断，客户端主线程/渲染线程使用。请求只消费一次，无光源时等待至多 5 秒。
 * 复用生产片元源码和本帧输入，在独立颜色目标重放；原主目标、世界深度和正常 pass 的 uniform 均不写入。
 */
final class ScreenSpaceLightCapture {
    private static final ResourceLocation FRAGMENT = ResourceLocation.parse("machine_max:shaders/program/screen_space_light.fsh");
    private static final String VERTEX = ScreenSpaceDepthPyramid.VERTEX;
    private long requestedAt;
    private boolean requested;

    void request() {
        if (requested) return;
        requested = true;
        requestedAt = Util.getMillis();
        notify(MMLanguageRegistry.MESSAGE_LIGHT_CAPTURE_ARMED);
    }

    void expireRequest() {
        if (requested && Util.getMillis() - requestedAt >= 5_000) {
            requested = false;
            notify(MMLanguageRegistry.MESSAGE_LIGHT_CAPTURE_EMPTY);
        }
    }

    void cancel() {
        requested = false;
    }

    boolean isRequested() { return requested; }

    void capture(RenderTarget main, RenderTarget worldDepth, ScreenSpaceDepthPyramid hierarchy, LightFrameContext frame,
                 ScreenSpaceLightBuffer lights, float depthA, float depthB, float normalWeight, float occlusionTolerance,
                 float shadowRange) {
        if (!requested) return;
        requested = false; // 包括失败路径在内均只尝试一次。
        RenderSystem.assertOnRenderThread();
        Minecraft mc = Minecraft.getInstance();
        Path directory = mc.gameDirectory.toPath().resolve("screenshots")
                .resolve("mm-light-" + Util.getFilenameFormattedDateTime() + "-" + System.currentTimeMillis());
        PostProcessingGpuState before = new PostProcessingGpuState();
        int program = 0, outputTexture = 0, framebuffer = 0, vao = 0;
        boolean saved = false;
        JsonObject metadata = null;
        try {
            int w = main.width, h = main.height;
            Files.createDirectories(directory);
            String fragment;
            try (var stream = mc.getResourceManager().getResourceOrThrow(FRAGMENT).open()) {
                fragment = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            int versionEnd = fragment.indexOf('\n');
            if (!fragment.startsWith("#version") || versionEnd < 0) throw new IllegalStateException("Capture shader needs a GLSL version line");
            fragment = fragment.substring(0, versionEnd + 1) + "#define MM_LIGHT_CAPTURE 1\n" + fragment.substring(versionEnd + 1);
            Files.writeString(directory.resolve("fragment.fsh"), fragment, StandardCharsets.UTF_8);
            Files.writeString(directory.resolve("vertex.vsh"), VERTEX, StandardCharsets.UTF_8);
            Files.writeString(directory.resolve("depth_reduce.fsh"), hierarchy.fragment(), StandardCharsets.UTF_8);
            preparePixelRead();
            saveColor(main.getColorTextureId(), w, h, directory.resolve("00_without_added_light.png"));
            saveDepth(worldDepth.getDepthTextureId(), w, h, directory.resolve("depth.f32be"));

            program = createProgram(fragment);
            vao = glGenVertexArrays();
            outputTexture = glGenTextures();
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, outputTexture);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            framebuffer = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, outputTexture, 0);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Capture framebuffer is incomplete");
            glBindVertexArray(vao);
            glUseProgram(program);
            glViewport(0, 0, w, h);
            for (int capability : PostProcessingGpuState.CAPABILITIES) glDisable(capability);
            glEnable(GL_SCISSOR_TEST);
            glColorMask(true, true, true, true);
            if (before.samplersSupported) { GL33C.glBindSampler(0, 0); GL33C.glBindSampler(1, 0); GL33C.glBindSampler(2, 0); }
            set(program, "HierarchyLevels", hierarchy.levels());
            glUniform1i(glGetUniformLocation(program, "DepthHierarchySampler"), 2);
            commonUniforms(program, w, h, frame.focalPx(), depthA, depthB, normalWeight, occlusionTolerance, lights);

            metadata = metadata(frame, lights, depthA, depthB, normalWeight, occlusionTolerance);
            metadata.addProperty("capture_format_version", 1);
            metadata.addProperty("effect", "machine_max:screen_space_light");
            metadata.addProperty("depth_encoding", "float32-be-bottom-up");
            metadata.addProperty("fragment_sha256", sha256(fragment));
            metadata.addProperty("vertex_sha256", sha256(VERTEX));
            metadata.addProperty("depth_reduce_sha256", sha256(hierarchy.fragment()));
            metadata.addProperty("color_sha256", sha256File(directory.resolve("00_without_added_light.png")));
            metadata.addProperty("depth_sha256", sha256File(directory.resolve("depth.f32be")));
            metadata.addProperty("ShadowRange", shadowRange);
            metadata.addProperty("shadow_fade_start_fraction", 0.75f);
            metadata.addProperty("hierarchy_levels", hierarchy.levels());
            JsonArray comparisons = new JsonArray();
            for (ScreenSpaceLightDiagnosticPreset preset : ScreenSpaceLightDiagnosticPreset.forFrame(w, h, lights.count())) {
                var controls = preset.uniforms(shadowRange);
                float effectiveRange = controls.get("ShadowRange");
                for (var entry : controls.entrySet()) set(program, entry.getKey(), entry.getValue());
                set(program, "CaptureOutput", 0);
                long start = System.nanoTime();
                drawTiles(main, worldDepth, hierarchy.texture(), w, h);
                double renderMs = (System.nanoTime() - start) / 1.0e6;
                saveColor(outputTexture, w, h, directory.resolve(preset.name() + ".png"));
                if (preset.mask()) {
                    set(program, "CaptureOutput", 1);
                    drawTiles(main, worldDepth, hierarchy.texture(), w, h);
                    saveColor(outputTexture, w, h, directory.resolve(preset.name() + "_reasons.png"));
                }
                JsonObject item = new JsonObject();
                item.addProperty("name", preset.name());
                item.addProperty("ray_budget_override", preset.rayBudget());
                item.addProperty("pixel_budget_override", preset.pixelBudget());
                item.addProperty("whole_path_samples", preset.samples());
                item.addProperty("traversal", preset.traversal() == 0 ? "hierarchy" : preset.traversal() == 1 ? "major_axis_dda" : "pixel_grid");
                item.addProperty("thickness_override_m", preset.thickness());
                item.addProperty("legacy_seven_point_average", preset.legacy());
                item.addProperty("normal_space_thickness", preset.normalThickness());
                item.addProperty("occlusion_disabled", preset.occlusionOff());
                item.addProperty("only_light_slot", preset.onlyLight() - 1);
                item.addProperty("end_exclusion_override_m", preset.endExclusion());
                item.addProperty("shadow_range_m", effectiveRange);
                item.add("uniforms", new GsonBuilder().create().toJsonTree(controls));
                item.addProperty("tiled_render_wall_ms", renderMs);
                comparisons.add(item);
            }
            metadata.add("comparisons", comparisons);
            Files.writeString(directory.resolve("README.txt"), """
                    所有图片使用同一帧的颜色、世界深度、投影和光源，世界画面不含手部/HUD。
                    可用仓库tools/shader-diagnostics/diagnose.ps1独立回放，使用说明见该目录README.md。
                    01_current：当前分层深度遍历；02_dda512：逐像素DDA，单条/像素总预算均为512。
                    03_uniform64、04_uniform128：每盏灯64/128个中点覆盖全程，有限厚度，命中即早退。
                    05_full：逐像素走完整可见路径，各灯均有完整预算；不表示补回隐藏/离屏几何。
                    06_full_thickness1m：只把05的厚度改为1m，用于判断厚度敏感性，不代表几何正确答案。
                    07_legacy7：7点覆盖全程、单边深度、沿线命中平均；只重放旧遮挡算子。
                    08_hierarchy128：分层查询预算128；09_hierarchy_full：分层遍历的充足预算参考。
                    10_normal_thickness：完整遍历，0.25m沿局部法线给出；仅为斜角候选，不是默认模型。
                    11_without_surface_shadow：只关闭表面项S3，保留法线/衰减/光晕/星芒。
                    12_lightN：独立保留槽N光源与原因图，各有完整默认预算；可对照frame.json的光心。
                    13_end05、14_end10：光心端排除0.5m/1m，定位炮口邻近的自遮挡。
                    15_range8、16_range16、17_range64：只调整遮挡计算范围为8/16/64米，范围末端25%平滑淡出。
                    其余各组使用frame.json的ShadowRange；范围内检查完整P→C，full表示预算充足，仍受范围限制。
                    法线厚度候选的步数只计主深度查询，不含额外邻域法线采样；仅用于离线对照。
                    原因图按所有参与表面光源聚合：橙=预算耗尽，紫=裁剪，蓝=缺有效深度，
                    青=全程点采样未命中（点间部分未检查），红=命中，绿=完整未命中，暗灰=未查询S3（无表面贡献或超范围）。
                    多原因同时存在时依次优先显示橙、紫、蓝、青；有灯命中不消除其他灯的未知原因。
                    depth.f32be：大端float32，OpenGL自下而上行序；PNG已翻转为自上而下。
                    frame.json包含全部光源数组、投影/视图矩阵和标定参数；fragment.fsh、vertex.vsh是本次实际重放的源码；SHA-256记录在元数据中。
                    override=0表示使用源码默认值。render_wall_ms含分块提交/同步，不是正式GPU帧时间基准。
                    """, StandardCharsets.UTF_8);
            saved = true;
        } catch (IOException | RuntimeException e) {
            MachineMax.LOGGER.error("同帧光照对照截取失败：{}", directory, e);
        } finally {
            if (program != 0) glDeleteProgram(program);
            if (vao != 0) glDeleteVertexArrays(vao);
            if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
            if (outputTexture != 0) glDeleteTextures(outputTexture);
            before.restore();
        }
        if (saved) {
            boolean restored = before.matches(new PostProcessingGpuState());
            metadata.addProperty("gl_state_restored", restored);
            try {
                Files.writeString(directory.resolve("frame.json"), new GsonBuilder().setPrettyPrinting().create().toJson(metadata), StandardCharsets.UTF_8);
                if (!restored) throw new IllegalStateException("Capture changed native GL state");
                MachineMax.LOGGER.info("同帧光照对照已保存，原生GL状态核对通过：{}", directory.toAbsolutePath());
            } catch (IOException | RuntimeException e) {
                saved = false;
                MachineMax.LOGGER.error("光照对照结果收尾失败：{}", directory, e);
            }
        }
        if (saved) notify(MMLanguageRegistry.MESSAGE_LIGHT_CAPTURE_SAVED, directory.toAbsolutePath().toString());
        else notify(MMLanguageRegistry.MESSAGE_LIGHT_CAPTURE_FAILED);
    }

    private static void drawTiles(RenderTarget main, RenderTarget depth, int hierarchy, int w, int h) {
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, main.getColorTextureId());
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, depth.getDepthTextureId());
        glActiveTexture(GL_TEXTURE2); glBindTexture(GL_TEXTURE_2D, hierarchy);
        // 每个draw只处理128²像素，逐行同步，限制充足预算参考图的连续GPU执行时间。
        for (int y = 0; y < h; y += 128) {
            for (int x = 0; x < w; x += 128) {
                glScissor(x, y, Math.min(128, w - x), Math.min(128, h - y));
                glDrawArrays(GL_TRIANGLES, 0, 3);
            }
            glFinish();
        }
    }

    private static void saveColor(int texture, int w, int h, Path file) throws IOException {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        try (NativeImage image = new NativeImage(w, h, false)) {
            image.downloadTexture(0, true);
            image.flipY();
            image.writeToFile(file);
        }
    }

    private static void saveDepth(int texture, int w, int h, Path file) throws IOException {
        FloatBuffer values = MemoryUtil.memAllocFloat(w * h);
        try {
            glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texture);
            glGetTexImage(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, GL_FLOAT, values);
            try (DataOutputStream stream = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
                for (int i = 0; i < values.capacity(); i++) stream.writeFloat(values.get(i));
            }
        } finally {
            MemoryUtil.memFree(values);
        }
    }

    private static void preparePixelRead() {
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        for (int name : PostProcessingGpuState.PACK_PARAMETERS) glPixelStorei(name, name == GL_PACK_ALIGNMENT ? 1 : 0);
    }

    private static JsonObject metadata(LightFrameContext frame, ScreenSpaceLightBuffer lights, float a, float b, float normalWeight, float tolerance) {
        var gson = new GsonBuilder().create();
        JsonObject out = new JsonObject();
        out.addProperty("width", frame.viewportWidth()); out.addProperty("height", frame.viewportHeight());
        out.addProperty("focal_px", frame.focalPx()); out.addProperty("depth_a", a); out.addProperty("depth_b", b);
        out.addProperty("normal_weight", normalWeight); out.addProperty("light_count", lights.count());
        out.addProperty("gl_renderer", glGetString(GL_RENDERER));
        out.add("view_matrix", gson.toJsonTree(frame.modelViewMatrix().get(new float[16])));
        out.add("projection_matrix", gson.toJsonTree(frame.projectionMatrix().get(new float[16])));
        out.add("LightView", gson.toJsonTree(lights.positionRadius())); out.add("LightColor", gson.toJsonTree(lights.colorIntensity()));
        out.add("LightTol", gson.toJsonTree(lights.sourceDepthTolerance())); out.add("LightOcclusion", gson.toJsonTree(lights.occlusion()));
        out.add("LightMinDist", gson.toJsonTree(lights.minDistance())); out.add("LightGlow", gson.toJsonTree(lights.glow()));
        out.add("LightGlare", gson.toJsonTree(lights.glare()));
        out.addProperty("OcclusionTolRel", tolerance);
        return out;
    }

    private static void commonUniforms(int p, int w, int h, float focal, float a, float b, float normal, float tolerance, ScreenSpaceLightBuffer lights) {
        glUniform2f(glGetUniformLocation(p, "OutSize"), w, h);
        set(p, "FocalPx", focal); set(p, "DepthA", a); set(p, "DepthB", b);
        set(p, "NormalWeight", normal); set(p, "LightCount", lights.count()); set(p, "OcclusionTolRel", tolerance);
        glUniform1i(glGetUniformLocation(p, "DiffuseSampler"), 0); glUniform1i(glGetUniformLocation(p, "MCDepthSampler"), 1);
        glUniform1fv(glGetUniformLocation(p, "LightView"), lights.positionRadius());
        glUniform1fv(glGetUniformLocation(p, "LightColor"), lights.colorIntensity());
        glUniform1fv(glGetUniformLocation(p, "LightTol"), lights.sourceDepthTolerance());
        glUniform1fv(glGetUniformLocation(p, "LightOcclusion"), lights.occlusion());
        glUniform1fv(glGetUniformLocation(p, "LightMinDist"), lights.minDistance());
        glUniform1fv(glGetUniformLocation(p, "LightGlow"), lights.glow());
        glUniform1fv(glGetUniformLocation(p, "LightGlare"), lights.glare());
    }

    private static String sha256File(Path file) throws IOException {
        try (var stream = new java.security.DigestInputStream(Files.newInputStream(file), MessageDigest.getInstance("SHA-256"))) {
            stream.transferTo(java.io.OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(stream.getMessageDigest().digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(String source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(int program, String name, float value) {
        glUniform1f(glGetUniformLocation(program, name), value);
    }

    private static int createProgram(String fragment) {
        int vertex = 0, pixel = 0, program = 0;
        try {
            vertex = compile(GL_VERTEX_SHADER, VERTEX);
            pixel = compile(GL_FRAGMENT_SHADER, fragment);
            program = glCreateProgram();
            glAttachShader(program, vertex); glAttachShader(program, pixel);
            glBindFragDataLocation(program, 0, "fragColor");
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) throw new IllegalStateException(glGetProgramInfoLog(program));
            return program;
        } catch (RuntimeException e) {
            if (program != 0) glDeleteProgram(program);
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
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException(log);
        }
        return shader;
    }

    private static void notify(MMLanguageRegistry message, Object... args) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.translatable(message.tag, args), false);
    }

}
