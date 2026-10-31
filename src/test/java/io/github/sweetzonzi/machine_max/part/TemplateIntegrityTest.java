package io.github.sweetzonzi.machine_max.part;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全部内容包载具模板的满状态契约。
 *
 * <p>模板是"全新出库"的载具快照：部件耐久、组装进度、连接点完整度都必须为 1。
 * 任何低于 1 的数值都只可能来自编辑模板时误留的运行时状态，玩家读到的将是一台带暗伤的载具
 * （零件耐久与轮座等连接点会同时显示为受损）。</p>
 *
 * <p>缺失字段按 {@code RegularPartData} / {@code SubPartData} / {@code AbstractConnector}
 * 的 Codec 默认值（均为 1f）解读为满值，因此只校验显式写出的字段。</p>
 *
 * <p>扫描范围是 {@code src/main/resources/spark_modules/<内容包>/machine_max/templates/*.json}，
 * 新增内容包无需改动本测试。</p>
 */
class TemplateIntegrityTest {
    private static final Path MODULES = Path.of("src/main/resources/spark_modules");

    @Test
    void everyTemplateShipsAtFullState() throws Exception {
        List<Path> templates = templates();
        assertFalse(templates.isEmpty(), "未找到任何内容包模板：" + MODULES.toAbsolutePath());

        List<String> violations = new ArrayList<>();
        for (Path template : templates) {
            collectViolations(template, violations);
        }

        assertTrue(violations.isEmpty(),
                "模板必须是满状态的出厂快照，以下字段低于 1：\n  " + String.join("\n  ", violations));
    }

    /** 遍历 spark_modules 下每个内容包 machine_max/templates 目录里的模板文件。 */
    private static List<Path> templates() throws Exception {
        List<Path> found = new ArrayList<>();
        if (!Files.isDirectory(MODULES)) return found;
        try (Stream<Path> packs = Files.list(MODULES)) {
            for (Path pack : packs.filter(Files::isDirectory).toList()) {
                Path dir = pack.resolve("machine_max/templates");
                if (!Files.isDirectory(dir)) continue;
                try (Stream<Path> files = Files.list(dir)) {
                    files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(found::add);
                }
            }
        }
        return found;
    }

    /** 逐部件、逐子零件、逐连接点记录所有低于满值的比例字段。 */
    private static void collectViolations(Path template, List<String> violations) throws Exception {
        String file = MODULES.relativize(template).toString().replace('\\', '/');
        JsonObject root = JsonParser.parseString(Files.readString(template)).getAsJsonObject();
        JsonObject parts = root.getAsJsonObject("parts");
        if (parts == null) {
            violations.add(file + "：缺少 parts");
            return;
        }

        for (Map.Entry<String, JsonElement> entry : parts.entrySet()) {
            String uuid = entry.getKey();
            JsonObject part = entry.getValue().getAsJsonObject();
            check(violations, file, uuid, "shared_durability_ratio", part, "shared_durability_ratio");
            check(violations, file, uuid, "assembling_progress", part, "assembling_progress");

            JsonObject subParts = part.getAsJsonObject("sub_parts");
            if (subParts == null) {
                violations.add(file + " / " + uuid + "：缺少 sub_parts");
                continue;
            }
            for (Map.Entry<String, JsonElement> subEntry : subParts.entrySet()) {
                String where = uuid + "/" + subEntry.getKey();
                JsonObject subPart = subEntry.getValue().getAsJsonObject();
                check(violations, file, where, "durability_ratio", subPart, "durability_ratio");

                JsonObject connectors = subPart.getAsJsonObject("connector_data");
                if (connectors == null) continue;
                for (Map.Entry<String, JsonElement> connector : connectors.entrySet()) {
                    check(violations, file, where + " -> " + connector.getKey(), "integrity_ratio",
                            connector.getValue().getAsJsonObject(), "integrity_ratio");
                }
            }
        }
    }

    /** 比例字段缺失视为 Codec 默认满值，只对显式写出的值做断言。 */
    private static void check(List<String> violations, String file, String where,
                              String label, JsonObject owner, String key) {
        if (!owner.has(key) || owner.get(key).isJsonNull()) return;
        float value = owner.get(key).getAsFloat();
        if (value < 1f) violations.add(file + " / " + where + "：" + label + " = " + value);
    }
}
