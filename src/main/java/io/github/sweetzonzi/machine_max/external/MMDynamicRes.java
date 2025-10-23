package io.github.sweetzonzi.machine_max.external;

import com.google.gson.*;
import io.github.sweetzonzi.machine_max.common.visual.AnimatableParams;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.vehicle.data.VehicleData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static io.github.sweetzonzi.machine_max.MachineMax.LOGGER;
import static io.github.sweetzonzi.machine_max.MachineMax.MOD_ID;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class MMDynamicRes {
    public static ConcurrentMap<ResourceLocation, DynamicPack> EXTERNAL_RESOURCE = new ConcurrentHashMap<>(); //所有当下读取的外部资源
    public static ConcurrentMap<ResourceLocation, PartType> PART_TYPES = new ConcurrentHashMap<>(); // key是自带构造函数生成的registryKey， value是暂存的PartType
    //TODO:按维度区分，避免不同服务端物理线程获取到相同的对象
    public static ConcurrentMap<ResourceLocation, PartType> SERVER_PART_TYPES = new ConcurrentHashMap<>(); // key是自带构造函数生成的registryKey， value是暂存的PartType
    public static ConcurrentMap<ResourceLocation, VehicleData> BLUEPRINTS = new ConcurrentHashMap<>(); // 读取为蓝图数据，每个包可以有多个蓝图 key是自带构造函数生成的registryKey， value是暂存的VehicleData
    public static ConcurrentMap<ResourceLocation, String> BLUEPRINT_INFO = new ConcurrentHashMap<>(); //蓝图对应的描述信息
    public static ConcurrentMap<ResourceLocation, AnimatableParams> CUSTOM_HUD = new ConcurrentHashMap<>(); // 自定义HUD配置文件
    public static ConcurrentMap<ResourceLocation, JsonElement> COLORS = new ConcurrentHashMap<>(); // 读取为自定义色彩合集 key注册路径， value是该文件的JsonElement对象
    public static List<Exception> exceptions = new ArrayList<>(); // 读取过程中出现的异常
    public static List<String> errorFiles = new ArrayList<>(); // 读取过程中出现错误的文件
    public static List<MutableComponent> errorMessages = new ArrayList<>(); // 读取过程中出现错误的提示信息
    public static ConcurrentMap<ResourceLocation, DynamicPack> MM_SCRIPTS = new ConcurrentHashMap<>(); // 读取为mm自带脚本（并不是星火的）
    public static List<String> MM_PUBLIC_SCRIPTS = new ArrayList<>(); // 自带外部公共库的所有js代码会被存在这里

    //各个外部路径
    public static final Path GAME_DIR = FMLPaths.GAMEDIR.get();
    public static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get();//.minecraft/config文件夹
    public static final Path NAMESPACE = CONFIG_PATH.resolve(MOD_ID);//模组根文件夹
    public static final Path SPARK_MODULE = GAME_DIR.resolve("spark_modules");
    public static final Path PUBLIC_JS_LIBS = NAMESPACE.resolve("public_scripts");//js外部公共库目录

    public static boolean overwrite = true;//覆写总开关，考虑以后做成用户自定义配置

    // 添加静态字段来跟踪临时目录
    private static final Set<Path> TEMP_DIRS = ConcurrentHashMap.newKeySet();

    // 在类初始化时注册关闭钩子
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Path tempDir : TEMP_DIRS) {
                try {
                    Files.walk(tempDir)
                            .sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    LOGGER.warn("Failed to delete temporary file: {}", path, e);
                                }
                            });
                } catch (IOException e) {
                    LOGGER.warn("Failed to clean up temporary directory: {}", tempDir, e);
                }
            }
        }));
    }

    @SubscribeEvent
    public static void init(FMLCommonSetupEvent event) {
        loadData();
    }

    public static void reload() {
        initResources();
        loadData();
    }

    public static void initResources() {
        EXTERNAL_RESOURCE.clear();
        exceptions.clear();
        errorFiles.clear();
        errorMessages.clear();
//        MM_SCRIPTS.clear();
//        MMInitialJS.clear();
//        MM_PUBLIC_SCRIPTS.clear();
        loadResources();
    }

    /**
     * 外部包加载过程
     */
    public static void loadResources() {
        LOGGER.info("开始从外部包读取资源文件...");
        //清理之前的数据，避免刷新时发生重复的注册
        //保证 主路径、载具包根路径 存在
        Exist(NAMESPACE);
        Exist(SPARK_MODULE);
//        Exist(PUBLIC_JS_LIBS);
//        for (Path root : listPaths(VEHICLES, path -> Files.isDirectory(path) || isZipFile(path))) {
//            String packName = root.getFileName().toString();
//            if (Files.isDirectory(root)) {
//                // 处理文件夹资源包
//                packUp(packName, Exist(root.resolve("font")));
//            } else if (isZipFile(root)) {
//                // 处理ZIP压缩包
//                packUpZip(packName, root);
//            }
//        }
        //TODO:从.minecraft/spark_modules中读取各个包的各个模块的内容，包可以是文件夹也可以是zip压缩包
        for (Path root : listPaths(SPARK_MODULE, path -> Files.isDirectory(path) || isZipFile(path))) {
            String packName = root.getFileName().toString();

            if (Files.isDirectory(root)) {
                // 处理文件夹资源包
                packUp(packName, Exist(root.resolve("font")));
            } else if (isZipFile(root)) {
                // 处理ZIP压缩包
                packUpZip(packName, root);
            }
        }
    }

    /**
     * 外部包加载过程
     */
    public static void loadData() {
        LOGGER.info("开始从外部包读取配置...");
        //保证 主路径、载具包根路径 存在
        Exist(NAMESPACE);
        Exist(PUBLIC_JS_LIBS);
        //公共js库（用于开发时不用覆盖，
        boolean STATIC = true;
        // STATIC: 所有载具包都可以调用里面封装的库代码，所以为了保证用户所有脚本的正常运行，发布版必须覆盖）
        boolean DYNAMIC = false;
        // DYNAMIC: 某些示范代码需要关闭覆盖保证存在文件即可，在生成器中则需要覆盖）

//        copyResourceToFile("/public_scripts/functions.js", PUBLIC_JS_LIBS.resolve("functions.js"), STATIC);
//        for (Path jsPackageFile : listPaths(PUBLIC_JS_LIBS, Files::isRegularFile)) {
//            try {
//                MM_PUBLIC_SCRIPTS.add(new String(Files.readAllBytes(jsPackageFile)));
//            } catch (Exception ignored) {
//            }
//        }

//        MMInitialJS.register();//注册所有JS形式的初始化配置
    }

    @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    public static class DataPackReloader extends SimplePreparableReloadListener<Void> {

        @Override
        protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
            MMDynamicRes.reload();//异步重新读取资源
            return null;
        }

        @Override
        protected void apply(Void nothing, ResourceManager manager, ProfilerFiller profiler) {

        }

        public static void sendErrorToPlayer(Player player) {
            for (int i = 0; i < errorFiles.size(); i++) {
                String file = errorFiles.get(i);
                MutableComponent message = errorMessages.get(i).withColor(Color.RED.getRGB());
                player.sendSystemMessage(Component.translatable("error.machine_max.load", file).withColor(Color.WHITE.getRGB()).append(message));
            }
        }

        public static void sendErrorToConsole(MinecraftServer server) {
            for (int i = 0; i < MMDynamicRes.errorFiles.size(); i++) {
                String file = MMDynamicRes.errorFiles.get(i);
                Component message = MMDynamicRes.errorMessages.get(i);
                server.sendSystemMessage(Component.translatable("error.machine_max.load", file).append(message).withColor(Color.red.getRGB()));
            }
        }

        @SubscribeEvent
        public static void registerClientReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new MMDynamicRes.DataPackReloader());
        }

    }

    public static void GenerateChannels(String jsCode) {
        createDefaultFile(PUBLIC_JS_LIBS.resolve("channels.js"), jsCode, overwrite);
    }

    /**
     * 对一个载具包子目录的解析 packName是载具包名称 categoryPath是子目录
     */
    private static void packUp(String packName, Path categoryPath) {
        if (!Files.exists(categoryPath)) return;
        String category = categoryPath.getFileName().toString();
        for (Path filePath : listAllFiles(categoryPath)) {
            DynamicPack dynamicPack;
            String fileName = filePath.getFileName().toString();
            String relativePath = categoryPath.relativize(filePath).toString().replace("\\", "/").toLowerCase();
            ResourceLocation location = ResourceLocation.tryBuild(MOD_ID, "%s/%s/%s".formatted(packName.toLowerCase(), category, relativePath));
            try {
                if (category.equals("font")) {
                    location = ResourceLocation.tryBuild(MOD_ID, "%s/%s".formatted(category, fileName)); //字体系统的标准搜索路径
                }
                if (location == null) {
                    throw new IllegalArgumentException("error.machine_max.invalid_resource_location");
                }
                dynamicPack = new DynamicPack(packName, location, category, filePath.toFile());//生成动态包（这里保留的目的是一般拿来注入材质包和模型、动画，part-type却不能用要单独实现）
                EXTERNAL_RESOURCE.put(location, dynamicPack);//保存动态包，后续会被addPackEvent读取、注册
            } catch (Exception e) {
                exceptions.add(e);
                errorFiles.add(filePath.toString());
                errorMessages.add(Component.translatable(e.getMessage()));
                LOGGER.error("An error occurred while reading {}, file: {}, skipped. Reason: {}", category, filePath, e.getMessage());
            }
        }
    }

    /**
     * 处理ZIP压缩包，解压并模拟成文件夹资源包
     */
    private static void packUpZip(String packName, Path zipPath) {
        try {
            // 创建临时目录用于解压
            Path tempDir = Files.createTempDirectory("mm_zip_" + packName);
            TEMP_DIRS.add(tempDir);

            // 解压ZIP文件
            try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();

                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    Path entryPath = tempDir.resolve(entry.getName());

                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        try (InputStream is = zipFile.getInputStream(entry)) {
                            Files.copy(is, entryPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }

            // 处理解压后的目录，使用现有的packUp方法
            processUnpackedZip(packName, tempDir);

        } catch (IOException e) {
            LOGGER.error("Failed to process ZIP pack: {}", zipPath, e);
            exceptions.add(e);
            errorFiles.add(zipPath.toString());
            errorMessages.add(Component.translatable("error.machine_max.zip_process_failed", e.getMessage()));
        }
    }

    /**
     * 处理解压后的ZIP内容，使用现有的packUp方法
     */
    private static void processUnpackedZip(String packName, Path unpackedDir) {
        packUp(packName, unpackedDir.resolve("font"));
    }

    /**
     * 保证路径存在，否则创建这个文件夹
     */
    private static Path Exist(Path path) {
        if (!Files.exists(path)) {
            try {
                Files.createDirectory(path);
            } catch (IOException e) {
                LOGGER.error("Failed to create folder on %s because of %s".formatted(path, e));
            }
        }
        return path;
    }


    /**
     * 保证文件存在，否则创建这个文件
     *
     * @return
     */
    public static Path createDefaultFile(Path targetPath, String content, boolean overwrite) {
        try {
            boolean canOverwrite = overwrite && Files.exists(targetPath);
            Files.writeString(
                    targetPath,
                    content,
                    canOverwrite ? StandardOpenOption.TRUNCATE_EXISTING : StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
            System.out.printf("文件%s成功: %s%n", canOverwrite ? "覆写" : "创建", targetPath);
        } catch (IOException e) {
            if (e instanceof FileAlreadyExistsException) {
                LOGGER.info("文件%s已存在，跳过".formatted(targetPath));
            } else LOGGER.error("创建文件 %s 时发生错误：%s".formatted(targetPath, e));
        }
        return targetPath;
    }

    /**
     * 保证文件存在，否则通过base64创建这个文件
     *
     * @return
     */
    public static Path createDefaultFileByBase64(Path targetPath, String base64, boolean overwrite) {
        try {
            boolean canOverwrite = overwrite && Files.exists(targetPath);
            byte[] fileBytes = Base64.getDecoder().decode(base64);
            Files.write(
                    targetPath,
                    fileBytes,
                    canOverwrite ? StandardOpenOption.TRUNCATE_EXISTING : StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
            System.out.printf("文件%s成功: %s%n", canOverwrite ? "覆写" : "创建", targetPath);
        } catch (IOException e) {
            if (e instanceof FileAlreadyExistsException) {
                LOGGER.info("文件%s已存在，跳过".formatted(targetPath));
            } else LOGGER.error("创建文件 %s 时发生错误：%s".formatted(targetPath, e));
        }
        return targetPath;
    }


    /**
     * 获取一个路径下所有的子目录，第二个是过滤器（比如Files::isDirectory 是拿到所有子文件夹）
     */
    private static List<Path> listPaths(Path path, Predicate<Path> predicate) {
        try {
            return Files.list(path)
                    .filter(predicate)
                    .toList();
        } catch (IOException e) {
            LOGGER.error("获取路径列表失败： {}", path, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取一个路径下各层级子目录中的所有文件路径，包括多层嵌套文件夹中的文件
     */
    public static List<Path> listAllFiles(Path path) {
        List<Path> result = new ArrayList<>();
        try {
            Files.walk(path)
                    .filter(Files::isRegularFile)
                    .forEach(result::add);
        } catch (IOException e) {
            LOGGER.error("获取文件路径列表失败： {}", path, e);
        }
        return result;
    }

    /**
     * 将文件内容读取到字节数组输入流（自动关闭资源）
     *
     * @param file 要读取的文件对象
     * @return ByteArrayInputStream 或 null（读取失败时）
     */
    public static ByteArrayInputStream fileStream(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return new ByteArrayInputStream(bytes);
        } catch (IOException e) {
            System.err.println("文件获取字节流时发生错误：" + e.getMessage());
            return null;
        }
    }


    /**
     * 将文件名称的.xxx后缀部分去掉
     */
    public static String getRealName(String str) {
        return str.contains(".") ? str.substring(0, str.lastIndexOf('.')) : str;
    }


    /**
     * 将类路径资源文件复制到指定文件系统路径
     *
     * @param resourcePath 资源路径 (e.g. "config/default.properties")
     * @param targetPath   目标文件系统路径
     * @param overwrite    是否覆盖已存在文件
     */
    public static void copyResourceToFile(String resourcePath, Path targetPath, boolean overwrite) {
        ClassLoader classLoader = MMDynamicRes.class.getClassLoader();

        try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
            // 检查资源是否存在
            if (inputStream == null) {
                throw new IOException("未找到资源文件: " + resourcePath);
            }

            // 创建父目录（如果不存在）
            Path parentDir = targetPath.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }

            // 选择复制选项
            if (overwrite) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(inputStream, targetPath);
            }
        } catch (IOException e) {
            System.err.println("复制失败: " + resourcePath + " -> " + targetPath);
            e.printStackTrace();
        }
    }

    /**
     * 检查路径是否为ZIP文件
     */
    private static boolean isZipFile(Path path) {
        return Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(".zip");
    }

}
