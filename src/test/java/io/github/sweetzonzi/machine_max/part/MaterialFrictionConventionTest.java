package io.github.sweetzonzi.machine_max.part;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sweetzonzi.machine_max.testing.ContentFiles;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全部内容包材质的摩擦向量约定。
 *
 * <p>{@code physics.friction} 写作 {@code [横向, 纵向, 纵向]}：第一个分量是沿轮轴的横向摩擦，
 * 第二个是滚动方向的纵向摩擦，第三个分量是第二个的副本。刚体的各向异性摩擦按部件局部坐标逐分量
 * 相乘，轮面又会绕自身轴向转动，因此只有后两个分量相等时，纵向分量的缩放才既不改变大小也不偏转
 * 方向。两者不等会让纵向摩擦随轮子转角摆动，并在 45° 附近把摩擦方向扭偏，凭空产生侧向力。</p>
 *
 * <p>这条约定无法用 JSON Schema（draft-07 不能比较两个数组元素）表达，也没有运行期的强制：
 * 只有履带材料会在装载时留下警告，轮胎与普通部件不检查。因此由本测试固定，扫描范围是各内容包
 * 命名空间下的 {@code materials/*.json}，新增内容包无需改动本测试。</p>
 */
class MaterialFrictionConventionTest {

    @Test
    void everyMaterialRepeatsLongitudinalFrictionInTheThirdComponent() throws Exception {
        List<Path> materials = ContentFiles.jsonInEveryNamespace("materials");
        assertFalse(materials.isEmpty(), "未找到任何内容包材质：" + ContentFiles.PACKS);

        List<String> violations = new ArrayList<>();
        for (Path material : materials) {
            collectViolations(material, violations);
        }

        assertTrue(violations.isEmpty(),
                "friction 必须写成 [横向, 纵向, 纵向]（第三个分量等于第二个）：\n  "
                        + String.join("\n  ", violations));
    }

    /** 缺省 friction 使用各向同性默认值，因此只校验显式写出的向量。 */
    private static void collectViolations(Path material, List<String> violations) throws Exception {
        String file = ContentFiles.describe(material);
        JsonObject root = ContentFiles.json(material);
        JsonObject physics = root.getAsJsonObject("physics");
        if (physics == null) return;
        JsonElement friction = physics.get("friction");
        if (friction == null || !friction.isJsonArray()) return;
        JsonArray vector = friction.getAsJsonArray();
        if (vector.size() != 3) {
            violations.add(file + "：friction 必须是三元组，实为 " + vector.size() + " 个分量");
            return;
        }
        double lateral = vector.get(0).getAsDouble();
        double longitudinal = vector.get(1).getAsDouble();
        double third = vector.get(2).getAsDouble();
        if (Math.abs(longitudinal - third) > 1e-9) {
            violations.add(file + "：friction = [" + lateral + ", " + longitudinal + ", " + third
                    + "]，第三个分量应为 " + longitudinal);
        }
    }
}
