package io.github.sweetzonzi.machinemax.external;

import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.client.gui.renderable.RenderableAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.PartType;
import io.github.sweetzonzi.machinemax.common.vehicle.data.VehicleData;
import io.github.sweetzonzi.machinemax.external.js.MMInitialJS;
import io.github.sweetzonzi.machinemax.external.parse.OBoneParse;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import javax.annotation.Nullable;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

import static io.github.sweetzonzi.machinemax.MachineMax.LOGGER;
import static io.github.sweetzonzi.machinemax.MachineMax.MOD_ID;

public class MMDynamicRes {
    public static ConcurrentMap<ResourceLocation, DynamicPack> EXTERNAL_RESOURCE = new ConcurrentHashMap<>(); //所有当下读取的外部资源
    public static ConcurrentMap<ResourceLocation, PartType> PART_TYPES = new ConcurrentHashMap<>(); // key是自带构造函数生成的registryKey， value是暂存的PartType
    //TODO:按维度区分，避免不同服务端物理线程获取到相同的对象
    public static ConcurrentMap<ResourceLocation, PartType> SERVER_PART_TYPES = new ConcurrentHashMap<>(); // key是自带构造函数生成的registryKey， value是暂存的PartType
    public static ConcurrentMap<ResourceLocation, OModel> O_MODELS = new ConcurrentHashMap<>(); // 读取为part的骨架数据，同时是geckolib的模型文件 key是自带构造函数生成的registryKey， value是暂存的OModel
    public static ConcurrentMap<ResourceLocation, VehicleData> BLUEPRINTS = new ConcurrentHashMap<>(); // 读取为蓝图数据，每个包可以有多个蓝图 key是自带构造函数生成的registryKey， value是暂存的VehicleData
    public static List<Pair<ResourceLocation, JsonElement>> CRAFTING_RECIPES = new ArrayList<>();
    public static ConcurrentMap<ResourceLocation, RenderableAttr> CUSTOM_HUD = new ConcurrentHashMap<>(); // 自定义HUD配置文件
    public static ConcurrentMap<ResourceLocation, JsonElement> COLORS = new ConcurrentHashMap<>(); // 读取为自定义色彩合集 key注册路径， value是该文件的JsonElement对象
    public static List<Exception> exceptions = new ArrayList<>(); // 读取过程中出现的异常
    public static List<String> errorFiles = new ArrayList<>(); // 读取过程中出现错误的文件
    public static List<MutableComponent> errorMessages = new ArrayList<>(); // 读取过程中出现错误的提示信息
    public static ConcurrentMap<ResourceLocation, DynamicPack> MM_SCRIPTS = new ConcurrentHashMap<>(); // 读取为mm自带脚本（并不是星火的）
    public static List<String> MM_PUBLIC_SCRIPTS = new ArrayList<>(); // 自带外部公共库的所有js代码会被存在这里

    //各个外部路径
    public static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get();//.minecraft/config文件夹
    public static final Path NAMESPACE = CONFIG_PATH.resolve(MOD_ID);//模组根文件夹
    public static final Path VEHICLES = NAMESPACE.resolve("custom_packs");//载具包根文件夹
    public static final Path PUBLIC_JS_LIBS = NAMESPACE.resolve("public_scripts");//js外部公共库目录

