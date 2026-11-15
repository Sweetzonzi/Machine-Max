package io.github.sweetzonzi.machine_max.client.render.post;

import com.google.gson.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** 保存帧的独立GPU回放与PNG对照入口；运行时只需Java、Gson和LWJGL。 */
public final class ScreenSpaceLightDiagnostics {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PROGRAMS = Path.of("src/main/resources/assets/machine_max/shaders/program");
    private static final String[] ARRAY_NAMES = {"LightView", "LightColor", "LightTol", "LightOcclusion", "LightMinDist", "LightGlow", "LightGlare"};
    private static final int[] ARRAY_LENGTHS = {64, 64, 16, 16, 16, 32, 16};

    public static void main(String[] args) throws Exception {
        Map<String, String> options = options(args);
        if (options.containsKey("help")) {
            System.out.println("shaderDiagnostics --capture DIR [--shader saved|current|FILE] [--preset current|captured|budget|range|thickness|all]\n"
                    + "  [--variants JSON] [--output NEW_DIR] [--baseline PNG] [--threshold 2] [--pixel X,Y] [--light 0]\n"
                    + "shaderDiagnostics --compare-left PNG --compare-right PNG [--output NEW_DIR] [--threshold 2]");
            return;
        }
        int threshold = Integer.parseInt(options.getOrDefault("threshold", "2"));
        if (threshold < 0 || threshold > 255) throw new IllegalArgumentException("threshold must be in [0,255]");
        Path output = options.containsKey("output") ? Path.of(options.get("output")).toAbsolutePath().normalize()
                : Path.of("run/diagnostics/shader-light", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8)).toAbsolutePath();
        if (options.containsKey("compare-left") || options.containsKey("compare-right")) {
            BufferedImage left = readImage(Path.of(required(options, "compare-left")));
            BufferedImage right = readImage(Path.of(required(options, "compare-right")));
            sameSize(left, right);
            createOutput(output);
            writeImage(left, output.resolve("reference.png"));
            writeImage(right, output.resolve("comparison.png"));
            JsonObject report = new JsonObject();
            report.addProperty("mode", "png_comparison");
            JsonArray results = new JsonArray();
            results.add(result("comparison", "comparison.png", compare(left, right, threshold, output.resolve("comparison_diff.png"))));
            report.add("results", results);
            finish(output, report, left, results);
            return;
        }
        Frame frame = Frame.read(Path.of(required(options, "capture")));
        if (output.startsWith(frame.directory)) throw new IllegalArgumentException("Output must be outside the read-only capture directory");
        String mode = options.getOrDefault("shader", "saved");
        Path fragmentFile = mode.equals("saved") ? frame.directory.resolve("fragment.fsh")
                : mode.equals("current") ? PROGRAMS.resolve("screen_space_light.fsh") : Path.of(mode);
        String fragment = diagnosticSource(Files.readString(fragmentFile));
        boolean hierarchy = Pattern.compile("\\buniform\\s+sampler2D\\s+DepthHierarchySampler\\b").matcher(fragment).find();
        Path reducerFile = mode.equals("saved") ? frame.directory.resolve("depth_reduce.fsh") : PROGRAMS.resolve("screen_space_depth_reduce.fsh");
        if (options.containsKey("reducer")) reducerFile = Path.of(options.get("reducer"));
        String reducer = hierarchy ? Files.readString(reducerFile) : "";
        String vertex = mode.equals("saved") && Files.exists(frame.directory.resolve("vertex.vsh"))
                ? Files.readString(frame.directory.resolve("vertex.vsh")) : ScreenSpaceDepthPyramid.VERTEX;
        List<Variant> variants = variants(options, frame);
        int[] pixel = options.containsKey("pixel") ? Arrays.stream(options.get("pixel").split(",")).mapToInt(Integer::parseInt).toArray() : null;
        if (pixel != null && (pixel.length != 2 || pixel[0] < 0 || pixel[0] >= frame.width || pixel[1] < 0 || pixel[1] >= frame.height))
            throw new IllegalArgumentException("Pixel uses PNG top-left coordinates and must lie inside the capture");
        int light = Integer.parseInt(options.getOrDefault("light", "0"));
        if (pixel != null && (light < 0 || light >= frame.lights)) throw new IllegalArgumentException("Light index is outside light_count");
        Path baselineFile = options.containsKey("baseline") ? Path.of(options.get("baseline")) : frame.directory.resolve("01_current.png");
        BufferedImage baseline = options.containsKey("baseline") || Files.exists(baselineFile) ? readImage(baselineFile) : null;
        if (baseline != null) sameSize(frame.color, baseline);
        createOutput(output);
        Files.writeString(output.resolve("fragment.fsh"), fragment);
        Files.writeString(output.resolve("vertex.vsh"), vertex);
        if (hierarchy) Files.writeString(output.resolve("depth_reduce.fsh"), reducer);
        Files.copy(frame.directory.resolve("frame.json"), output.resolve("input-frame.json"));
        if (baseline != null) writeImage(baseline, output.resolve("reference.png"));
        JsonObject report = new JsonObject();
        report.addProperty("mode", "screen_space_light_replay");
        report.addProperty("capture", frame.directory.toString());
        report.addProperty("capture_format_version", frame.version);
        report.addProperty("available_input_hashes_verified", frame.hashesVerified);
        report.addProperty("shader", fragmentFile.toAbsolutePath().normalize().toString());
        report.addProperty("fragment_sha256", sha256(fragment.getBytes(StandardCharsets.UTF_8)));
        report.addProperty("frame_json_sha256", sha256(Files.readAllBytes(frame.directory.resolve("frame.json"))));
        report.addProperty("frame_shadow_range_m", frame.range);
        report.addProperty("legacy_unbounded_range", !frame.metadata.has("ShadowRange"));
        report.addProperty("baseline", baseline == null ? "none" : baselineFile.toAbsolutePath().normalize().toString());
        report.addProperty("timing_note", "tiled_render_wall_ms includes CPU submission and synchronization; it is not a production GPU benchmark");
        JsonArray results = new JsonArray();
        report.add("results", results);
        try (Gpu gpu = new Gpu(frame, vertex, fragment, reducer)) {
            report.addProperty("gl_renderer", glGetString(GL_RENDERER));
            report.addProperty("gl_version", glGetString(GL_VERSION));
            for (Variant variant : variants) {
                long start = System.nanoTime();
                BufferedImage image = gpu.render(variant, false);
                double ms = (System.nanoTime() - start) / 1e6;
                writeImage(image, output.resolve(variant.name + ".png"));
                JsonObject item = result(variant.name, variant.name + ".png", baseline == null ? null
                        : compare(baseline, image, threshold, output.resolve(variant.name + "_diff.png")));
                item.add("overrides", JSON.toJsonTree(variant.uniforms));
                item.add("uniforms", JSON.toJsonTree(gpu.effectiveUniforms(variant)));
                item.addProperty("tiled_render_wall_ms", ms);
                if (variant.reasons && gpu.uniform("CaptureOutput") >= 0) {
                    BufferedImage reasons = gpu.render(variant, true);
                    writeImage(reasons, output.resolve(variant.name + "_reasons.png"));
                    item.add("reason_counts", reasonCounts(reasons));
                }
                if (pixel != null) item.add("pixel", gpu.pixel(variant, pixel[0], pixel[1], light));
                results.add(item);
                System.out.println(variant.name + (item.has("comparison") ? ": changed=" + item.getAsJsonObject("comparison").get("changed_pixels") : ": rendered"));
            }
            if (glGetError() != GL_NO_ERROR) throw new IllegalStateException("OpenGL error during replay");
        } catch (Exception e) {
            Files.writeString(output.resolve("error.txt"), e.toString());
            throw e;
        }
        finish(output, report, baseline, results);
    }

    private static Map<String, String> options(String[] args) {
        Set<String> allowed = Set.of("capture", "shader", "reducer", "preset", "variants", "output", "baseline", "threshold", "pixel", "light", "compare-left", "compare-right", "help");
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--") || !allowed.contains(args[i].substring(2))) throw new IllegalArgumentException("Unknown option: " + args[i]);
            String name = args[i].substring(2);
            if (result.containsKey(name)) throw new IllegalArgumentException("Duplicate option: " + name);
            if (name.equals("help")) result.put(name, "true");
            else {
                if (++i == args.length) throw new IllegalArgumentException("Missing value for " + name);
                result.put(name, args[i]);
            }
        }
        return result;
    }

    private static String required(Map<String, String> options, String key) {
        if (!options.containsKey(key)) throw new IllegalArgumentException("Missing --" + key + "; use --help");
        return options.get(key);
    }

    private record Variant(String name, Map<String, Float> uniforms, boolean reasons) {
        Variant {
            if (!name.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,79}")) throw new IllegalArgumentException("Invalid variant name: " + name);
            for (var entry : uniforms.entrySet()) {
                if (!entry.getKey().matches("[A-Za-z][A-Za-z0-9_]*") || entry.getKey().equals("CaptureOutput") || !Float.isFinite(entry.getValue()))
                    throw new IllegalArgumentException("Invalid scalar override: " + entry);
            }
            uniforms = Map.copyOf(uniforms);
        }
    }

    private static List<Variant> variants(Map<String, String> options, Frame frame) throws IOException {
        List<Variant> result = new ArrayList<>();
        String group = options.getOrDefault("preset", "current");
        var all = ScreenSpaceLightDiagnosticPreset.forFrame(frame.width, frame.height, frame.lights);
        switch (group) {
            case "current" -> result.add(new Variant("01_current", Map.of(), true));
            case "all" -> all.forEach(p -> result.add(new Variant(p.name(), p.uniforms(frame.range), p.mask())));
            case "range" -> {
                for (int range : new int[]{0, 8, 16, 64}) result.add(new Variant("range" + range, Map.of("ShadowRange", (float) range), true));
            }
            case "budget" -> {
                for (int budget : new int[]{64, 128, 256, 512}) result.add(new Variant("budget" + budget,
                        Map.of("CaptureRayBudget", (float) budget, "CapturePixelBudget", (float) budget), true));
            }
            case "thickness" -> {
                float full = frame.width + frame.height + 2;
                for (int cm : new int[]{2, 5, 10, 25}) result.add(new Variant("thickness" + cm + "cm",
                        Map.of("CaptureThickness", cm / 100f, "CaptureTraversal", 2f, "CaptureRayBudget", full, "CapturePixelBudget", full * frame.lights), true));
            }
            case "captured" -> {
                for (var element : frame.metadata.getAsJsonArray("comparisons")) {
                    JsonObject saved = element.getAsJsonObject();
                    Map<String, Float> values = new LinkedHashMap<>();
                    if (saved.has("uniforms")) saved.getAsJsonObject("uniforms").entrySet().forEach(e -> values.put(e.getKey(), e.getValue().getAsFloat()));
                    else {
                        String[][] keys = {{"ray_budget_override", "CaptureRayBudget"}, {"pixel_budget_override", "CapturePixelBudget"},
                                {"whole_path_samples", "CaptureSamples"}, {"thickness_override_m", "CaptureThickness"},
                                {"end_exclusion_override_m", "CaptureEndExclusion"}, {"shadow_range_m", "ShadowRange"}};
                        for (var key : keys) if (saved.has(key[0])) values.put(key[1], saved.get(key[0]).getAsFloat());
                        String[][] flags = {{"legacy_seven_point_average", "CaptureLegacy"}, {"normal_space_thickness", "CaptureNormalThickness"}, {"occlusion_disabled", "CaptureOcclusionOff"}};
                        for (var key : flags) if (saved.has(key[0])) values.put(key[1], saved.get(key[0]).getAsBoolean() ? 1f : 0f);
                        if (saved.has("only_light_slot")) values.put("CaptureOnlyLight", saved.get("only_light_slot").getAsFloat() + 1);
                        if (saved.has("traversal")) values.put("CaptureTraversal", switch (saved.get("traversal").getAsString()) {
                            case "hierarchy" -> 0f; case "major_axis_dda" -> 1f; case "pixel_grid" -> 2f;
                            default -> throw new IllegalArgumentException("Unknown captured traversal");
                        });
                    }
                    String name = saved.get("name").getAsString();
                    result.add(new Variant(name, values, Files.exists(frame.directory.resolve(name + "_reasons.png"))));
                }
            }
            default -> throw new IllegalArgumentException("Unknown preset group: " + group);
        }
        if (options.containsKey("variants")) {
            for (var element : JsonParser.parseString(Files.readString(Path.of(options.get("variants")))).getAsJsonArray()) {
                JsonObject item = element.getAsJsonObject();
                Map<String, Float> values = new LinkedHashMap<>();
                item.getAsJsonObject("uniforms").entrySet().forEach(e -> values.put(e.getKey(), e.getValue().getAsFloat()));
                result.add(new Variant(item.get("name").getAsString(), values, item.has("reasons") && item.get("reasons").getAsBoolean()));
            }
        }
        Set<String> names = new HashSet<>();
        if (result.isEmpty()) throw new IllegalArgumentException("No variants selected");
        for (Variant variant : result) if (!names.add(variant.name)) throw new IllegalArgumentException("Duplicate variant name: " + variant.name);
        return result;
    }

    private record Frame(Path directory, JsonObject metadata, int version, int width, int height, int lights,
                         float range, BufferedImage color, ByteBuffer depth, boolean hashesVerified) {
        static Frame read(Path path) throws IOException {
            path = path.toAbsolutePath().normalize();
            JsonObject m = JsonParser.parseString(Files.readString(path.resolve("frame.json"))).getAsJsonObject();
            int version = m.has("capture_format_version") ? m.get("capture_format_version").getAsInt() : 0;
            if (version < 0 || version > 1) throw new IllegalArgumentException("Unsupported capture_format_version: " + version);
            if (version == 1 && (!m.get("effect").getAsString().equals("machine_max:screen_space_light")
                    || !m.get("depth_encoding").getAsString().equals("float32-be-bottom-up")))
                throw new IllegalArgumentException("Unsupported capture effect/depth encoding");
            if (m.has("gl_state_restored") && !m.get("gl_state_restored").getAsBoolean()) throw new IllegalArgumentException("Capture reported failed GL state restoration");
            int w = m.get("width").getAsInt(), h = m.get("height").getAsInt(), lights = m.get("light_count").getAsInt();
            if (w <= 0 || h <= 0 || lights < 0 || lights > 16) throw new IllegalArgumentException("Invalid dimensions/light_count");
            int bytes = Math.multiplyExact(Math.multiplyExact(w, h), 4);
            Path depthFile = path.resolve("depth.f32be");
            if (Files.size(depthFile) != bytes) throw new IllegalArgumentException("depth.f32be size does not match width * height * 4");
            BufferedImage color = readImage(path.resolve("00_without_added_light.png"));
            if (color.getWidth() != w || color.getHeight() != h) throw new IllegalArgumentException("Color dimensions disagree with frame.json");
            for (String key : new String[]{"focal_px", "depth_a", "depth_b", "normal_weight", "OcclusionTolRel"})
                if (!Float.isFinite(m.get(key).getAsFloat())) throw new IllegalArgumentException("Non-finite " + key);
            if (m.get("focal_px").getAsFloat() <= 0 || m.get("depth_b").getAsFloat() == 0) throw new IllegalArgumentException("Invalid projection calibration");
            for (int i = 0; i < ARRAY_NAMES.length; i++) {
                JsonArray values = m.getAsJsonArray(ARRAY_NAMES[i]);
                if (values.size() != ARRAY_LENGTHS[i]) throw new IllegalArgumentException("Wrong array length: " + ARRAY_NAMES[i]);
                for (var value : values) if (!Float.isFinite(value.getAsFloat())) throw new IllegalArgumentException("Non-finite light attribute");
            }
            boolean verified = false;
            String[][] hashes = {{"fragment_sha256", "fragment.fsh"}, {"vertex_sha256", "vertex.vsh"}, {"depth_reduce_sha256", "depth_reduce.fsh"},
                    {"color_sha256", "00_without_added_light.png"}, {"depth_sha256", "depth.f32be"}};
            for (var hash : hashes) if (m.has(hash[0])) {
                if (!sha256(Files.readAllBytes(path.resolve(hash[1]))).equals(m.get(hash[0]).getAsString())) throw new IllegalArgumentException("Capture hash mismatch: " + hash[1]);
                verified = true;
            }
            if (version == 1) for (var hash : hashes) if (!m.has(hash[0])) throw new IllegalArgumentException("Missing capture hash: " + hash[0]);
            float range = m.has("ShadowRange") ? m.get("ShadowRange").getAsFloat() : 1_000_000f;
            if (!Float.isFinite(range) || range < 0) throw new IllegalArgumentException("Invalid ShadowRange");
            return new Frame(path, m, version, w, h, lights, range, color, ByteBuffer.wrap(Files.readAllBytes(depthFile)).order(ByteOrder.BIG_ENDIAN), verified);
        }
        float scalar(String name) { return metadata.get(name).getAsFloat(); }
    }

    private static final class Gpu implements AutoCloseable {
        private long window;
        private GLFWErrorCallback errors;
        private int program, traceProgram, vao, color, depth, target, fbo;
        private ScreenSpaceDepthPyramid pyramid;
        private final Frame frame;
        private final String source, vertex;
        Gpu(Frame frame, String vertex, String source, String reducer) {
            this.frame = frame; this.source = source; this.vertex = vertex;
            try {
                errors = GLFWErrorCallback.createPrint(System.err); errors.set();
                if (!glfwInit()) throw new IllegalStateException("GLFW initialization failed; a working graphics session is required");
                glfwDefaultWindowHints(); glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
                glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
                glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
                window = glfwCreateWindow(32, 32, "Shader diagnostics", 0, 0);
                if (window == 0) throw new IllegalStateException("Cannot create an OpenGL 3.3 context");
                glfwMakeContextCurrent(window); GL.createCapabilities();
                if (frame.width > glGetInteger(GL_MAX_TEXTURE_SIZE) || frame.height > glGetInteger(GL_MAX_TEXTURE_SIZE)) throw new IllegalArgumentException("Capture exceeds GL_MAX_TEXTURE_SIZE");
                vao = glGenVertexArrays(); glBindVertexArray(vao);
                ByteBuffer pixels = BufferUtils.createByteBuffer(frame.width * frame.height * 4);
                for (int y = frame.height - 1; y >= 0; y--) for (int x = 0; x < frame.width; x++) {
                    int value = frame.color.getRGB(x, y);
                    pixels.put((byte) (value >> 16)).put((byte) (value >> 8)).put((byte) value).put((byte) (value >> 24));
                }
                pixels.flip(); color = texture(GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE, pixels, frame.width, frame.height);
                FloatBuffer floats = BufferUtils.createFloatBuffer(frame.width * frame.height); floats.put(frame.depth.asFloatBuffer()).flip();
                depth = glGenTextures(); glBindTexture(GL_TEXTURE_2D, depth); parameters();
                glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, frame.width, frame.height, 0, GL_RED, GL_FLOAT, floats);
                if (!reducer.isEmpty()) {
                    pyramid = new ScreenSpaceDepthPyramid(reducer);
                    pyramid.build(depth, frame.width, frame.height, frame.scalar("depth_a"), frame.scalar("depth_b"));
                }
                program = program(vertex, source);
                target = texture(GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE, null, frame.width, frame.height);
                fbo = glGenFramebuffers();
                glBindFramebuffer(GL_FRAMEBUFFER, fbo); glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
                if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Incomplete replay framebuffer");
            } catch (RuntimeException e) { close(); throw e; }
        }
        int uniform(String name) { return glGetUniformLocation(program, name); }
        Map<String, Float> effectiveUniforms(Variant variant) {
            Map<String, Float> values = new LinkedHashMap<>(ScreenSpaceLightDiagnosticPreset.forFrame(frame.width, frame.height, frame.lights).getFirst().uniforms(frame.range));
            values.putAll(variant.uniforms);
            values.entrySet().removeIf(e -> uniform(e.getKey()) < 0);
            return values;
        }
        void controls(int p, Variant variant) {
            glUseProgram(p);
            set(p, "FocalPx", frame.scalar("focal_px")); set(p, "DepthA", frame.scalar("depth_a")); set(p, "DepthB", frame.scalar("depth_b"));
            set(p, "NormalWeight", frame.scalar("normal_weight")); set(p, "LightCount", frame.lights); set(p, "OcclusionTolRel", frame.scalar("OcclusionTolRel"));
            glUniform2f(glGetUniformLocation(p, "OutSize"), frame.width, frame.height);
            for (String name : ARRAY_NAMES) {
                JsonArray array = frame.metadata.getAsJsonArray(name); float[] values = new float[array.size()];
                for (int i = 0; i < values.length; i++) values[i] = array.get(i).getAsFloat();
                glUniform1fv(glGetUniformLocation(p, name), values);
            }
            var defaults = ScreenSpaceLightDiagnosticPreset.forFrame(frame.width, frame.height, frame.lights).getFirst().uniforms(frame.range);
            defaults.forEach((name, value) -> set(p, name, value));
            for (var entry : variant.uniforms.entrySet()) {
                int location = glGetUniformLocation(p, entry.getKey());
                if (uniform(entry.getKey()) < 0) throw new IllegalArgumentException("Shader does not support override " + entry.getKey());
                glUniform1f(location, entry.getValue());
            }
            set(p, "HierarchyLevels", pyramid == null ? 0 : pyramid.levels());
            glUniform1i(glGetUniformLocation(p, "DiffuseSampler"), 0); glUniform1i(glGetUniformLocation(p, "MCDepthSampler"), 1);
            glUniform1i(glGetUniformLocation(p, "DepthHierarchySampler"), 2);
            glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, color);
            glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, depth);
            glActiveTexture(GL_TEXTURE2); glBindTexture(GL_TEXTURE_2D, pyramid == null ? 0 : pyramid.texture());
        }
        BufferedImage render(Variant variant, boolean reasons) {
            controls(program, variant); set(program, "CaptureOutput", reasons ? 1 : 0);
            glBindFramebuffer(GL_FRAMEBUFFER, fbo); glBindVertexArray(vao); glViewport(0, 0, frame.width, frame.height);
            for (int capability : PostProcessingGpuState.CAPABILITIES) glDisable(capability);
            glColorMask(true, true, true, true); glEnable(GL_SCISSOR_TEST);
            for (int y = 0; y < frame.height; y += 128) {
                for (int x = 0; x < frame.width; x += 128) {
                    glScissor(x, y, Math.min(128, frame.width - x), Math.min(128, frame.height - y)); glDrawArrays(GL_TRIANGLES, 0, 3);
                }
                glFinish();
            }
            glDisable(GL_SCISSOR_TEST);
            ByteBuffer bytes = BufferUtils.createByteBuffer(frame.width * frame.height * 4);
            glReadPixels(0, 0, frame.width, frame.height, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
            BufferedImage image = new BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_ARGB);
            int[] rgb = new int[frame.width * frame.height];
            for (int y = 0; y < frame.height; y++) for (int x = 0; x < frame.width; x++) {
                int i = (y * frame.width + x) * 4;
                rgb[(frame.height - 1 - y) * frame.width + x] = ((bytes.get(i + 3) & 255) << 24) | ((bytes.get(i) & 255) << 16) | ((bytes.get(i + 1) & 255) << 8) | (bytes.get(i + 2) & 255);
            }
            image.setRGB(0, 0, frame.width, frame.height, rgb, 0, frame.width); return image;
        }
        JsonObject pixel(Variant variant, int x, int y, int light) {
            JsonObject result = new JsonObject();
            result.addProperty("x", x); result.addProperty("y", y); result.addProperty("light_slot", light);
            result.addProperty("budget_scope", "independent single light; not the normal shared-light competition");
            if (variant.uniforms.getOrDefault("CaptureLegacy", 0f) > 0 || variant.uniforms.getOrDefault("CaptureOcclusionOff", 0f) > 0) {
                result.addProperty("trace", "not applicable: finite-depth trace is bypassed"); return result;
            }
            if (traceProgram == 0) {
                var mainPattern = Pattern.compile("(?m)^\\s*void\\s+main\\s*\\(\\s*\\)").matcher(source);
                if (!mainPattern.find() || !source.contains("int traceLightRay(")) throw new IllegalArgumentException("Pixel inspection requires traceLightRay");
                int main = mainPattern.start();
                String weight = source.contains("float surfaceShadowWeight(") ? "surfaceShadowWeight(d)" : "1.0";
                traceProgram = program(vertex, source.substring(0, main) + """
                        uniform vec2 DiagnosticPixel;
                        uniform float DiagnosticLight;
                        uniform float DiagnosticMode;
                        void main() {
                            vec2 uv = DiagnosticPixel / OutSize;
                            float z;
                            if (!forwardDepth(uv, z)) { fragColor = DiagnosticMode > 0.5 ? vec4(0, 0, 0, -1) : vec4(2, 0, 4, 1); return; }
                            vec3 P = viewPos(uv, z), N = vec3(0, 0, 1);
                            bool hasN = viewNormal(uv, z, N);
                            int i = int(DiagnosticLight);
                            vec3 C = vec3(LightView[i*4], LightView[i*4+1], LightView[i*4+2]);
                            float d = length(C-P);
                            if (DiagnosticMode > 0.5) { fragColor = vec4(P, d); return; }
                            float weight = WEIGHT;
                            float orient = hasN && d > 0.0001 ? max(mix(1.0, dot(N, (C-P)/d), clamp(NormalWeight, 0.0, 1.0)), 0.0) : 1.0;
                            int steps = 0, reason = 0, state = RAY_CLEAR;
                            if (d < LightView[i*4+3] && orient > 0.0 && LightColor[i*4+3] > 0.0 && weight > 0.0) {
                                int available = CapturePixelBudget > 0.0 ? int(CapturePixelBudget) : OCCL_PIXEL_STEPS;
                                state = traceLightRay(P, C, d, N, hasN, available, steps, reason);
                            }
                            fragColor = vec4(float(state), float(steps), float(reason), state == RAY_HIT ? 1.0-weight : 1.0);
                        }
                        """.replace("WEIGHT", weight));
            }
            int pixelTexture = texture(GL_RGBA32F, GL_RGBA, GL_FLOAT, null, 1, 1), pixelFbo = glGenFramebuffers();
            try {
                glBindFramebuffer(GL_FRAMEBUFFER, pixelFbo); glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, pixelTexture, 0);
                controls(traceProgram, variant); glViewport(0, 0, 1, 1); glDisable(GL_SCISSOR_TEST); glBindVertexArray(vao);
                glUniform2f(glGetUniformLocation(traceProgram, "DiagnosticPixel"), x + .5f, frame.height - y - .5f);
                set(traceProgram, "DiagnosticLight", light); set(traceProgram, "DiagnosticMode", 0);
                glDrawArrays(GL_TRIANGLES, 0, 3); float[] values = new float[4]; glReadPixels(0, 0, 1, 1, GL_RGBA, GL_FLOAT, values);
                result.addProperty("state", switch (Math.round(values[0])) { case 0 -> "HIT"; case 1 -> "CLEAR"; default -> "UNKNOWN"; });
                result.addProperty("queries", Math.round(values[1])); result.addProperty("reason_bits", Math.round(values[2])); result.addProperty("surface_visibility", values[3]);
                set(traceProgram, "DiagnosticMode", 1); glDrawArrays(GL_TRIANGLES, 0, 3); glReadPixels(0, 0, 1, 1, GL_RGBA, GL_FLOAT, values);
                result.addProperty("has_surface", values[3] >= 0);
                if (values[3] >= 0) result.add("view_position_xyz_and_light_distance", JSON.toJsonTree(values));
                return result;
            } finally { glBindFramebuffer(GL_FRAMEBUFFER, fbo); glDeleteFramebuffers(pixelFbo); glDeleteTextures(pixelTexture); }
        }
        @Override public void close() {
            if (window != 0) {
                if (vao != 0) glUseProgram(0);
                if (program != 0) glDeleteProgram(program);
                if (traceProgram != 0) glDeleteProgram(traceProgram);
                if (pyramid != null) pyramid.close();
                if (vao != 0) glDeleteVertexArrays(vao);
                if (fbo != 0) glDeleteFramebuffers(fbo);
                for (int texture : new int[]{color, depth, target}) if (texture != 0) glDeleteTextures(texture);
                glfwDestroyWindow(window); window = 0;
            }
            glfwTerminate(); glfwSetErrorCallback(null); if (errors != null) errors.free();
        }
    }

    private static int texture(int internal, int format, int type, ByteBuffer data, int w, int h) {
        int id = glGenTextures(); glBindTexture(GL_TEXTURE_2D, id); parameters();
        glTexImage2D(GL_TEXTURE_2D, 0, internal, w, h, 0, format, type, data); return id;
    }
    private static void parameters() {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }
    private static void set(int p, String name, float value) { glUniform1f(glGetUniformLocation(p, name), value); }
    private static int program(String vertex, String fragment) {
        int v = shader(GL_VERTEX_SHADER, vertex), f = 0, p = 0;
        try {
            f = shader(GL_FRAGMENT_SHADER, fragment); p = glCreateProgram(); glAttachShader(p, v); glAttachShader(p, f);
            glBindFragDataLocation(p, 0, "fragColor"); glLinkProgram(p);
            if (glGetProgrami(p, GL_LINK_STATUS) == GL_FALSE) throw new IllegalArgumentException(glGetProgramInfoLog(p));
            return p;
        } catch (RuntimeException e) { if (p != 0) glDeleteProgram(p); throw e; }
        finally { glDeleteShader(v); if (f != 0) glDeleteShader(f); }
    }
    private static int shader(int type, String source) {
        int shader = glCreateShader(type); glShaderSource(shader, source); glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) { String log = glGetShaderInfoLog(shader); glDeleteShader(shader); throw new IllegalArgumentException(log); }
        return shader;
    }
    private static String diagnosticSource(String source) {
        if (!source.startsWith("#version") || !source.contains("\n")) throw new IllegalArgumentException("Shader needs a GLSL version line");
        if (Pattern.compile("(?m)^\\s*#define\\s+MM_LIGHT_CAPTURE\\b").matcher(source).find()) return source;
        int end = source.indexOf('\n') + 1; return source.substring(0, end) + "#define MM_LIGHT_CAPTURE 1\n" + source.substring(end);
    }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static BufferedImage readImage(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IllegalArgumentException("Not a supported image: " + path);
        return image;
    }
    private static void writeImage(BufferedImage image, Path path) throws IOException { ImageIO.write(image, "PNG", path.toFile()); }
    private static void sameSize(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) throw new IllegalArgumentException("Image dimensions differ");
    }
    private static void createOutput(Path output) throws IOException { Files.createDirectories(output.getParent()); Files.createDirectory(output); }
    private static JsonObject compare(BufferedImage baseline, BufferedImage image, int threshold, Path heatmap) throws IOException {
        sameSize(baseline, image); long changed = 0, exact = 0, sum = 0; int max = 0;
        BufferedImage heat = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int a = baseline.getRGB(x, y), b = image.getRGB(x, y), d = 0;
            for (int shift = 0; shift <= 16; shift += 8) { int channel = Math.abs(((a >> shift) & 255) - ((b >> shift) & 255)); sum += channel; d = Math.max(d, channel); }
            if (d > threshold) changed++; if (d != 0) exact++; max = Math.max(max, d);
            int value = Math.min(255, d * 4); heat.setRGB(x, y, (value << 16) | (Math.min(255, d) << 8));
        }
        writeImage(heat, heatmap); JsonObject result = new JsonObject();
        result.addProperty("threshold_rgb8", threshold); result.addProperty("pixels", (long) image.getWidth() * image.getHeight());
        result.addProperty("changed_pixels", changed); result.addProperty("nonidentical_pixels", exact); result.addProperty("max_channel_difference", max);
        result.addProperty("mean_absolute_channel_difference", sum / (3.0 * image.getWidth() * image.getHeight())); result.addProperty("exact_rgb_match", exact == 0);
        return result;
    }
    private static JsonObject reasonCounts(BufferedImage image) {
        Map<Integer, Long> counts = new TreeMap<>();
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) counts.merge(image.getRGB(x, y) & 0xFFFFFF, 1L, Long::sum);
        Map<Integer, String> names = Map.of(0xFF7F00, "budget", 0xFF8000, "budget", 0xFF00FF, "clipped", 0x0000FF, "missing_depth",
                0x00FFFF, "sampled_gaps", 0xFF0000, "hit", 0x00FF00, "clear", 0x141414, "not_evaluated");
        Map<String, Long> named = new TreeMap<>();
        counts.forEach((rgb, count) -> named.merge(names.getOrDefault(rgb, String.format("rgb_%06x", rgb)), count, Long::sum));
        JsonObject result = new JsonObject(); named.forEach(result::addProperty); return result;
    }
    private static JsonObject result(String name, String image, JsonObject comparison) {
        JsonObject item = new JsonObject(); item.addProperty("name", name); item.addProperty("image", image);
        if (comparison != null) item.add("comparison", comparison); return item;
    }
    private static void finish(Path output, JsonObject report, BufferedImage baseline, JsonArray results) throws IOException {
        Files.writeString(output.resolve("report.json"), JSON.toJson(report));
        StringBuilder csv = new StringBuilder("name,changed_pixels,nonidentical_pixels,max_channel_difference,mean_absolute_channel_difference\n");
        int columns = 3, thumbW = 400, thumbH = 230, count = results.size() + (baseline == null ? 0 : 1);
        BufferedImage sheet = new BufferedImage(columns * thumbW, ((count + columns - 1) / columns) * (thumbH + 28), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics(); g.setColor(new Color(24, 24, 24)); g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int index = 0;
        if (baseline != null) { thumbnail(g, baseline, "reference", index++, thumbW, thumbH, columns); }
        StringBuilder table = new StringBuilder(), options = new StringBuilder();
        for (var element : results) {
            JsonObject item = element.getAsJsonObject(); String name = item.get("name").getAsString(), file = item.get("image").getAsString();
            thumbnail(g, readImage(output.resolve(file)), name, index++, thumbW, thumbH, columns);
            String changed = "n/a";
            if (item.has("comparison")) {
                JsonObject c = item.getAsJsonObject("comparison"); changed = c.get("changed_pixels").getAsString();
                csv.append(name).append(',').append(changed).append(',').append(c.get("nonidentical_pixels")).append(',')
                        .append(c.get("max_channel_difference")).append(',').append(c.get("mean_absolute_channel_difference")).append('\n');
            }
            table.append("<tr><td>").append(name).append("</td><td>").append(changed).append("</td><td><a href=\"").append(file).append("\">image</a>");
            if (item.has("comparison")) table.append(" · <a href=\"").append(name).append("_diff.png\">diff</a>");
            if (Files.exists(output.resolve(name + "_reasons.png"))) table.append(" · <a href=\"").append(name).append("_reasons.png\">reasons</a>");
            table.append("</td></tr>");
            options.append("<option value=\"").append(file).append("\">").append(name).append("</option>");
        }
        g.dispose(); writeImage(sheet, output.resolve("overview.png")); Files.writeString(output.resolve("metrics.csv"), csv);
        String reference = baseline == null ? results.get(0).getAsJsonObject().get("image").getAsString() : "reference.png";
        Files.writeString(output.resolve("index.html"), """
                <!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>Shader diagnostics</title>
                <style>body{background:#181b21;color:#e9edf4;font:16px system-ui;margin:24px}a{color:#8dc6ff}table{border-collapse:collapse}td,th{padding:7px 18px;border:1px solid #465166}select,input{margin:12px}#view{position:relative;width:100%;max-width:1600px}#view img{width:100%;display:block}#candidate{position:absolute;inset:0;clip-path:inset(0 50% 0 0)}img.overview{max-width:100%}</style>
                <h1>着色器诊断</h1><p>左侧为候选，右侧为参考。滑动分割线检查同一位置。差异不是几何正确性的证明。</p>
                <label>候选<select id="variant">{{OPTIONS}}</select></label><label>分割<input id="split" type="range" min="0" max="100" value="50"></label>
                <div id="view"><img src="{{REFERENCE}}"><img id="candidate" src="{{FIRST}}"></div>
                <p><a href="report.json">完整参数、输入来源及像素查询</a> · <a href="metrics.csv">差异CSV</a> · <a href="overview.png">对照拼图</a></p>
                <p>changed_pixels：RGB任一通道差值超过指定阈值的像素数；exact_rgb_match按全部RGB逐位比较。分块回放耗时含提交和同步，不是正式GPU性能基准。</p>
                <p>原因图：橙=预算，紫=裁剪，蓝=缺深度，青=点间未检查，红=命中，绿=完整未命中，灰=未查询。未知区域按放光处理。</p>
                <table><tr><th>候选</th><th>changed_pixels</th><th>输出</th></tr>{{ROWS}}</table>
                <h2>全组预览</h2><img class="overview" src="overview.png">
                <script>const v=document.getElementById('variant'),c=document.getElementById('candidate'),s=document.getElementById('split');v.onchange=()=>c.src=v.value;s.oninput=()=>c.style.clipPath='inset(0 '+(100-s.value)+'% 0 0)';</script></html>
                """.replace("{{OPTIONS}}", options).replace("{{REFERENCE}}", reference).replace("{{FIRST}}", results.get(0).getAsJsonObject().get("image").getAsString()).replace("{{ROWS}}", table));
        System.out.println("Report: " + output.resolve("index.html"));
    }
    private static void thumbnail(Graphics2D g, BufferedImage image, String name, int index, int w, int h, int columns) {
        int x = index % columns * w, y = index / columns * (h + 28); g.setColor(Color.WHITE); g.drawString(name, x + 8, y + 20);
        double scale = Math.min((double) w / image.getWidth(), (double) h / image.getHeight());
        g.drawImage(image, x, y + 28, (int) (image.getWidth() * scale), (int) (image.getHeight() * scale), null);
    }
}
