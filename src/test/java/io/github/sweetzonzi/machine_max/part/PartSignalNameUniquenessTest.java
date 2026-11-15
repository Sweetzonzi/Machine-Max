package io.github.sweetzonzi.machine_max.part;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sweetzonzi.machine_max.testing.ContentFiles;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内容包里「子系统 / 交互区 / 连接点」三类信号名的 Part 内唯一性。
 *
 * <p>三类名字共用一套命名空间，按名解析（信号具名目标、MoLang {@code local.*}）的作用域是单个
 * Part 实例，也就是单份变体定义。解码期 {@code VariantAttr#validateUniqueNames} 是权威校验：
 * 重名或使用保留地址（{@code local} / {@code global}）会抛 {@code PackContentException}，
 * 该部件类型在双端都不出现。运行期没有并列分支，名字解析命中即返回。</p>
 *
 * <p>本测试把同一条判据放在内容层再拦一次：作者改内容时构件期就能看到冲突的文件与冲突双方，
 * 不必启动游戏才发现某个部件类型整体消失。扫描范围是各内容包命名空间下的 {@code parts/}
 * （含 {@code parts/prop/} 一层分组），新增内容包无需改动本测试。</p>
 */
class PartSignalNameUniquenessTest {

    /** 三类名字共用一套命名空间，顺序与解码期校验一致：连接点 → 子系统 → 交互区。 */
    private static final Set<String> RESERVED_SIGNAL_NAMES = Set.of("local", "global");

    private static final List<Category> CATEGORIES = List.of(
            new Category("connectors", "连接点"),
            new Category("subsystems", "子系统"),
            new Category("interact_boxes", "交互区")
    );

    @Test
    void everyPartVariantUsesUniqueSignalNames() throws Exception {
        List<Path> parts = ContentFiles.jsonTreeInEveryNamespace("parts");
        assertFalse(parts.isEmpty(), "未找到任何内容包部件定义：" + ContentFiles.PACKS);

        List<String> violations = new ArrayList<>();
        for (Path part : parts) {
            collectViolations(part, violations);
        }

        assertTrue(violations.isEmpty(),
                "单变体内三类信号名必须唯一且不得使用保留地址（local / global）：\n  "
                        + String.join("\n  ", violations));
    }

    private static void collectViolations(Path file, List<String> violations) throws Exception {
        String label = ContentFiles.describe(file);
        JsonObject root = ContentFiles.json(file);
        JsonObject variants = root.getAsJsonObject("variants");
        if (variants == null) return;

        // 单变体简写：variants 直接就是变体对象；多变体时 keys 是变体名。
        if (variants.has("sub_parts")) {
            collectVariant(label, "default", variants, violations);
            return;
        }
        for (Map.Entry<String, JsonElement> entry : variants.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            collectVariant(label, entry.getKey(), entry.getValue().getAsJsonObject(), violations);
        }
    }

    private static void collectVariant(String file, String variantName, JsonObject variant, List<String> violations) {
        JsonObject subParts = variant.getAsJsonObject("sub_parts");
        if (subParts == null) return;

        Map<String, String> ownerByName = new LinkedHashMap<>();
        for (Category category : CATEGORIES) {
            for (Map.Entry<String, JsonElement> subPartEntry : subParts.entrySet()) {
                if (!subPartEntry.getValue().isJsonObject()) continue;
                JsonElement names = subPartEntry.getValue().getAsJsonObject().get(category.field());
                if (names == null || !names.isJsonObject()) continue;
                for (String name : names.getAsJsonObject().keySet()) {
                    Set<String> reserved = Set.of("local", "global");
                    if (RESERVED_SIGNAL_NAMES.contains(name)) {
                        violations.add(file + "：变体 " + variantName + " 的子零件 " + subPartEntry.getKey()
                                + " 把信号保留地址 " + name + " 用作" + category.label() + "名"
                                + "（保留地址只作为 signal_targets 的目标，不能当节点名）");
                        continue;
                    }
                    String owner = subPartEntry.getKey() + "/" + category.label();
                    String previous = ownerByName.putIfAbsent(name, owner);
                    if (previous != null) {
                        violations.add(file + "：变体 " + variantName + " 内名称 " + name + " 重复："
                                + previous + " 与 " + owner + " 冲突");
                    }
                }
            }
        }
    }

    private record Category(String field, String label) {
    }
}
