package io.github.sweetzonzi.machine_max.client.render.gui.creative;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.registry.MMTags;
import io.github.sweetzonzi.machine_max.testing.ContentFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第一方内容包的系列标签与系列译名。
 *
 * <p>递归盘点 {@code src/main/resources/spark_modules/**&#47;parts/**&#47;*.json}，不限定内容命名空间。
 * 规则全部由数据自身推出，不维护包清单，也不维护免检清单：</p>
 *
 * <ol>
 *   <li>变体上的系列标签必须与路径推出的系列一致——{@code parts/<系列>/} 取目录名，
 *       {@code parts/<系列>_*.json} 取第一个下划线前的部分——且一个变体最多挂一个；</li>
 *   <li>不写系列标签是允许的：货箱、黄金马桶这类不归属车族的独立物件与通用机枪塔都属于这种；</li>
 *   <li>每个出现过的系列都要有译名 {@code machine_max.series.<系列>}；同一内容包里同时存在研发组
 *       译名 {@code machine_max.<系列>} 时，两者取值必须一致；</li>
 *   <li>内容里出现过的系列不得再以裸标签 {@code machine_max:<系列>} 挂在变体上——车族只由
 *       {@code machine_max:series/<系列>} 表达，裸名保留为研发组译名键。</li>
 * </ol>
 *
 * <p>用例只读资源文件，不启动 Minecraft 注册表与语言服务。</p>
 */
class CreativeContentTagsTest {

    /** 完整系列标签前缀 {@code machine_max:series/}，命名空间取模组 id，路径前缀取 MMTags。 */
    private static final String SERIES_PREFIX = MachineMax.MOD_ID + ":" + MMTags.PART_SERIES_PREFIX;

    @Test
    void everySeriesTagMatchesTheSeriesItsPathImplies() throws IOException {
        List<String> problems = new ArrayList<>();
        int taggedVariants = 0;

        for (Path file : partFiles()) {
            String relative = ContentFiles.describe(file);
            String expected = seriesFromPath(relative);
            for (List<String> tags : variantTagLists(ContentFiles.json(file))) {
                List<String> series = tags.stream().filter(tag -> tag.startsWith(SERIES_PREFIX)).toList();
                if (series.isEmpty()) continue;
                taggedVariants++;
                if (series.size() > 1) {
                    problems.add(relative + " 的一个变体挂了多个系列标签：" + series);
                }
                for (String tag : series) {
                    if (!tag.equals(SERIES_PREFIX + expected)) {
                        problems.add(relative + " 的系列标签 " + tag + " 与路径推出的系列 "
                                + SERIES_PREFIX + expected + " 不一致");
                    }
                }
            }
        }

        assertTrue(problems.isEmpty(), String.join("\n", problems));
        // 只守「真的扫到了内容」。份数与变体数属于内容本身，增删一份零件不该让测试变红。
        assertTrue(taggedVariants > 0, "未从内容里读到任何系列标签：" + ContentFiles.PACKS);
    }

    @Test
    void seriesTranslationsExistInEveryPackThatUsesThem() throws IOException {
        Map<String, Set<String>> packsBySeries = new TreeMap<>();
        for (Path file : partFiles()) {
            String pack = packOf(file);
            for (List<String> tags : variantTagLists(ContentFiles.json(file))) {
                for (String tag : tags) {
                    if (!tag.startsWith(SERIES_PREFIX)) continue;
                    packsBySeries
                            .computeIfAbsent(tag.substring(SERIES_PREFIX.length()), key -> new TreeSet<>())
                            .add(pack);
                }
            }
        }
        assertFalse(packsBySeries.isEmpty(), "未从内容里读到任何系列标签");

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : packsBySeries.entrySet()) {
            String seriesKey = "machine_max.series." + entry.getKey();
            String groupKey = "machine_max." + entry.getKey();
            for (String pack : entry.getValue()) {
                Path langDirectory = contentDirectory(pack).resolve("lang");
                for (String language : List.of("zh_cn", "en_us")) {
                    Path path = langDirectory.resolve(language + ".json");
                    assertTrue(Files.isRegularFile(path), pack + " 缺少 lang/" + language + ".json");
                    JsonObject lang = ContentFiles.json(path).getAsJsonObject();
                    if (!lang.has(seriesKey)) {
                        problems.add(ContentFiles.describe(path) + " 缺少 " + seriesKey);
                    } else if (lang.has(groupKey)
                            && !lang.get(groupKey).getAsString().equals(lang.get(seriesKey).getAsString())) {
                        problems.add(ContentFiles.describe(path) + ": " + seriesKey + " != " + groupKey);
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void noVariantCarriesASeriesBareTag() throws IOException {
        Set<String> bareNames = new LinkedHashSet<>();
        for (Path file : partFiles()) {
            for (List<String> tags : variantTagLists(ContentFiles.json(file))) {
                for (String tag : tags) {
                    if (tag.startsWith(SERIES_PREFIX)) {
                        bareNames.add(MachineMax.MOD_ID + ":" + tag.substring(SERIES_PREFIX.length()));
                    }
                }
            }
        }
        assertFalse(bareNames.isEmpty(), "未从内容里读出任何系列标签");

        List<String> problems = new ArrayList<>();
        for (Path file : partFiles()) {
            for (List<String> tags : variantTagLists(ContentFiles.json(file))) {
                for (String tag : tags) {
                    if (bareNames.contains(tag)) {
                        problems.add(ContentFiles.describe(file) + " 仍挂着裸车族标签 " + tag);
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyContentSeriesTagIsRecognisedAsASeries() throws IOException {
        Set<String> series = new LinkedHashSet<>();
        for (Path file : partFiles()) {
            for (List<String> tags : variantTagLists(ContentFiles.json(file))) {
                for (String tag : tags) {
                    if (tag.startsWith(SERIES_PREFIX)) series.add(tag);
                }
            }
        }
        assertFalse(series.isEmpty(), "未从内容里读出任何系列标签");
        for (String tag : series) {
            assertTrue(CreativePartBrowser.isSeriesTag(
                    net.minecraft.resources.ResourceLocation.parse(tag)), tag);
        }
        assertFalse(CreativePartBrowser.isSeriesTag(MMTags.PART_CATEGORY_STRUCTURAL));
    }

    // ==================== 资源读取 ====================

    /** 递归收集 parts 目录下的全部零件定义，不限内容命名空间。 */
    private static List<Path> partFiles() throws IOException {
        if (!Files.isDirectory(ContentFiles.PACKS)) return List.of();
        try (Stream<Path> stream = Files.walk(ContentFiles.PACKS)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> path.toString().replace('\\', '/').contains("/parts/"))
                    .sorted()
                    .toList();
        }
    }

    /** 零件文件所在的内容包目录名，例如 {@code Machine-Max_K17}。 */
    private static String packOf(Path file) {
        return ContentFiles.PACKS.relativize(file).getName(0).toString();
    }

    /**
     * 内容命名空间目录：包内直接带 {@code lang/zh_cn.json} 的那一层。
     *
     * <p>多数包用 {@code machine_max}，M1A2 SEP 与 T-90A 分别用自己的 {@code m1a2_sep}、{@code t90a}。</p>
     */
    private static Path contentDirectory(String pack) throws IOException {
        Path packDirectory = ContentFiles.PACKS.resolve(pack);
        try (Stream<Path> stream = Files.list(packDirectory)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(dir -> Files.isRegularFile(dir.resolve("lang").resolve("zh_cn.json")))
                    .findFirst()
                    .orElseThrow(() -> new IOException(pack + " 缺少 lang 目录"));
        }
    }

    /**
     * 路径推出的系列：{@code parts/<系列目录>/} 取目录名，
     * {@code parts/} 根下取文件名第一个下划线之前的部分。
     */
    private static String seriesFromPath(String relative) {
        String after = relative.split("/parts/", 2)[1];
        if (after.contains("/")) return after.substring(0, after.indexOf('/'));
        String stem = after.substring(0, after.length() - ".json".length());
        int underscore = stem.indexOf('_');
        return underscore < 0 ? stem : stem.substring(0, underscore);
    }

    /** 取一份定义里全部变体的 tags；单配置 variants 作为一份内联变体统计。 */
    private static List<List<String>> variantTagLists(JsonElement definition) {
        JsonObject variants = definition.getAsJsonObject().getAsJsonObject("variants");
        List<List<String>> result = new ArrayList<>();
        if (variants.has("model")) {
            result.add(tagsOf(variants));
            return result;
        }
        for (Map.Entry<String, JsonElement> variant : variants.entrySet()) {
            result.add(tagsOf(variant.getValue().getAsJsonObject()));
        }
        return result;
    }

    private static List<String> tagsOf(JsonObject variant) {
        if (!variant.has("tags")) return List.of();
        JsonArray tags = variant.getAsJsonArray("tags");
        List<String> result = new ArrayList<>(tags.size());
        for (JsonElement tag : tags) result.add(tag.getAsString());
        return result;
    }
}
