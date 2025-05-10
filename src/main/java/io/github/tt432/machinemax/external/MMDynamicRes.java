package io.github.tt432.machinemax.external;

import cn.solarmoon.spark_core.animation.model.origin.OModel;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.tt432.machinemax.common.vehicle.PartType;
import io.github.tt432.machinemax.common.vehicle.data.VehicleData;
import io.github.tt432.machinemax.external.parse.OBoneParse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Predicate;

import static io.github.tt432.machinemax.MachineMax.LOGGER;
import static io.github.tt432.machinemax.MachineMax.MOD_ID;

public class MMDynamicRes {
    public static HashMap<ResourceLocation, DynamicPack> EXTERNAL_RESOURCE = new HashMap<>(); //所有当下读取的外部资源
    public static HashMap<ResourceLocation, PartType> PART_TYPES = new HashMap<>(); // key是自带构造函数生成的registryKey， value是暂存的PartType
    public static HashMap<ResourceLocation, OModel> O_MODELS = new HashMap<>(); // 读取为part的骨架数据，同时是geckolib的模型文件 key是自带构造函数生成的registryKey， value是暂存的OModel
    public static HashMap<ResourceLocation, VehicleData> BLUEPRINTS = new HashMap<>(); // 读取为蓝图数据，每个包可以有多个蓝图 key是自带构造函数生成的registryKey， value是暂存的VehicleData

    //各个外部路径
    public static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get();//.minecraft/config文件夹
    public static final Path NAMESPACE = CONFIG_PATH.resolve(MOD_ID);//模组根文件夹
    public static final Path VEHICLES = NAMESPACE.resolve("vehicles");//载具包根文件夹

