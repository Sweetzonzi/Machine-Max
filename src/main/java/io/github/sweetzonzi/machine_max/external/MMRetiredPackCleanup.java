package io.github.sweetzonzi.machine_max.external;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 清理内容包目录里旧版本留下的包副本。
 *
 * <p>第一方内容包的目录改过名（{@code machine_max.builtin} → {@code Machine-Max_Builtin}、
 * {@code Machine-Max_Official_Pack} → {@code Machine-Max_Civilian}、{@code sdkfz} 拆成若干包），
 * 而 {@code SparkPackResourceLoader} 用目录名给每个包生成 {@code <gamedir>/spark_modules/<目录名>.zip}，
 * 旧名对应的 zip 不会被覆盖。{@code SparkPackGraph} 又以包 id 为键、后写入者覆盖前者，
 * 于是同一 id 会同时来自旧 zip 与新 zip，实际生效的那份取决于目录遍历顺序。
 *
 * <p>这里按文件名删掉这些退役副本：只处理本模组历史版本用过的名字，不碰第三方的包；
 * 名字对不上时什么也不做。
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID)
public final class MMRetiredPackCleanup {

    /** 退役的包目录名。zip 与解压目录两种形态都会删。 */
    private static final Set<String> RETIRED_NAMES = Set.of(
            "machine_max.builtin",
            "Machine-Max_Official_Pack",
            "sdkfz"
    );

    private MMRetiredPackCleanup() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(MMRetiredPackCleanup::removeRetiredPacks);
    }

    /**
     * 主线程：删除 {@code <gamedir>/spark_modules} 下退役名字的 zip 与目录。
     */
    private static void removeRetiredPacks() {
        Path dir = FMLPaths.GAMEDIR.get().resolve("spark_modules");
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<String> removed = new ArrayList<>();
        for (String name : RETIRED_NAMES) {
            Path zip = dir.resolve(name + ".zip");
            Path folder = dir.resolve(name);
            try {
                if (Files.isRegularFile(zip)) {
                    Files.delete(zip);
                    removed.add(zip.getFileName().toString());
                }
                if (Files.isDirectory(folder)) {
                    deleteRecursively(folder);
                    removed.add(folder.getFileName().toString() + "/");
                }
            } catch (IOException e) {
                MachineMax.LOGGER.warn("无法删除退役内容包 {}: {}", name, e.toString());
            }
        }
        if (!removed.isEmpty()) {
            MachineMax.LOGGER.info("已删除退役内容包副本: {}", String.join(", ", removed));
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            List<Path> paths = stream.sorted(java.util.Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }
}
