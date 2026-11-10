package io.github.sweetzonzi.machine_max.part;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sweetzonzi.machine_max.testing.ContentFiles;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * <p>扫描范围是各内容包命名空间下的 {@code templates/*.json}（多数包是
 * {@code machine_max/templates}，M1A2 SEP 与 T-90A 分别是 {@code m1a2_sep/templates}、
 * {@code t90a/templates}），新增内容包无需改动本测试。</p>
 */
class TemplateIntegrityTest {

    @Test
    void everyTemplateShipsAtFullState() throws Exception {
        List<Path> templates = ContentFiles.jsonInEveryNamespace("templates");
        assertFalse(templates.isEmpty(), "未找到任何内容包模板：" + ContentFiles.PACKS);

        List<String> violations = new ArrayList<>();
        for (Path template : templates) {
            collectViolations(template, violations);
        }

        assertTrue(violations.isEmpty(),
                "模板必须是满状态的出厂快照，以下字段低于 1：\n  " + String.join("\n  ", violations));
    }

    /** 逐部件、逐子零件、逐连接点记录所有低于满值的比例字段。 */
    private static void collectViolations(Path template, List<String> violations) throws Exception {
        String file = ContentFiles.describe(template);
        JsonObject root = ContentFiles.json(template);
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