    public static boolean overwrite = true;//覆写总开关，考虑以后做成用户自定义配置

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
        CUSTOM_HUD.clear();
        SERVER_PART_TYPES.clear();
        OBoneParse.clear();
        BLUEPRINTS.clear();
        CRAFTING_RECIPES.clear();
        exceptions.clear();
        errorFiles.clear();
        errorMessages.clear();
        MM_SCRIPTS.clear();
        MMInitialJS.clear();
        MM_PUBLIC_SCRIPTS.clear();
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
        Exist(PUBLIC_JS_LIBS);
        GenerateTestPack(); //自动生成测试包
        for (Path root : listPaths(VEHICLES, Files::isDirectory)) {
            String packName = root.getFileName().toString();
            //资源类数据先加载
            packUp(packName, Exist(root.resolve("content")));
            packUp(packName, Exist(root.resolve("lang")));
            packUp(packName, Exist(root.resolve("texture")));
            packUp(packName, Exist(root.resolve("sound")));
            packUp(packName, Exist(root.resolve("hud")));
            packUp(packName, Exist(root.resolve("icon")));
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
        Exist(PUBLIC_JS_LIBS);
        for (Path root : listPaths(VEHICLES, Files::isDirectory)) {
            String packName = root.getFileName().toString();
            //各种MM配置
            packUp(packName, Exist(root.resolve("blueprint")));
            packUp(packName, Exist(root.resolve("model")));
            packUp(packName, Exist(root.resolve("animation")));
            packUp(packName, Exist(root.resolve("part_type")));
            packUp(packName, Exist(root.resolve("recipe")));
            packUp(packName, Exist(root.resolve("script")));
            packUp(packName, Exist(root.resolve("color")));
        }

        //公共js库（用于开发时不用覆盖，
        boolean STATIC = true;
        // STATIC: 所有载具包都可以调用里面封装的库代码，所以为了保证用户所有脚本的正常运行，发布版必须覆盖）
        boolean DYNAMIC = false;
        // DYNAMIC: 某些示范代码需要关闭覆盖保证存在文件即可，在生成器中则需要覆盖）

        copyResourceToFile("/public_scripts/functions.js", PUBLIC_JS_LIBS.resolve("functions.js"), STATIC);
        for (Path jsPackageFile : listPaths(PUBLIC_JS_LIBS, Files::isRegularFile)) {
            try {
                MM_PUBLIC_SCRIPTS.add(new String(Files.readAllBytes(jsPackageFile)));
            } catch (Exception ignored) {
            }
        }

        MMInitialJS.register();//注册所有JS形式的初始化配置
    }

    @EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
    public static class DataPackReloader extends SimplePreparableReloadListener<Void> {
        @Nullable
        private static ReloadableServerResources serverResources;

        @Override
        protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
            MMDynamicRes.reload();//异步重新读取资源
            return null;
        }

