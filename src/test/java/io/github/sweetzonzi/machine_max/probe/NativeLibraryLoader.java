package io.github.sweetzonzi.machine_max.probe;

import com.jme3.bullet.util.NativeLibrary;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * 测试与探针共用的 Bullet native 加载器。
 *
 * <p>不启动 Minecraft 的物理用例需要同一份引擎库。加载顺序：</p>
 * <ol>
 *   <li>{@code MM_NATIVE_PATH} 环境变量指定任意一份 native（用于对比不同构件）；</li>
 *   <li>本 JVM 已经加载过时直接复用（{@code NativeLibrary.versionNumber()} 能答上来）；</li>
 *   <li>Spark-Core 随依赖进入测试 classpath 的 {@code natives/bullet_dpmt|bullet_dp/...} 资源；</li>
 *   <li>复合构建下 classpath 未打包资源时，从兄弟工程的源码树兜底。</li>
 * </ol>
 */
public final class NativeLibraryLoader {

    private static boolean loaded;

    private NativeLibraryLoader() {
    }

    /** 幂等：重复调用只加载一次。native 缺失时抛 {@link IllegalStateException} 说明找过哪些路径。 */
    public static synchronized void load() {
        if (loaded) return;

        String override = System.getenv("MM_NATIVE_PATH");
        if (override != null && !override.isBlank()) {
            System.load(Path.of(override).toAbsolutePath().toString());
            loaded = true;
            return;
        }

        try {
            NativeLibrary.versionNumber();
            loaded = true;
            return;
        } catch (UnsatisfiedLinkError notYetLoaded) {
            // 独立 JUnit 进程里还没有人加载过引擎，继续按下面找库文件。
        }

        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String folder = os.contains("win") ? "windows" : os.contains("mac") ? "macos" : "linux";
        String library = os.contains("win") ? "bulletjme.dll"
                : os.contains("mac") ? "libbulletjme.dylib" : "libbulletjme.so";
        String architecture = arch.contains("aarch64") || arch.contains("arm64") ? "arm64" : "x86_64";

        for (String variant : new String[]{"bullet_dpmt", "bullet_dp"}) {
            String resource = "natives/" + variant + "/" + folder + "/" + architecture + "/" + library;
            try (InputStream input = NativeLibraryLoader.class.getClassLoader().getResourceAsStream(resource)) {
                if (input == null) continue;
                Path temporary = Files.createTempFile("bulletjme-", library.substring(library.lastIndexOf('.')));
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                temporary.toFile().deleteOnExit();
                System.load(temporary.toAbsolutePath().toString());
                loaded = true;
                return;
            } catch (IOException e) {
                throw new IllegalStateException("无法释放测试用 native 资源: " + resource, e);
            }
        }

        // 复合构建下的开发目录兜底（不依赖 classpath 打包方式）
        for (String base : new String[]{"build/resources/main", "src/main/resources"}) {
            Path fallback = Path.of("..", "Spark-Core", base,
                    "natives", "bullet_dpmt", folder, architecture, library);
            if (Files.isRegularFile(fallback)) {
                System.load(fallback.toAbsolutePath().toString());
                loaded = true;
                return;
            }
        }

        throw new IllegalStateException("找不到 bulletjme native: natives/bullet_dpmt|bullet_dp/"
                + folder + "/" + architecture + "/" + library
                + "（可用 MM_NATIVE_PATH 指定任意一份）");
    }
}