    /**注册热重载事件*/
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new MMDynamicRes.DataPackReloader());
    }

    /**外部包加载过程*/
    public static void loadData() {
        LOGGER.warn("开始从外部包读取配置！！");
        //清理之前的数据，避免刷新时发生重复的注册
        EXTERNAL_RESOURCE.clear();
        PART_TYPES.clear();
        OBoneParse.clear();
        BLUEPRINTS.clear();
        //保证 主路径、载具包根路径 存在
        Exist(NAMESPACE);
        Exist(VEHICLES);
        GenerateTestPack(); //自动生成测试包
        for (Path root : listPaths(VEHICLES, Files::isDirectory)) {
            String packName = root.getFileName().toString();
            packUp(packName, Exist(root.resolve("part")));
            packUp(packName, Exist(root.resolve("part_type")));
            packUp(packName, Exist(root.resolve("script")));
            packUp(packName, Exist(root.resolve("blueprint")));
            packUp(packName, Exist(root.resolve("lang")));
            packUp(packName, Exist(root.resolve("texture")));
        }
        if (Minecraft.getInstance().getLanguageManager() instanceof LanguageManager langManager) {
            System.out.printf("当前语言系统编号 CODE: %s \n", langManager.getSelected());
            System.out.println("下面是所有支持的语言系统编号: ");
            langManager.getLanguages().forEach((code, info)-> {
                System.out.printf("CODE: %s INFO: %s \n", code, info);
            });

        }

    }

    public static class DataPackReloader extends SimplePreparableReloadListener<Void> {
        @Override
        protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
            return null;
        }

        @Override
        protected void apply(Void nothing, ResourceManager manager, ProfilerFiller profiler) {
            loadData();// 重新读取
            if (Minecraft.getInstance().player instanceof Player player)
                player.sendSystemMessage(Component.literal("[%s]: 你刚刚尝试了重载载具包".formatted(MOD_ID)));
        }
    }

    /**自动生成测试包*/
    private static void GenerateTestPack() {
        //拿到存在的路径
        Path testpack = Exist(VEHICLES.resolve("testpack"));
        Path partFolder = Exist(testpack.resolve("part"));
        Path partTypeFolder = Exist(testpack.resolve("part_type"));
        Path script = Exist(testpack.resolve("script"));
        Path blueprint = Exist(testpack.resolve("blueprint"));
        Path lang = Exist(testpack.resolve("lang"));
        Path texture = Exist(testpack.resolve("texture"));
        //设置默认测试包的路径、名字、内容
        createDefaultFile(partFolder.resolve("test_cube_vpack.geo.json"), TestPackProvider.part(), true);
        createDefaultFile(partTypeFolder.resolve("test_cube_vpack.json"), TestPackProvider.part_type(), true);
        createDefaultFile(blueprint.resolve("test_blue_print.json"), TestPackProvider.blueprint(), true);
        //自定义翻译
        createDefaultFile(lang.resolve("zh_cn.json"), TestPackProvider.zh_cn(), true);
        createDefaultFile(lang.resolve("en_us.json"), TestPackProvider.en_us(), true);
        //自带测试材质
        createDefaultFileByBase64(texture.resolve("test_cube_vpack.png"), TestPackProvider.test_cube_vpack_png_base64(), true);
    }


    /**对一个载具包子目录的解析 packName是载具包名称 categoryPath是子目录*/
    private static void packUp(String packName, Path categoryPath) {
        String category = categoryPath.getFileName().toString();
        for (Path filePath : listPaths(categoryPath, Files::isRegularFile)) {
            String fileName = filePath.getFileName().toString();
            String fileRealName = getRealName(fileName);
            ResourceLocation location = ResourceLocation.tryBuild(MOD_ID, "%s/%s/%s".formatted(packName, category, fileName));
            try {
                JsonElement json = JsonParser.parseString(Files.readString(filePath));
                switch (category) {
                    case "part_type" -> { //part_type文件夹中的配置
                        PartType partType = PartType.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
                        PART_TYPES.put(partType.registryKey, partType); //我暂时把它存在PART_TYPES
                        //测试数据是否成功录入
                        partType.getConnectorIterator().forEachRemaining((c) -> {
                            LOGGER.info("连接器队列: " +c);
                        });
                        partType.getPartOutwardConnectors().forEach((a, b) -> {
                            LOGGER.info("接口名称: %s 类型: %s".formatted(a, b));
                        });
                    }

                    case "part" -> {
                        // 首先决定载具包中variants的格式定义
//                    fileName = fileRealName; //删去资源路径后缀 .json
//                    location = ResourceLocation.tryBuild(MOD_ID, "part/%s".formatted(fileName));  //使用默认的路径格式 machine_max:part/test_cube.geo
                        // 上面被注释则是放弃覆盖，继续使用路径格式 machine_max:testpack/part/test_cube.geo.json
                        OBoneParse.register(location, json);
                    }

                    case "script" -> {}
                    case "blueprint" -> {
                        VehicleData data = VehicleData.CODEC.decode(JsonOps.INSTANCE, json).result().orElseThrow().getFirst();
                        BLUEPRINTS.put(location, data);
                    }

                    case "lang" -> {
                        location = ResourceLocation.tryBuild(MOD_ID, "%s/%s".formatted(category, fileName)); //语言翻译系统的标准搜索路径
                    }
                }

            } catch (IOException e) {
                LOGGER.error("读取{}时失败 目标文件位于{}: {}", category, filePath, e.getMessage());
            }

            DynamicPack dynamicPack = new DynamicPack(location, category, filePath.toFile());//生成动态包（这里保留的目的是一般拿来注入材质包和模型、动画，part-type却不能用要单独实现）
            EXTERNAL_RESOURCE.put(location, dynamicPack);//保存动态包，后续会被addPackEvent读取、注册
        }

    }

    /**保证路径存在，否则创建这个文件夹*/
    private static Path Exist(Path path){
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



    /**获取一个路径下所有的子目录，第二个是过滤器（比如Files::isDirectory 是拿到所有子文件夹）*/
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


    /**将文件名称的.xxx后缀部分去掉*/
    public static String getRealName(String str) {
        return str.contains(".") ? str.substring(0, str.lastIndexOf('.')) : str;
    }
}
