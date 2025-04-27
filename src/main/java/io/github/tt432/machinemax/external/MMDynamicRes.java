package io.github.tt432.machinemax.external;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.tt432.machinemax.common.vehicle.PartType;
import io.github.tt432.machinemax.external.parse.PartTypeAdapter;
import io.github.tt432.machinemax.external.parse.ResourceLocationAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;

import static io.github.tt432.machinemax.MachineMax.LOGGER;
import static io.github.tt432.machinemax.MachineMax.MOD_ID;

public class MMDynamicRes {
    public static HashMap<ResourceLocation, DynamicPack> EXTERNAL_RESOURCE = new HashMap<>(); //所有当下读取的外部资源
    public static HashMap<ResourceLocation, PartType> PART_TYPES = new HashMap<>();
    //各个外部路径
    public static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get();
    public static final Path NAMESPACE = CONFIG_PATH.resolve(MOD_ID);
    public static final Path VEHICLES = NAMESPACE.resolve("vehicles");

    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new MMDynamicRes.DataPackReloader());
    }
    public static void loadData() { //数据加载过程
        LOGGER.warn("开始从外部包读取配置！！");
        EXTERNAL_RESOURCE.clear();
        PART_TYPES.clear();
        Exist(NAMESPACE);
        Exist(VEHICLES);
        GenerateTestPack();
        for (Path root : listPaths(VEHICLES, Files::isDirectory)) {
            String packName = root.getFileName().toString();
            packUp(packName, Exist(root.resolve("part")));
            packUp(packName, Exist(root.resolve("part_type")));
            packUp(packName, Exist(root.resolve("script")));
        }
    }

    public static class DataPackReloader extends SimplePreparableReloadListener<Void> {
        @Override
        protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
            return null;
        }

        @Override
        protected void apply(Void nothing, ResourceManager manager, ProfilerFiller profiler) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("[%s]: 尝试重载载具包"));
            loadData();
        }
    }

    private static void GenerateTestPack() {
        Path testpack = Exist(VEHICLES.resolve("testpack"));
        Path partFolder = Exist(testpack.resolve("part"));
        Path partTypeFolder = Exist(testpack.resolve("part_type"));
        Exist(testpack.resolve("script"));
        createDefaultFile(partFolder.resolve("test_cube.geo.json"), TestPackProvider.part());
        createDefaultFile(partTypeFolder.resolve("test_cube.json"), TestPackProvider.part_type());
    }

    private static void packUp(String packName, Path categoryPath) {
        GsonBuilder gsonBuilder = new GsonBuilder()
                .registerTypeAdapter(ResourceLocation.class, new ResourceLocationAdapter()); //首先注册ResourceLocation解析器
        String category = categoryPath.getFileName().toString();
        for (Path filePath : listPaths(categoryPath, Files::isRegularFile)) {
            String fileName = filePath.getFileName().toString();
            ResourceLocation location = ResourceLocation.tryBuild(MOD_ID, "%s/%s/%s".formatted(packName, category, fileName));
            DynamicPack dynamicPack = new DynamicPack(location, category, filePath.toFile());
            String content = dynamicPack.getContent(false);
            switch (category) {
                case "part_type" -> { //part_type文件夹中的配置
                    Gson gson = gsonBuilder
                            .registerTypeAdapter(PartType.class, new PartTypeAdapter()) //注册PartType解析器
                            .create();
                    PartType partType = gson.fromJson(content, PartType.class); //解析配置得到PartType
                    PART_TYPES.put(partType.registryKey, partType); //我暂时把它存在PART_TYPES
                }
                case "part" -> {}
                case "script" -> {}
            }
            EXTERNAL_RESOURCE.put(location, dynamicPack);
        }

    }

    private static Path Exist(Path path){  //保证路径存在
        if (!Files.exists(path)) {
            try {
                Files.createDirectory(path);
            } catch (IOException e) {
                LOGGER.error("Failed to create folder on %s because of %s".formatted(path, e));
            }
        }
        return path;
    }

    public static void createDefaultFile(Path targetPath, String content) {
        if (!Files.exists(targetPath)) {
            try {
            Files.writeString(
                    targetPath,
                    content,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
            System.out.println("文件创建成功: " + targetPath);
            } catch (IOException e) {
                LOGGER.error("创建文件 %s 时发生错误：%s".formatted(targetPath, e));
            }
        } else {
            System.out.println("文件已存在，跳过创建: " + targetPath);
        }
    }


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


    private static String getRealName(String str) {     //将文件名称的.xxx后缀部分去掉
        return str.contains(".") ? str.substring(0, str.lastIndexOf('.')) : str;
    }
}
