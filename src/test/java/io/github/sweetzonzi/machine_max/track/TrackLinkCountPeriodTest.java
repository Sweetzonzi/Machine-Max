package io.github.sweetzonzi.machine_max.track;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sweetzonzi.machine_max.testing.ContentFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模板里的履带节数必须是型号周期 K 的整数倍。
 *
 * <p>闭环要首尾相接：N 不是 K 的整数倍时，第 N − 1 节与第 0 节落在同一个槽位，公母两种铸件
 * 在同一处相撞，接缝上还会多出一个错位的节。运行期对持久化节数只告警、不改写（存档与蓝图
 * 里的节数决定耐久上限），因此这个约束要在内容进游戏前就挡住。</p>
 *
 * <p>只校验模板：蓝图只是 {@code template} + 展示信息的引用，节数来自模板。
 * 没写 {@code link_count} 的条目跳过——那种情况加载端按当前包络推导，推导本身满足周期。</p>
 */
class TrackLinkCountPeriodTest {

    @Test
    void everyTemplateTrackLinkCountIsWholePeriods() throws IOException {
        Map<String, Integer> periods = trackPeriods();
        List<String> violations = new ArrayList<>();

        for (Path template : templates()) {
            String file = ContentFiles.describe(template);
            JsonArray links = ContentFiles.json(template).getAsJsonArray("track_links");
            if (links == null) continue;
            for (JsonElement element : links) {
                JsonObject link = element.getAsJsonObject();
                String typeId = link.get("track_type").getAsString();
                int period = periods.getOrDefault(typeId, 1);
                int count = link.has("link_count") ? link.get("link_count").getAsInt() : 0;
                if (count <= 0 || period <= 1 || count % period == 0) continue;
                violations.add(file + "：track_type=" + typeId + " 的 link_count=" + count
                        + " 不是周期 " + period + " 的整数倍");
            }
        }

        assertTrue(violations.isEmpty(),
                "模板的履带节数必须是型号周期的整数倍：\n  " + String.join("\n  ", violations));
    }

    /** 型号 id → 周期 K（{@code link_bones} 长度，没有该字段的型号为 1）。 */
    private static Map<String, Integer> trackPeriods() throws IOException {
        Map<String, Integer> periods = new HashMap<>();
        for (Path namespace : namespaces()) {
            for (Path file : ContentFiles.jsonFiles(namespace.resolve("fittings"))) {
                String id = namespace.getFileName() + ":" + stripJson(file.getFileName().toString());
                periods.put(id, periodOf(file));
            }
        }
        return periods;
    }

    private static int periodOf(Path fitting) throws IOException {
        JsonArray bones = ContentFiles.json(fitting).getAsJsonArray("link_bones");
        return bones == null ? 1 : Math.max(1, bones.size());
    }

    private static List<Path> templates() throws IOException {
        List<Path> found = new ArrayList<>();
        for (Path namespace : namespaces()) {
            found.addAll(ContentFiles.jsonFiles(namespace.resolve("templates")));
        }
        return found;
    }

    /** 内容包的命名空间目录：内容 id 的前缀就是这一层目录名。 */
    private static List<Path> namespaces() throws IOException {
        List<Path> found = new ArrayList<>();
        if (!Files.isDirectory(ContentFiles.PACKS)) return found;
        try (Stream<Path> packs = Files.list(ContentFiles.PACKS)) {
            for (Path pack : packs.filter(Files::isDirectory).toList()) {
                try (Stream<Path> children = Files.list(pack)) {
                    found.addAll(children.filter(Files::isDirectory).toList());
                }
            }
        }
        return found;
    }

    private static String stripJson(String name) {
        return name.substring(0, name.length() - ".json".length());
    }
}
