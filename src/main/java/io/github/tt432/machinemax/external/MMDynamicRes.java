package io.github.tt432.machinemax.external;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;

import static io.github.tt432.machinemax.MachineMax.LOGGER;
import static io.github.tt432.machinemax.MachineMax.MOD_ID;

public class MMDynamicRes {
    public static HashMap<ResourceLocation, DynamicPack> EXTERNAL_RESOURCE = new HashMap<>(); //所有当下读取的外部资源

    //各个外部路径
    public static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get();
    public static final Path NAMESPACE = CONFIG_PATH.resolve(MOD_ID);
    public static final Path VEHICLES = NAMESPACE.resolve("vehicles");

    public static void loadData() {
        Exist(NAMESPACE);
        Exist(VEHICLES);
        for (Path root : listPaths(VEHICLES, Files::isDirectory)) {
            String packName = root.getFileName().toString();
            packUp(packName, Exist(root.resolve("part")));
            packUp(packName, Exist(root.resolve("part_type")));
            packUp(packName, Exist(root.resolve("script")));
        }
    }

    private static void packUp(String packName, Path categoryPath) {
        String category = categoryPath.getFileName().toString();
        for (Path filePath : listPaths(categoryPath, Files::isRegularFile)) {
            String fileName = filePath.getFileName().toString();
            ResourceLocation location = ResourceLocation.tryBuild(MOD_ID, "%s/%s/%s".formatted(packName, category, fileName));
            EXTERNAL_RESOURCE.put(location, new DynamicPack(location, category, filePath.toFile()));
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
    private static List<Path> listPaths(Path path, Predicate<Path> predicate) {
        try {
            return Files.list(path)
                    .filter(predicate)
                    .toList();
        } catch (IOException e) {
            LOGGER.error("Failed to list paths in {}", path, e);
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
            System.err.println("File failed to load" + e.getMessage());
            return null;
        }
    }


    private static String getRealName(String str) {     //将文件名称的.xxx后缀部分去掉
        return str.contains(".") ? str.substring(0, str.lastIndexOf('.')) : str;
    }
}
