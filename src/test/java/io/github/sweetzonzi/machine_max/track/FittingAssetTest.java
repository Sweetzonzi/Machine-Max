package io.github.sweetzonzi.machine_max.track;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 内容包 {@code fittings/} 的静态资源自检：型号引用的模型、贴图、图标必须真的存在。
 *
 * <p>型号的 {@code visual.*} 是 ResourceLocation，写错命名空间或路径时只有进游戏才会在
 * 渲染期回退，日志一条 warn 很容易漏掉。这里在单元测试里按内容包目录布局反查文件：
 * 模型在 {@code models/fitting/<型号名>.json}，贴图与图标是命名空间目录下的相对路径。</p>
 *
 * <p>图标只额外要求「可解码 + 方形」：{@code item_icon_2d_128x} 是一个正方形面、
 * UV 铺满整张贴图，非方形会被拉伸，属于可判定的显示错误。边长不设限，128 只是推荐值；
 * 也不要求 2 的幂——图标是独立贴图、不进图集，仓库里已有非 2 次幂的零件图标在用。</p>
 */
class FittingAssetTest {
    /** 仓库内的内容包根目录；Gradle 的测试工作目录是项目根。 */
    private static final Path PACKS = Path.of("src", "main", "resources", "spark_modules");

    @Test
    void everyFittingVisualPointsAtExistingAssets() throws IOException {
        List<Path> fittings = new ArrayList<>();
        try (Stream<Path> packs = Files.list(PACKS)) {
            for (Path pack : packs.toList()) {
                try (Stream<Path> namespaces = Files.list(pack)) {
                    for (Path namespace : namespaces.toList()) {
                        Path dir = namespace.resolve("fittings");
                        if (!Files.isDirectory(dir)) continue;
                        try (Stream<Path> files = Files.list(dir)) {
                            files.filter(p -> p.toString().endsWith(".json")).forEach(fittings::add);
                        }
                    }
                }
            }
        }
        assertTrue(!fittings.isEmpty(), "没有找到任何 fittings/*.json");

        for (Path file : fittings) {
            JsonObject visual = readVisual(file);
            String model = visual.get("model").getAsString();
            String texture = visual.get("texture").getAsString();

            // 模型 id 保留 .geo 后缀，文件是 <型号名>.json
            assertTrue(resolveModel(model).isPresent(),
                    () -> file + " 的 visual.model " + model + " 找不到对应的 models/fitting/*.json");

            assertTrue(resolveResource(texture).isPresent(),
                    () -> file + " 的 visual.texture " + texture + " 在内容包里不存在");

            if (visual.has("icon")) {
                String icon = visual.get("icon").getAsString();
                Path path = resolveResource(icon).orElseGet(() -> {
                    fail(file + " 的 visual.icon " + icon + " 在内容包里不存在");
                    return null;
                });
                BufferedImage image = ImageIO.read(path.toFile());
                assertTrue(image != null, () -> file + " 的图标无法解码: " + path);
                assertTrue(image.getWidth() == image.getHeight(),
                        () -> file + " 的图标不是方形，铺到正方形底板上会被拉伸: " + path
                                + "（" + image.getWidth() + "×" + image.getHeight() + "）");
            }
        }
    }

    private static JsonObject readVisual(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return root.getAsJsonObject("visual");
        }
    }

    /** {@code machine_max:2s38_track.geo} → 任一内容包的 {@code <ns>/models/fitting/2s38_track.geo.json}。 */
    private static java.util.Optional<Path> resolveModel(String id) throws IOException {
        String[] parts = id.split(":", 2);
        if (parts.length != 2) return java.util.Optional.empty();
        String relative = "models/fitting/" + parts[1] + ".json";
        return resolve(parts[0], relative);
    }

    /** {@code machine_max:textures/part/x.png} → 任一内容包的 {@code <ns>/textures/part/x.png}。 */
    private static java.util.Optional<Path> resolveResource(String id) throws IOException {
        String[] parts = id.split(":", 2);
        if (parts.length != 2) return java.util.Optional.empty();
        return resolve(parts[0], parts[1]);
    }

    private static java.util.Optional<Path> resolve(String namespace, String relative) throws IOException {
        try (Stream<Path> packs = Files.list(PACKS)) {
            return packs
                    .map(pack -> pack.resolve(namespace).resolve(relative))
                    .filter(Files::isRegularFile)
                    .findFirst();
        }
    }
}
