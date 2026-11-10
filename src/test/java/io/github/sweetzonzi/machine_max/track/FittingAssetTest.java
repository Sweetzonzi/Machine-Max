package io.github.sweetzonzi.machine_max.track;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sweetzonzi.machine_max.testing.ContentFiles;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 内容包 {@code fittings/} 的静态资源自检：型号引用的模型、贴图、图标必须真的存在，
 * 逐节骨骼名必须能在模型里落到实处。
 *
 * <p>型号的 {@code visual.*} 是 ResourceLocation，写错命名空间或路径时只有进游戏才会在
 * 渲染期回退，日志一条 warn 很容易漏掉。这里在单元测试里按内容包目录布局反查文件：
 * 模型在 {@code models/fitting/<型号名>.json}，贴图与图标是命名空间目录下的相对路径。</p>
 *
 * <p>{@code link_bones} 的失败同样是运行期才可见的：名字写错、骨骼没有几何、重名或超过
 * 上限时，游戏里只会看到整条履带退回程序化的扁长方体，日志一条 warn 埋在一堆加载信息里。
 * 模型放进游戏前这里先按 Blockbench 导出的骨骼表核一遍。骨骼表只取第一个
 * {@code minecraft:geometry}，与加载器一致。</p>
 *
 * <p>图标只额外要求「可解码 + 方形」：{@code item_icon_2d_128x} 是一个正方形面、
 * UV 铺满整张贴图，非方形会被拉伸，属于可判定的显示错误。边长不设限，128 只是推荐值；
 * 也不要求 2 的幂——图标是独立贴图、不进图集，仓库里已有非 2 次幂的零件图标在用。</p>
 */
class FittingAssetTest {
    /** 最小重复单元的节数上限，与 fitting schema 的 {@code maxItems} 一致。 */
    private static final int MAX_LINK_PERIOD = 8;

    @Test
    void everyFittingVisualPointsAtExistingAssets() throws IOException {
        List<Path> fittings = ContentFiles.jsonInEveryNamespace("fittings");
        assertTrue(!fittings.isEmpty(), "没有找到任何 fittings/*.json");

        for (Path file : fittings) {
            JsonObject visual = ContentFiles.json(file).getAsJsonObject("visual");
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

    @Test
    void everyTrackLinkBonesMatchesItsModel() throws IOException {
        for (Path file : ContentFiles.jsonInEveryNamespace("fittings")) {
            JsonObject root = ContentFiles.json(file);
            JsonArray declared = root.getAsJsonArray("link_bones");
            if (declared == null || declared.isEmpty()) continue;

            String model = root.getAsJsonObject("visual").get("model").getAsString();
            Path modelFile = resolveModel(model).orElseGet(() -> {
                fail(file + " 的 visual.model " + model + " 找不到对应的 models/fitting/*.json");
                return null;
            });
            JsonObject geometry = firstGeometry(modelFile);

            List<String> names = new ArrayList<>(declared.size());
            for (JsonElement element : declared) {
                names.add(element.getAsString());
            }
            assertTrue(names.size() <= MAX_LINK_PERIOD,
                    () -> file + " 的 link_bones 有 " + names.size() + " 节，超过上限 " + MAX_LINK_PERIOD);
            assertTrue(new LinkedHashSet<>(names).size() == names.size(),
                    () -> file + " 的 link_bones 有重名骨骼: " + names);

            Map<String, JsonObject> bones = bones(geometry);
            for (String name : names) {
                JsonObject bone = bones.get(name);
                assertTrue(bone != null,
                        () -> file + " 的 link_bones 里 " + name + " 不在 " + modelFile
                                + " 的第一个 minecraft:geometry 骨骼表中；现有骨骼: " + bones.keySet());
                assertTrue(hasGeometry(bone),
                        () -> file + " 的 link_bones 里 " + name + " 没有 cube 或 poly_mesh，画不出节");
            }
        }
    }

    /** 加载器只读第一个 geometry，校验也只看它。 */
    private static JsonObject firstGeometry(Path modelFile) throws IOException {
        JsonArray geometries = ContentFiles.json(modelFile).getAsJsonArray("minecraft:geometry");
        assertTrue(geometries != null && !geometries.isEmpty(),
                () -> modelFile + " 里没有 minecraft:geometry");
        return geometries.get(0).getAsJsonObject();
    }

    private static Map<String, JsonObject> bones(JsonObject geometry) {
        Map<String, JsonObject> bones = new LinkedHashMap<>();
        JsonArray array = geometry.getAsJsonArray("bones");
        if (array == null) return bones;
        for (JsonElement element : array) {
            JsonObject bone = element.getAsJsonObject();
            bones.put(bone.get("name").getAsString(), bone);
        }
        return bones;
    }

    private static boolean hasGeometry(JsonObject bone) {
        JsonArray cubes = bone.getAsJsonArray("cubes");
        if (cubes != null && !cubes.isEmpty()) return true;
        JsonElement mesh = bone.get("poly_mesh");
        return mesh != null && !mesh.isJsonNull();
    }

    /** {@code machine_max:2s38_track.geo} → 任一内容包的 {@code <ns>/models/fitting/2s38_track.geo.json}。 */
    private static Optional<Path> resolveModel(String id) throws IOException {
        String[] parts = id.split(":", 2);
        if (parts.length != 2) return Optional.empty();
        String relative = "models/fitting/" + parts[1] + ".json";
        return resolve(parts[0], relative);
    }

    /** {@code machine_max:textures/part/x.png} → 任一内容包的 {@code <ns>/textures/part/x.png}。 */
    private static Optional<Path> resolveResource(String id) throws IOException {
        String[] parts = id.split(":", 2);
        if (parts.length != 2) return Optional.empty();
        return resolve(parts[0], parts[1]);
    }

    private static Optional<Path> resolve(String namespace, String relative) throws IOException {
        try (Stream<Path> packs = Files.list(ContentFiles.PACKS)) {
            return packs
                    .map(pack -> pack.resolve(namespace).resolve(relative))
                    .filter(Files::isRegularFile)
                    .findFirst();
        }
    }
}
