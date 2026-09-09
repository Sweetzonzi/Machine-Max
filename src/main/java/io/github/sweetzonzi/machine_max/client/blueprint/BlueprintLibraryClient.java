package io.github.sweetzonzi.machine_max.client.blueprint;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintProblem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 客户端蓝图库：扫描 / 解析 {@code <gamedir>/saved_blueprints/*.json}。
 *
 * <p>客户端目录是<b>唯一权威库</b>，服务端只对上传数据做校验。不做常驻缓存与重载机制：
 * 打开菜单时现场扫描，主线程限流（每 tick 至多解析 1 个文件），会话内按
 * {@code fileName + lastModified} 记忆化，避免反复开关菜单重复解析。</p>
 *
 * <p>选择主线程限流而非后台线程，是为了让 {@code validate} 与内容包数据处于同一线程——
 * {@code MMDynamicRes} 的配方表是普通 {@code HashMap}，数据包重载会先 clear 再重填。</p>
 */
public final class BlueprintLibraryClient {
    /** 蓝图库目录名（位于游戏目录下） */
    public static final String DIRECTORY = "saved_blueprints";

    /**
     * 有效条目。
     *
     * @param fileName 文件名（随机 UUID + .json）
     * @param payload  载具数据；展示所需的 name / meta 从中读取，mass / partCount 现场统计
     */
    public record BlueprintLibraryEntry(String fileName, VehicleData payload) {
    }

    /**
     * 错误条目。
     *
     * @param fileName 文件名
     * @param reason   失败原因
     * @param detail   细节（异常消息或缺失零件列表）
     */
    public record BlueprintLibraryError(String fileName, Reason reason, String detail) {
    }

    /** 错误原因 */
    public enum Reason {
        /** JSON 解析失败 */
        PARSE_ERROR,
        /** 校验未通过（零件 / 变体缺失等） */
        MISSING_PARTS
    }

    /** 记忆化条目：entry 与 error 恰有一个非空 */
    private record CachedResult(@Nullable BlueprintLibraryEntry entry, @Nullable BlueprintLibraryError error) {
    }

    private static final List<BlueprintLibraryEntry> valid = new ArrayList<>();
    private static final List<BlueprintLibraryError> broken = new ArrayList<>();
    private static final Deque<File> pending = new ArrayDeque<>();
    /** 会话内记忆化：fileName + lastModified → 解析结果 */
    private static final Map<String, CachedResult> cache = new HashMap<>();

    private BlueprintLibraryClient() {
    }

    /** 校验通过的有效条目（按展示名排序） */
    public static List<BlueprintLibraryEntry> getValid() {
        return valid.stream()
                .sorted(Comparator.comparing(entry -> entry.payload().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** 解析或校验失败的条目，统一追加在列表末尾 */
    public static List<BlueprintLibraryError> getBroken() {
        return List.copyOf(broken);
    }

    /** 是否仍在逐 tick 解析中 */
    public static boolean isScanning() {
        return !pending.isEmpty();
    }

    /**
     * 重新列举目录并启动扫描。
     *
     * <p>先 {@code listFiles()} 列出目录（微秒级），记忆化命中的立即入列，其余进入解析队列。</p>
     *
     * @param level 当前客户端世界，用于注册表校验；可为空（此时跳过 validate）
     */
    public static void rescan(@Nullable Level level) {
        valid.clear();
        broken.clear();
        pending.clear();
        File dir = directory();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            CachedResult cached = cache.get(cacheKey(file));
            if (cached == null) {
                pending.add(file);
            } else if (cached.entry() != null) {
                valid.add(cached.entry());
            } else {
                broken.add(cached.error());
            }
        }
    }

    /**
     * 每 tick 推进解析队列，至多处理 1 个文件。
     *
     * @param level 当前客户端世界，用于注册表校验；可为空
     */
    public static void tick(@Nullable Level level) {
        File file = pending.poll();
        if (file == null) return;
        parseFile(file, level);
    }

    /** 关闭菜单时停止队列（已解析结果保留在记忆中） */
    public static void stop() {
        pending.clear();
    }

    /** 蓝图库目录，必要时创建 */
    public static File directory() {
        File dir = new File(FMLPaths.GAMEDIR.get().toFile(), DIRECTORY);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * 删除本地蓝图文件。
     *
     * @param fileName 文件名
     * @return 是否删除成功
     */
    public static boolean delete(String fileName) {
        File file = new File(directory(), fileName);
        cache.remove(cacheKey(file));
        boolean deleted = file.delete();
        valid.removeIf(entry -> entry.fileName().equals(fileName));
        broken.removeIf(error -> error.fileName().equals(fileName));
        return deleted;
    }

    /**
     * 重命名蓝图文件的展示名（改写 JSON 内的 {@code name} 字段）。
     *
     * @param fileName 文件名
     * @param newName  新展示名
     * @return 是否成功
     */
    public static boolean rename(String fileName, String newName) {
        File file = new File(directory(), fileName);
        if (!file.exists()) return false;
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Optional<VehicleData> parsed = VehicleData.parseFromJson(json);
            if (parsed.isEmpty()) return false;
            String rewritten = VehicleData.serializeToJsonString(parsed.get().withNewName(newName));
            Files.writeString(file.toPath(), rewritten, StandardCharsets.UTF_8);
            // 文件内容已变，清掉记忆化让下次扫描重新解析；同时就地更新内存条目以便立即生效
            cache.remove(cacheKey(file));
            for (int i = 0; i < valid.size(); i++) {
                if (valid.get(i).fileName().equals(fileName)) {
                    valid.set(i, new BlueprintLibraryEntry(fileName, parsed.get().withNewName(newName)));
                    break;
                }
            }
            return true;
        } catch (IOException e) {
            MachineMax.LOGGER.error("重命名蓝图失败: {}", fileName, e);
            return false;
        }
    }

    /** 解析单个文件并写入结果列表 */
    private static void parseFile(File file, @Nullable Level level) {
        String fileName = file.getName();
        String key = cacheKey(file);
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Optional<VehicleData> parsed = VehicleData.parseFromJson(json);
            if (parsed.isEmpty()) {
                record(key, null, new BlueprintLibraryError(fileName, Reason.PARSE_ERROR, "JSON 解析失败"));
                return;
            }
            VehicleData data = parsed.get();
            if (level != null) {
                List<BlueprintProblem> problems = data.validate(level);
                if (!problems.isEmpty()) {
                    String detail = problems.stream()
                            .map(BlueprintProblem::detail)
                            .collect(Collectors.joining("; "));
                    record(key, null, new BlueprintLibraryError(fileName, Reason.MISSING_PARTS, detail));
                    return;
                }
            }
            record(key, new BlueprintLibraryEntry(fileName, data), null);
        } catch (IOException e) {
            record(key, null, new BlueprintLibraryError(fileName, Reason.PARSE_ERROR, String.valueOf(e.getMessage())));
        } catch (Exception e) {
            // 解析期的任何异常都降级为错误条目，不让异常逃逸到渲染层
            MachineMax.LOGGER.warn("读取蓝图文件失败: {}", fileName, e);
            record(key, null, new BlueprintLibraryError(fileName, Reason.PARSE_ERROR, String.valueOf(e.getMessage())));
        }
    }

    private static void record(String key, @Nullable BlueprintLibraryEntry entry, @Nullable BlueprintLibraryError error) {
        cache.put(key, new CachedResult(entry, error));
        if (entry != null) {
            valid.add(entry);
        } else if (error != null) {
            broken.add(error);
        }
    }

    private static String cacheKey(File file) {
        return file.getName() + "@" + file.lastModified();
    }
}
