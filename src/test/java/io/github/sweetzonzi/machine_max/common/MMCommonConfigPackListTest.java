package io.github.sweetzonzi.machine_max.common;

import com.google.gson.JsonObject;
import io.github.sweetzonzi.machine_max.testing.ContentFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code OVERRIDABLE_PACK_IDS} 必须恰好是 {@code spark_modules} 下的叶子包。
 *
 * <p>漏一个，那个包会在每次启动时被 jar 内版本覆盖——玩家改过的内容静默丢失；多一个，
 * 依赖链下层就可能停在旧版本，上层包随之报缺定义。两类都不报错，只能靠这条对照守住：
 * {@code machine_max:builtin}、各国型谱包与空骨架在名单外（始终跟随 jar），
 * 民用包与车包在名单内。
 */
class MMCommonConfigPackListTest {

    /** 空骨架：不随内容分发，也不该被覆盖开关左右。 */
    private static final String TEMPLATE = "machine_max:template";

    private static final String BUILTIN = "machine_max:builtin";

    /** 始终跟随 jar 的包：空骨架、builtin、各国型谱包。 */
    private static boolean alwaysFollowsJar(String packId) {
        return packId.equals(TEMPLATE)
                || packId.equals(BUILTIN)
                || packId.endsWith("_basic_kit");
    }

    @Test
    void listCoversExactlyTheLeafPacks() throws IOException {
        Set<String> declared = new LinkedHashSet<>();
        try (Stream<Path> packs = Files.list(ContentFiles.PACKS)) {
            for (Path pack : packs.filter(Files::isDirectory).toList()) {
                Path meta = pack.resolve("meta.json");
                if (!Files.isRegularFile(meta)) {
                    continue;
                }
                JsonObject json = ContentFiles.json(meta);
                assertTrue(json.has("id"), meta + " 缺少 id");
                declared.add(json.get("id").getAsString());
            }
        }

        for (String packId : declared) {
            assertEquals(!alwaysFollowsJar(packId), MMCommonConfig.OVERRIDABLE_PACK_IDS.contains(packId),
                    packId + (alwaysFollowsJar(packId)
                            ? " 应始终跟随 jar，不能出现在 OVERRIDABLE_PACK_IDS"
                            : " 是叶子包，必须出现在 OVERRIDABLE_PACK_IDS"));
        }
        long leafCount = declared.stream().filter(id -> !alwaysFollowsJar(id)).count();
        assertEquals(leafCount, MMCommonConfig.OVERRIDABLE_PACK_IDS.size(),
                "OVERRIDABLE_PACK_IDS 含有 spark_modules 下不存在的包");
    }

    @Test
    void everyPackIdSitsInTheModNamespace() {
        MMCommonConfig.OVERRIDABLE_PACK_IDS.forEach(id ->
                assertTrue(id.startsWith("machine_max:"),
                        id + " 不属于 machine_max 命名空间"));
    }
}
