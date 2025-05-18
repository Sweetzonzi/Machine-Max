package io.github.tt432.machinemax.external;

import cn.solarmoon.spark_core.animation.model.origin.OModel;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.mojang.serialization.JsonOps;
import io.github.tt432.machinemax.common.vehicle.PartType;
import io.github.tt432.machinemax.common.vehicle.data.VehicleData;
import io.github.tt432.machinemax.external.data.TestPackProvider;
import io.github.tt432.machinemax.external.parse.OBoneParse;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

import static io.github.tt432.machinemax.MachineMax.LOGGER;
import static io.github.tt432.machinemax.MachineMax.MOD_ID;

public class MMDynamicRes {
    public static ConcurrentMap<ResourceLocation, DynamicPack> EXTERNAL_RESOURCE = new ConcurrentHashMap<>(); //所有当下读取的外部资源
    public static ConcurrentMap<ResourceLocation, PartType> PART_TYPES = new ConcurrentHashMap<>(); // key是自带构造函数生成的registryKey， value是暂存的PartType
    public static ConcurrentMap<ResourceLocation, OModel> O_MODELS = new ConcurrentHashMap<>(); // 读取为part的骨架数据，同时是geckolib的模型文件 key是自带构造函数生成的registryKey， value是暂存的OModel
    public static ConcurrentMap<ResourceLocation, VehicleData> BLUEPRINTS = new ConcurrentHashMap<>(); // 读取为蓝图数据，每个包可以有多个蓝图 key是自带构造函数生成的registryKey， value是暂存的VehicleData
    public static ConcurrentMap<ResourceLocation, JsonElement> COLORS = new ConcurrentHashMap<>(); // 读取为蓝图数据，每个包可以有多个蓝图 key是自带构造函数生成的registryKey， value是暂存的VehicleData

    //各个外部路径
    public static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get();//.minecraft/config文件夹
    public static final Path NAMESPACE = CONFIG_PATH.resolve(MOD_ID);//模组根文件夹
    public static final Path VEHICLES = NAMESPACE.resolve("custom_packs");//载具包根文件夹

