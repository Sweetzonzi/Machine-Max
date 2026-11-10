package io.github.sweetzonzi.machine_max.testing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 内容包 JSON 的唯一读取入口：目录定位、JSONC 口径与目录遍历。
 *
 * <p>内容包 JSON 是 JSONC——加载器与 {@code ./gradlew checkContent} 都按去注释口径读。
 * 用例必须用同一口径，否则内容作者在文件里加一行 {@code //} 就会让用例在解析阶段抛异常，
 * 报的是解析错误而不是断言失败。</p>
 *
 * <p>{@link #PACKS} 是相对仓库根的路径；{@code build.gradle} 的 {@code test} 任务显式把
 * 工作目录钉在项目根（{@code workingDir = projectDir}），不依赖 Gradle 的默认值。</p>
 */
public final class ContentFiles {

    /** {@code src/main/resources/spark_modules}，相对仓库根。 */
    public static final Path PACKS = Path.of("src", "main", "resources", "spark_modules");

    private ContentFiles() {
    }

    /** 宽松解析一份内容包 JSON：行注释、块注释与单引号都接受。 */
    public static JsonElement parse(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setLenient(true);
        return JsonParser.parseReader(reader);
    }

    /** {@link #parse(Path)} 的对象形态。 */
    public static JsonObject json(Path file) throws IOException {
        return parse(file).getAsJsonObject();
    }

    /** 目录下直接包含的 {@code *.json}，按文件名排序；目录不存在时为空。 */
    public static List<Path> jsonFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * 每个内容包每个命名空间下的 {@code <子目录>/*.json}。
     *
     * <p>内容 id 是 {@code <命名空间>/<文件名>}，与所在包无关；命名空间目录各包不同
     * （多数是 {@code machine_max}，M1A2 SEP 与 T-90A 分别是 {@code m1a2_sep}、{@code t90a}），
     * 因此不能按 {@code machine_max} 一个名字去找。</p>
     */
    public static List<Path> jsonInEveryNamespace(String child) throws IOException {
        List<Path> found = new ArrayList<>();
        if (!Files.isDirectory(PACKS)) return found;
        try (Stream<Path> packs = Files.list(PACKS)) {
            for (Path pack : packs.filter(Files::isDirectory).toList()) {
                try (Stream<Path> namespaces = Files.list(pack)) {
                    for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
                        found.addAll(jsonFiles(namespace.resolve(child)));
                    }
                }
            }
        }
        return found;
    }

    /** 内容包根相对路径，用作失败消息里的定位。 */
    public static String describe(Path file) {
        return PACKS.relativize(file).toString().replace('\\', '/');
    }
}
