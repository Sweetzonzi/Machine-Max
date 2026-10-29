package io.github.sweetzonzi.machine_max.client.render.gui.creative;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.registry.MMTags;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第一方内容包的 series 标签与系列译名。
 *
 * <p>递归盘点 {@code src/main/resources/spark_modules/**&#47;parts/**&#47;*.json}：64 份定义之外的 6 份
 * （Builtin 的五个独立物件与 MTLB 的通用 PKT 机枪塔）不写系列，其余每份的每个变体都要带本系列的
 * {@code machine_max:series/&lt;系列&gt;} 标签。车族只由该标签表达，零件上不写裸车族标签
 * （{@code machine_max:&lt;系列&gt;}）；裸名保留为研发组译名键，供研发树按组 id 显示组名。系列译名取自各包
 * {@code machine_max/lang} 的 {@code machine_max.series.<系列>}，值必须与本包已有的研究组译名
 * {@code machine_max.<系列>} 一致。</p>
 *
 * <p>用例只读资源文件，不启动 Minecraft 注册表与语言服务。</p>
 */
class CreativeContentTagsTest {

    private static final Path PARTS_ROOT = Path.of("src/main/resources/spark_modules");
    /** 完整系列标签前缀 {@code machine_max:series/}，命名空间取模组 id，路径前缀取 MMTags。 */
    private static final String SERIES_PREFIX = MachineMax.MOD_ID + ":" + MMTags.PART_SERIES_PREFIX;

    /** 明确不写系列的零件文件，路径相对仓库根。 */
    private static final Set<String> WITHOUT_SERIES = Set.of(
            "src/main/resources/spark_modules/Machine-Max_Builtin/machine_max/parts/prop/container_16x16x32.json",
            "src/main/resources/spark_modules/Machine-Max_Builtin/machine_max/parts/prop/container_16x16x8.json",
            "src/main/resources/spark_modules/Machine-Max_Builtin/machine_max/parts/prop/container_16x32x32.json",
            "src/main/resources/spark_modules/Machine-Max_Builtin/machine_max/parts/prop/golden_closestool.json",
            "src/main/resources/spark_modules/Machine-Max_Builtin/machine_max/parts/prop/reinforced_container_16x16x8.json",
            "src/main/resources/spark_modules/Machine-Max_MTLB/machine_max/parts/pkt_turret.json");

    /** 每个有待标型号的包及其系列后缀。 */
    private static final Map<String, List<String>> SERIES_BY_PACK = new LinkedHashMap<>();

    static {
        SERIES_BY_PACK.put("Machine-Max_2S38", List.of("2s38"));
        SERIES_BY_PACK.put("Machine-Max_Civilian", List.of(
                "ae86", "dayun", "jeep", "kluo", "mini_ev", "r700",
                "senna_gtr", "szn", "van", "vn", "wine_fox"));
        SERIES_BY_PACK.put("Machine-Max_K17", List.of("k17"));
        SERIES_BY_PACK.put("Machine-Max_MTLB", List.of("mtlb"));
        SERIES_BY_PACK.put("Machine-Max_Sdkfz234", List.of("sdkfz234"));
    }

    @Test
    void everyTaggedVariantCarriesItsSeriesTag() throws IOException {
        int definitionCount = 0;
        int variantCount = 0;
        int taggedDefinitions = 0;
        int taggedVariants = 0;
        Set<String> series = new TreeSet<>();
        List<String> problems = new ArrayList<>();

        for (Path file : partFiles()) {
            definitionCount++;
            String relative = file.toString().replace('\\', '/');
            String expected = expectedSeries(relative);
            List<List<String>> variants = variantTagLists(readJson(file));
            variantCount += variants.size();
            if (expected == null) {
                for (List<String> tags : variants) {
                    for (String tag : tags) {
                        if (tag.startsWith(SERIES_PREFIX)) {
                            problems.add(relative + " should not carry " + tag);
                        }
                    }
                }
                continue;
            }
            taggedDefinitions++;
            taggedVariants += variants.size();
            series.add(SERIES_PREFIX + expected);
            for (List<String> tags : variants) {
                if (!tags.contains(SERIES_PREFIX + expected)) {
                    problems.add(relative + " variant misses " + SERIES_PREFIX + expected);
                }
            }
        }

        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertEquals(74, definitionCount, "零件定义份数");
        assertEquals(112, variantCount, "变体总数");
        assertEquals(68, taggedDefinitions, "待标定义份数");
        assertEquals(106, taggedVariants, "待标变体数");
        assertEquals(15, series.size(), "系列数：" + series);
    }

    @Test
    void seriesTranslationsCopyTheExistingResearchGroupNames() throws IOException {
        int entries = 0;
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : SERIES_BY_PACK.entrySet()) {
            for (String language : List.of("zh_cn", "en_us")) {
                Path path = PARTS_ROOT.resolve(entry.getKey())
                        .resolve("machine_max").resolve("lang").resolve(language + ".json");
                JsonObject lang = readJson(path).getAsJsonObject();
                for (String suffix : entry.getValue()) {
                    String groupKey = "machine_max." + suffix;
                    String seriesKey = "machine_max.series." + suffix;
                    assertTrue(lang.has(groupKey), path + " misses " + groupKey);
                    if (!lang.has(seriesKey)) {
                        problems.add(path + " misses " + seriesKey);
                        continue;
                    }
                    if (!lang.get(groupKey).getAsString().equals(lang.get(seriesKey).getAsString())) {
                        problems.add(path + ": " + seriesKey + " != " + groupKey);
                    }
                    entries++;
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertEquals(30, entries, "系列译名条数");
    }

    @Test
    void noVariantCarriesTheBareVehicleTag() throws IOException {
        Set<String> bare = new LinkedHashSet<>();
        for (List<String> suffixes : SERIES_BY_PACK.values()) {
            for (String suffix : suffixes) bare.add(MachineMax.MOD_ID + ":" + suffix);
        }
        assertEquals(15, bare.size(), "车族裸名数量");

        List<String> problems = new ArrayList<>();
        for (Path file : partFiles()) {
            for (List<String> tags : variantTagLists(readJson(file))) {
                for (String tag : tags) {
                    if (bare.contains(tag)) problems.add(file + " still carries " + tag);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyContentSeriesTagIsRecognisedAsASeries() throws IOException {
        Set<String> series = new LinkedHashSet<>();
        for (Path file : partFiles()) {
            String expected = expectedSeries(file.toString().replace('\\', '/'));
            if (expected != null) series.add(SERIES_PREFIX + expected);
        }
        assertEquals(15, series.size());
        for (String tag : series) {
            assertTrue(CreativePartBrowser.isSeriesTag(
                    net.minecraft.resources.ResourceLocation.parse(tag)), tag);
        }
        assertFalse(CreativePartBrowser.isSeriesTag(MMTags.PART_CATEGORY_STRUCTURAL));
    }

    // ==================== 资源读取 ====================

    /** 递归收集 parts 目录下的全部零件定义。 */
    private static List<Path> partFiles() throws IOException {
        try (Stream<Path> stream = Files.walk(PARTS_ROOT)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> path.toString().replace('\\', '/').contains("/machine_max/parts/"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * 与第 8.2 节清单一致的归属规则：不标系列的文件返回 {@code null}；
     * {@code parts/<系列目录>/} 下的取目录名，{@code parts/} 根下取文件名第一个下划线之前的部分。
     */
    private static String expectedSeries(String relative) {
        if (WITHOUT_SERIES.contains(relative)) return null;
        String after = relative.split("/machine_max/parts/", 2)[1];
        if (after.contains("/")) return after.substring(0, after.indexOf('/'));
        String stem = after.substring(0, after.length() - ".json".length());
        int underscore = stem.indexOf('_');
        return underscore < 0 ? stem : stem.substring(0, underscore);
    }

    /** 读入允许注释的 JSON；内容包的零件定义带 {@code //} 注释。 */
    private static JsonElement readJson(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setLenient(true);
        return JsonParser.parseReader(reader);
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