    /**
     * 注册热重载事件
     */
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new MMDynamicRes.DataPackReloader());
    }

    public static void init(FMLCommonSetupEvent event) {
        loadData();
    }

    public static void reload() {
        initResources();
        loadData();
    }

    public static void initResources() {
        EXTERNAL_RESOURCE.clear();
        PART_TYPES.clear();
        OBoneParse.clear();
        BLUEPRINTS.clear();
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
        Exist(VEHICLES);
        GenerateTestPack(); //自动生成测试包
        for (Path root : listPaths(VEHICLES, Files::isDirectory)) {
            String packName = root.getFileName().toString();
            //资源类数据先加载
            packUp(packName, Exist(root.resolve("content")));
            packUp(packName, Exist(root.resolve("lang")));
            packUp(packName, Exist(root.resolve("texture")));
            packUp(packName, Exist(root.resolve("font")));
        }
    }

    /**
     * 外部包加载过程
     */
    public static void loadData() {
        LOGGER.info("开始从外部包读取配置...");
        //保证 主路径、载具包根路径 存在
        Exist(NAMESPACE);
        Exist(VEHICLES);
        GenerateTestPack(); //自动生成测试包
        for (Path root : listPaths(VEHICLES, Files::isDirectory)) {
            String packName = root.getFileName().toString();
            //各种MM配置
            packUp(packName, Exist(root.resolve("model")));
            packUp(packName, Exist(root.resolve("part_type")));
            packUp(packName, Exist(root.resolve("script")));
            packUp(packName, Exist(root.resolve("blueprint")));
            packUp(packName, Exist(root.resolve("color")));
        }
    }

    public static class DataPackReloader extends SimplePreparableReloadListener<Void> {
        @Override
        protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
            return null;
        }

        @Override
        protected void apply(Void nothing, ResourceManager manager, ProfilerFiller profiler) {
            MMDynamicRes.reload();// 重新读取
            if (Minecraft.getInstance().player instanceof Player player)
                player.sendSystemMessage(Component.literal("[%s]: 外部载具包已重载!".formatted(MOD_ID)));
        }
    }

    /**
     * 自动生成测试包
     */
    private static void GenerateTestPack() {
        //拿到存在的路径
        Path examplePack = Exist(VEHICLES.resolve("example_pack"));
        Path partModelFolder = Exist(examplePack.resolve("model"));
        Path partTypeFolder = Exist(examplePack.resolve("part_type"));
        Path script = Exist(examplePack.resolve("script"));
        Path blueprint = Exist(examplePack.resolve("blueprint"));
        Path lang = Exist(examplePack.resolve("lang"));
        Path texture = Exist(examplePack.resolve("texture"));
        Path content = Exist(examplePack.resolve("content"));
        Path font = Exist(examplePack.resolve("font"));
        Path color = Exist(examplePack.resolve("color"));

        //设置默认测试包的路径、名字、内容
        //模型文件
        createDefaultFile(partModelFolder.resolve("test_cube.geo.json"), TestPackProvider.partModel_TestCube(), false);
        createDefaultFile(partModelFolder.resolve("ae86_back_seat.geo.json"), TestPackProvider.partModel_BackSeat(), false);
        createDefaultFile(partModelFolder.resolve("ae86_seat.geo.json"), TestPackProvider.partModel_Seat(), false);
        createDefaultFile(partModelFolder.resolve("ae86_hull.geo.json"), TestPackProvider.partModel_Hull(), false);
        createDefaultFile(partModelFolder.resolve("ae86_chassis_all_terrain.geo.json"), TestPackProvider.partModel_Chassis(), false);
        createDefaultFile(partModelFolder.resolve("ae86_wheel_all_terrain_right.geo.json"), TestPackProvider.partModel_RightWheel(), false);
        createDefaultFile(partModelFolder.resolve("ae86_wheel_all_terrain_left.geo.json"), TestPackProvider.partModel_LeftWheel(), false);
        //部件定义文件
        createDefaultFile(partTypeFolder.resolve("test_cube.json"), TestPackProvider.partType_TestCube(), false);
        //TODO:更容易造成物理线程的BoundingBox计算出错，获得一个巨大的AABB尺寸，原因不明，通过减少使用现场计算的AABB转而使用缓存缓解了此问题，但仍需修复
        createDefaultFile(partTypeFolder.resolve("ae86_back_seat.json"), TestPackProvider.partType_BackSeat(), false);
        createDefaultFile(partTypeFolder.resolve("ae86_seat.json"), TestPackProvider.partType_Seat(), false);
        createDefaultFile(partTypeFolder.resolve("ae86_hull.json"), TestPackProvider.partType_Hull(), false);
        createDefaultFile(partTypeFolder.resolve("ae86_chassis_all_terrain.json"), TestPackProvider.partType_Chassis(), false);
        createDefaultFile(partTypeFolder.resolve("ae86_wheel_all_terrain.json"), TestPackProvider.partType_Wheel(), false);
        //蓝图文件
        createDefaultFile(blueprint.resolve("test_blue_print.json"), TestPackProvider.blueprint(), true);
        //自定义翻译
        createDefaultFile(lang.resolve("zh_cn.json"), TestPackProvider.zh_cn(), false);
        createDefaultFile(lang.resolve("en_us.json"), TestPackProvider.en_us(), false);
        //自带测试材质
        createDefaultFileByBase64(texture.resolve("test_cube.png"), TestPackProvider.test_cube_png_base64(), false);
        createDefaultFileByBase64(texture.resolve("ae86_1.png"), TestPackProvider.ae86_1_png_base64(), false);
        createDefaultFileByBase64(texture.resolve("ae86_2.png"), TestPackProvider.ae86_2_png_base64(), false);
        createDefaultFileByBase64(texture.resolve("ae86_3.png"), TestPackProvider.ae86_3_png_base64(), false);
        createDefaultFileByBase64(texture.resolve("ae86_4.png"), TestPackProvider.ae86_4_png_base64(), false);
        //自定义文本文件
        createDefaultFile(content.resolve("test.html"), TestPackProvider.content_html(), false);
        //自定义字体文件
        createDefaultFile(font.resolve("test_font.json"), TestPackProvider.test_font_json(), true);
        copyResourceToFile("/bell.ttf", font.resolve("bell.ttf"), true);
        copyResourceToFile("/bellb.ttf", font.resolve("bellb.ttf"), true);
        copyResourceToFile("/belli.ttf", font.resolve("belli.ttf"), true);

        createDefaultFile(color.resolve("color_palette.json"), TestPackProvider.color_palette_json(), false);
    }


    /**
     * 对一个载具包子目录的解析 packName是载具包名称 categoryPath是子目录
     */
    private static void packUp(String packName, Path categoryPath) {
        String category = categoryPath.getFileName().toString();
        for (Path filePath : listPaths(categoryPath, Files::isRegularFile)) {
            String fileName = filePath.getFileName().toString();
            String fileRealName = getRealName(fileName);
            ResourceLocation location = ResourceLocation.tryBuild(MOD_ID, "%s/%s/%s".formatted(packName, category, fileName));
            try (JsonReader reader = new JsonReader(new FileReader(filePath.toFile()))) {
                reader.setLenient(true); // 允许非严格JSON
                JsonElement json = JsonParser.parseReader(reader);
                switch (category) {
                    case "part_type" -> { //part_type文件夹中的配置
                        PartType partType = PartType.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
                        PART_TYPES.put(partType.registryKey, partType); //我暂时把它存在PART_TYPES
                        //测试数据是否成功录入
//                        partType.getConnectorIterator().forEachRemaining((c) -> {
//                            LOGGER.info("连接器队列: " + c);
//                        });
//                        partType.getPartOutwardConnectors().forEach((a, b) -> {
//                            LOGGER.info("接口名称: %s 类型: %s".formatted(a, b));
//                        });
                    }

                    case "model" -> {
                        // 首先决定载具包中variants的格式定义
//                    fileName = fileRealName; //删去资源路径后缀 .json
//                    location = ResourceLocation.tryBuild(MOD_ID, "part/%s".formatted(fileName));  //使用默认的路径格式 machine_max:part/test_cube.geo
                        // 上面被注释则是放弃覆盖，继续使用路径格式 machine_max:testpack/part/test_cube.geo.json
                        OBoneParse.register(location, json);
                    }

                    case "script" -> {
                    }
                    case "blueprint" -> {
                        VehicleData data = VehicleData.CODEC.decode(JsonOps.INSTANCE, json).result().orElseThrow().getFirst();
                        BLUEPRINTS.put(location, data);
                    }

                    case "lang" -> {
                        location = ResourceLocation.tryBuild(MOD_ID, "%s/%s".formatted(category, fileName)); //语言翻译系统的标准搜索路径
                    }
                    case "font" -> {
                        location = ResourceLocation.tryBuild(MOD_ID, "%s/%s".formatted(category, fileName)); //字体系统的标准搜索路径
                        System.out.println("FONT " + location);
                    }

                    case "color" -> {
                        COLORS.put(location, json);
                    }
                }

            } catch (IOException e) {
                LOGGER.error("读取{}时失败 目标文件位于{}: {}", category, filePath, e.getMessage());
            }

            DynamicPack dynamicPack = new DynamicPack(location, category, filePath.toFile());//生成动态包（这里保留的目的是一般拿来注入材质包和模型、动画，part-type却不能用要单独实现）
            EXTERNAL_RESOURCE.put(location, dynamicPack);//保存动态包，后续会被addPackEvent读取、注册

//            //把指定包的文件转换成base64字符串形式    取消注释则会生成镜像base64文件包 在 run/config/machine_max/base64ify
//            Path master_base64ify = Exist(NAMESPACE.resolve("base64ify"));
//            Path root_base64ify = Exist(master_base64ify.resolve(packName+"_base64"));
//            Path category_base64ify = Exist(root_base64ify.resolve(category));
//            createDefaultFile(category_base64ify.resolve(fileName+"_base64.txt"), dynamicPack.getBase64(), true);
        }

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
            LOGGER.error("创建文件 %s 时发生错误：%s".formatted(targetPath, e));
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
            LOGGER.error("创建文件 %s 时发生错误：%s".formatted(targetPath, e));
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


}