        @Override
        protected void apply(Void nothing, ResourceManager manager, ProfilerFiller profiler) {
            //TODO: 整体转为数据包方案？
            if (serverResources != null) {
                //注入自定义配方
                RecipeManager recipeManager = serverResources.getRecipeManager();
                registerRecipes(recipeManager, CRAFTING_RECIPES);
            }
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
        public static void registerServerReloadListeners(AddReloadListenerEvent event) {
            event.addListener(new MMDynamicRes.DataPackReloader());
            serverResources = event.getServerResources();//每次reload都会有新的serverResources被创建，因此在这里保存下来最新的
        }

        public static void registerClientReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new MMDynamicRes.DataPackReloader());
        }

        private void registerRecipes(RecipeManager manager, List<Pair<ResourceLocation, JsonElement>> recipes) {
            // 使用反射访问RecipeManager内部映射
            try {
                // 获取当前不可变集合
                Field byNameField = RecipeManager.class.getDeclaredField("byName");
                byNameField.setAccessible(true);
                Field byTypeField = RecipeManager.class.getDeclaredField("byType");
                byTypeField.setAccessible(true);

                Map<ResourceLocation, RecipeHolder<?>> currentByName =
                        (Map<ResourceLocation, RecipeHolder<?>>) byNameField.get(manager);
                Multimap<RecipeType<?>, RecipeHolder<?>> currentByType =
                        (Multimap<RecipeType<?>, RecipeHolder<?>>) byTypeField.get(manager);

                // 创建可变副本
                Map<ResourceLocation, RecipeHolder<?>> newByName = new HashMap<>(currentByName);
                Multimap<RecipeType<?>, RecipeHolder<?>> newByType = ArrayListMultimap.create();

                // 复制现有数据到新集合
                for (Map.Entry<RecipeType<?>, Collection<RecipeHolder<?>>> entry : currentByType.asMap().entrySet()) {
                    newByType.putAll(entry.getKey(), entry.getValue());
                }

                // 添加新配方
                for (Pair<ResourceLocation, JsonElement> entry : recipes) {
                    ResourceLocation id = entry.getFirst();
                    JsonElement json = entry.getSecond();

                    RecipeHolder<?> recipeHolder = parseRecipe(id, json);
                    if (recipeHolder != null) {
                        // 替换同名配方
                        newByName.put(id, recipeHolder);
                        // 按类型分组
                        newByType.put(recipeHolder.value().getType(), recipeHolder);
                    }
                }

                // 创建不可变版本
                Map<ResourceLocation, RecipeHolder<?>> immutableByName = ImmutableMap.copyOf(newByName);
                Multimap<RecipeType<?>, RecipeHolder<?>> immutableByType = ImmutableMultimap.copyOf(newByType);

                // 更新RecipeManager
                byNameField.set(manager, immutableByName);
                byTypeField.set(manager, immutableByType);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException("Failed to register external recipes", e);
            }
        }

        @Nullable
        private RecipeHolder<?> parseRecipe(ResourceLocation id, JsonElement json) {
            try {
                // 解析配方对象
                var recipe = Recipe.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(JsonParseException::new);
                return new RecipeHolder<>(id, recipe);
            } catch (Exception e) {
                // 记录解析错误
                exceptions.add(e);
                errorFiles.add(id.toString());
                errorMessages.add(Component.translatable(e.getMessage()));
                LOGGER.error("An error occurred while reading recipe {}, skipped. Reason: {}", id, e.getMessage());
                return null;
            }
        }
    }

    public static void GenerateChannels(String jsCode) {
        createDefaultFile(PUBLIC_JS_LIBS.resolve("channels.js"), jsCode, overwrite);
    }

    /**
     * 自动生成测试包
     */
    private static void GenerateTestPack() {
        //拿到存在的路径
        Path examplePack = Exist(VEHICLES.resolve("example_pack"));
        Path modelFolder = Exist(examplePack.resolve("model"));
        Path animationFolder = Exist(examplePack.resolve("animation"));
        Path partTypeFolder = Exist(examplePack.resolve("part_type"));
        Path hudTypeFolder = Exist(examplePack.resolve("hud"));
        Path script = Exist(examplePack.resolve("script"));
        Path blueprint = Exist(examplePack.resolve("blueprint"));
        Path recipe = Exist(examplePack.resolve("recipe"));
        Path lang = Exist(examplePack.resolve("lang"));
        Path texture = Exist(examplePack.resolve("texture"));
        Path icon = Exist(examplePack.resolve("icon"));
        Path content = Exist(examplePack.resolve("content"));
        Path font = Exist(examplePack.resolve("font"));
        Path color = Exist(examplePack.resolve("color"));

        //设置默认测试包的路径、名字、内容
        //模型文件
        copyResourceToFile("/example_pack/model/example_hud.geo.json", modelFolder.resolve("example_hud.geo.json"), overwrite);
        copyResourceToFile("/example_pack/model/ae86_back_seat.geo.json", modelFolder.resolve("ae86_back_seat.geo.json"), overwrite);
        copyResourceToFile("/example_pack/model/ae86_seat.geo.json", modelFolder.resolve("ae86_seat.geo.json"), overwrite);
        copyResourceToFile("/example_pack/model/ae86_hull.geo.json", modelFolder.resolve("ae86_hull.geo.json"), overwrite);
        copyResourceToFile("/example_pack/model/ae86_chassis_all_terrain.geo.json", modelFolder.resolve("ae86_chassis_all_terrain.geo.json"), overwrite);
        copyResourceToFile("/example_pack/model/ae86_wheel_all_terrain_right.geo.json", modelFolder.resolve("ae86_wheel_all_terrain_right.geo.json"), overwrite);
        copyResourceToFile("/example_pack/model/ae86_wheel_all_terrain_left.geo.json", modelFolder.resolve("ae86_wheel_all_terrain_left.geo.json"), overwrite);
        copyResourceToFile("/example_pack/model/ae86_chassis.geo.json", modelFolder.resolve("ae86_chassis.geo.json"), overwrite);
        copyResourceToFile("/example_pack/model/ae86_wheel_right.geo.json", modelFolder.resolve("ae86_wheel_right.geo.json"), overwrite);
        copyResourceToFile("/example_pack/model/ae86_wheel_left.geo.json", modelFolder.resolve("ae86_wheel_left.geo.json"), overwrite);

        //动画文件
        copyResourceToFile("/example_pack/animation/example_hud.animation.json", animationFolder.resolve("example_hud.animation.json"), overwrite);
        copyResourceToFile("/example_pack/animation/ae86.animation.json", animationFolder.resolve("ae86.animation.json"), overwrite);

        //部件定义文件
        copyResourceToFile("/example_pack/part_type/ae86_back_seat.json", partTypeFolder.resolve("ae86_back_seat.json"), overwrite);
        copyResourceToFile("/example_pack/part_type/ae86_seat.json", partTypeFolder.resolve("ae86_seat.json"), overwrite);
        copyResourceToFile("/example_pack/part_type/ae86_hull.json", partTypeFolder.resolve("ae86_hull.json"), overwrite);
        copyResourceToFile("/example_pack/part_type/ae86_chassis_all_terrain.json", partTypeFolder.resolve("ae86_chassis_all_terrain.json"), overwrite);
        copyResourceToFile("/example_pack/part_type/ae86_wheel_all_terrain.json", partTypeFolder.resolve("ae86_wheel_all_terrain.json"), overwrite);
        copyResourceToFile("/example_pack/part_type/ae86_chassis.json", partTypeFolder.resolve("ae86_chassis.json"), overwrite);
        copyResourceToFile("/example_pack/part_type/ae86_wheel.json", partTypeFolder.resolve("ae86_wheel.json"), overwrite);

        //自定义HUD文件
        copyResourceToFile("/example_pack/hud/example_hud.json", hudTypeFolder.resolve("example_hud.json"), overwrite);

        //MM自带JS文件
        copyResourceToFile("/example_pack/script/main.js", script.resolve("main.js"), overwrite);

        //蓝图文件
        copyResourceToFile("/example_pack/blueprint/ae86.json", blueprint.resolve("ae86.json"), overwrite);
        copyResourceToFile("/example_pack/blueprint/ae86at.json", blueprint.resolve("ae86at.json"), overwrite);

        //配方文件
        copyResourceToFile("/example_pack/recipe/ae86_chassis.json", recipe.resolve("ae86_chassis.json"), overwrite);

        //自定义翻译
        copyResourceToFile("/example_pack/lang/zh_cn.json", lang.resolve("zh_cn.json"), overwrite);
        copyResourceToFile("/example_pack/lang/en_us.json", lang.resolve("en_us.json"), overwrite);

        //自带测试材质
        copyResourceToFile("/example_pack/texture/example_hud.png", texture.resolve("example_hud.png"), overwrite);
        copyResourceToFile("/example_pack/texture/ae86_1.png", texture.resolve("ae86_1.png"), overwrite);
        copyResourceToFile("/example_pack/texture/ae86_2.png", texture.resolve("ae86_2.png"), overwrite);
        copyResourceToFile("/example_pack/texture/ae86_3.png", texture.resolve("ae86_3.png"), overwrite);
        copyResourceToFile("/example_pack/texture/ae86_4.png", texture.resolve("ae86_4.png"), overwrite);
        copyResourceToFile("/example_pack/texture/ae86_1.png", texture.resolve("ae86_5.png"), overwrite);
        copyResourceToFile("/example_pack/texture/ae86_2.png", texture.resolve("ae86_6.png"), overwrite);
        copyResourceToFile("/example_pack/texture/ae86_3.png", texture.resolve("ae86_7.png"), overwrite);
        copyResourceToFile("/example_pack/texture/ae86_4.png", texture.resolve("ae86_8.png"), overwrite);
        copyResourceToFile("/example_pack/texture/ae86_4.png", texture.resolve("ae86_9.png"), overwrite);
        copyResourceToFile("/example_pack/texture/ae86_all_terrain_1.png", texture.resolve("ae86_all_terrain_1.png"), overwrite);
        copyResourceToFile("/example_pack/texture/ae86_all_terrain_2.png", texture.resolve("ae86_all_terrain_2.png"), overwrite);
        copyResourceToFile("/example_pack/texture/ae86_all_terrain_3.png", texture.resolve("ae86_all_terrain_3.png"), overwrite);
        copyResourceToFile("/example_pack/texture/ae86_all_terrain_4.png", texture.resolve("ae86_all_terrain_4.png"), overwrite);

        //自带测试图标
        copyResourceToFile("/example_pack/icon/ae86_back_seat_icon.png", icon.resolve("ae86_back_seat_icon.png"), overwrite);
        copyResourceToFile("/example_pack/icon/ae86_seat_icon.png", icon.resolve("ae86_seat_icon.png"), overwrite);
        copyResourceToFile("/example_pack/icon/ae86_chassis_all_terrain_icon.png", icon.resolve("ae86_chassis_all_terrain_icon.png"), overwrite);
        copyResourceToFile("/example_pack/icon/ae86_wheel_all_terrain_icon.png", icon.resolve("ae86_wheel_all_terrain_icon.png"), overwrite);
        copyResourceToFile("/example_pack/icon/ae86_chassis_icon.png", icon.resolve("ae86_chassis_icon.png"), overwrite);
        copyResourceToFile("/example_pack/icon/ae86_wheel_icon.png", icon.resolve("ae86_wheel_icon.png"), overwrite);
        copyResourceToFile("/example_pack/icon/ae86_hull_icon.png", icon.resolve("ae86_hull_icon.png"), overwrite);
        copyResourceToFile("/example_pack/icon/ae86_icon.png", icon.resolve("ae86_icon.png"), overwrite);
        copyResourceToFile("/example_pack/icon/ae86at_icon.png", icon.resolve("ae86at_icon.png"), overwrite);

        //自定义文本文件
        copyResourceToFile("/example_pack/content/ae86.html", content.resolve("ae86.html"), overwrite);
        copyResourceToFile("/example_pack/content/ae86at.html", content.resolve("ae86at.html"), overwrite);

        //自定义字体文件
        copyResourceToFile("/example_pack/font/test_font.json", font.resolve("test_font.json"), overwrite);
        copyResourceToFile("/example_pack/font/yahei.json", font.resolve("yahei.json"), overwrite);
        copyResourceToFile("/example_pack/font/yahei.ttf", font.resolve("yahei.ttf"), overwrite);
        copyResourceToFile("/example_pack/font/bell.ttf", font.resolve("bell.ttf"), overwrite);
        copyResourceToFile("/example_pack/font/bellb.ttf", font.resolve("bellb.ttf"), overwrite);
        copyResourceToFile("/example_pack/font/belli.ttf", font.resolve("belli.ttf"), overwrite);

        //自定义色板
        copyResourceToFile("/example_pack/color/color_palette.json", color.resolve("color_palette.json"), overwrite);

    }


    /**
     * 对一个载具包子目录的解析 packName是载具包名称 categoryPath是子目录
     */
    private static void packUp(String packName, Path categoryPath) {
        String category = categoryPath.getFileName().toString();
        for (Path filePath : listAllFiles(categoryPath)) {
            DynamicPack dynamicPack = null;
            String fileName = filePath.getFileName().toString();
            String fileRealName = getRealName(fileName);
            String relativePath = categoryPath.relativize(filePath).toString().replace("\\", "/").toLowerCase();
            ResourceLocation location = ResourceLocation.tryBuild(MOD_ID, "%s/%s/%s".formatted(packName.toLowerCase(), category, relativePath));
            try (JsonReader reader = new JsonReader(new FileReader(filePath.toFile()))) {
                reader.setLenient(true); // 允许非严格JSON
                JsonElement json = JsonParser.parseReader(reader);
                switch (category) {

                    case "model" -> {
                        OBoneParse.register(location, json);
                    }

                    case "animation" -> {
                        OAnimationSet animSet = OAnimationSet.getCODEC().parse(JsonOps.INSTANCE, json).result().orElseThrow();
                        OAnimationSet.getORIGINS().put(location, animSet);
                    }

                    case "part_type" -> { //part_type文件夹中的配置
                        PartType partType = PartType.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
                        location = partType.registryKey;
                        PART_TYPES.put(location, partType); //我暂时把它存在PART_TYPES
                        partType = PartType.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
                        SERVER_PART_TYPES.put(location, partType);
                    }

                    case "script" -> {
                        dynamicPack = new DynamicPack(packName, location, category, filePath.toFile());
                        MM_SCRIPTS.put(location, dynamicPack);
                    }

                    case "blueprint" -> {
                        VehicleData data = VehicleData.CODEC.decode(JsonOps.INSTANCE, json).result().orElseThrow().getFirst();
                        BLUEPRINTS.put(location, data);
                    }

                    case "hud" -> {
                        RenderableAttr attr = RenderableAttr.CODEC.decode(JsonOps.INSTANCE, json).result().orElseThrow().getFirst();
                        CUSTOM_HUD.put(location, attr);
                    }

                    case "recipe" -> {
                        CRAFTING_RECIPES.add(Pair.of(location, json));
                    }

                    case "lang" -> {
                        location = ResourceLocation.tryBuild(MOD_ID, "%s/%s".formatted(category, fileName)); //语言翻译系统的标准搜索路径
                        if (EXTERNAL_RESOURCE.containsKey(location)) {
                            //已经有该语言的.json翻译表，需要往里面注入
                            DynamicPack existedPack = EXTERNAL_RESOURCE.get(location);

                            try {
                                // 解析JSON字符串为JsonObject
                                JsonObject origin = JsonParser.parseString(existedPack.getContent()).getAsJsonObject();
                                JsonObject mergeIn = json.getAsJsonObject();

                                // 合并JsonObject（后者覆盖前者）
                                JsonObject merged = new JsonObject();
                                mergeJsonObjects(merged, origin);
                                mergeJsonObjects(merged, mergeIn);

                                // 转换为合并后的JSON字符串
                                String mergedJson = new GsonBuilder().setPrettyPrinting().create().toJson(merged);

                                // 保存到合并后的JSON到资源覆写
                                dynamicPack = new DynamicPack(packName, location, category, mergedJson);

                            } catch (JsonSyntaxException | IllegalStateException e) {
                                LOGGER.error("合并相同翻译表 {}时失败 目标文件位于外部包{}: {}", category, packName, e.getMessage());
                            }
                        }
                    }

                    case "font" -> {
                        location = ResourceLocation.tryBuild(MOD_ID, "%s/%s".formatted(category, fileName)); //字体系统的标准搜索路径
                    }

                    case "color" -> {
                        COLORS.put(location, json);
                    }

                }
                if (location == null) {
                    throw new IllegalArgumentException("error.machine_max.invalid_resource_location");
                }
                if (dynamicPack == null)
                    dynamicPack = new DynamicPack(packName, location, category, filePath.toFile());//生成动态包（这里保留的目的是一般拿来注入材质包和模型、动画，part-type却不能用要单独实现）
                EXTERNAL_RESOURCE.put(location, dynamicPack);//保存动态包，后续会被addPackEvent读取、注册
            } catch (Exception e) {
                exceptions.add(e);
                errorFiles.add(filePath.toString());
                errorMessages.add(Component.translatable(e.getMessage()));
                LOGGER.error("An error occurred while reading {}, file: {}, skipped. Reason: {}", category, filePath, e.getMessage());
            }

//            //把指定包的文件转换成base64字符串形式    取消注释则会生成镜像base64文件包 在 run/config/machine_max/base64ify
//            Path master_base64ify = Exist(NAMESPACE.resolve("base64ify"));
//            Path root_base64ify = Exist(master_base64ify.resolve(packName+"_base64"));
//            Path category_base64ify = Exist(root_base64ify.resolve(category));
//            createDefaultFile(category_base64ify.resolve(fileName+"_base64.txt"), dynamicPack.getBase64(), true);
        }

    }

    private static void mergeJsonObjects(JsonObject target, JsonObject source) {
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            target.add(entry.getKey(), entry.getValue());
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


}
