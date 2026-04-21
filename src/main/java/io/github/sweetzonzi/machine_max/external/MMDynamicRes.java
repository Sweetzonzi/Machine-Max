package io.github.sweetzonzi.machine_max.external;

import com.google.gson.JsonElement;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.BlueprintResearchRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.ResearchRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.registry.MMResources;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.MaterialAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.connector.ConnectorStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.AbstractSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.data.AssemblyData;
import io.github.sweetzonzi.machine_max.common.vehicle.data.BlueprintData;
import io.github.sweetzonzi.machine_max.common.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.common.visual.AnimatableParams;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MMDynamicRes {
    public static ConcurrentMap<ResourceLocation, MaterialAttr> MATERIALS = new ConcurrentHashMap<>();
    public static ConcurrentMap<ResourceLocation, ConnectorStaticAttr> CONNECTORS = new ConcurrentHashMap<>();
    public static ConcurrentMap<ResourceLocation, PartType> PART_TYPES = new ConcurrentHashMap<>(); // key是自带构造函数生成的registryKey， value是暂存的PartType
    public static ConcurrentMap<ResourceLocation, PartType> SERVER_PART_TYPES = new ConcurrentHashMap<>(); // key是自带构造函数生成的registryKey， value是暂存的PartType
    public static ConcurrentMap<ResourceLocation, AbstractSubsystemStaticAttr> STATIC_SUBSYSTEM_ATTRS = new ConcurrentHashMap<>(); // 静态的子系统属性，所有子系统实例共享，表示单一型号如某型发动机
    public static ConcurrentMap<ResourceLocation, AbstractSubsystemStaticAttr> SERVER_STATIC_SUBSYSTEM_ATTRS = new ConcurrentHashMap<>(); // 静态的子系统属性，所有子系统实例共享，表示单一型号如某型发动机
    public static ConcurrentMap<ResourceLocation, VehicleData> TEMPLATES = new ConcurrentHashMap<>(); // 已组装的结构数据
    public static ConcurrentMap<ResourceLocation, BlueprintData> BLUEPRINTS = new ConcurrentHashMap<>(); // 蓝图数据
    public static ConcurrentMap<ResourceLocation, AssemblyData> ASSEMBLIES = new ConcurrentHashMap<>(); // 装配体数据
    public static ConcurrentMap<ResourceLocation, String> TOOLTIPS = new ConcurrentHashMap<>(); //蓝图或装配体物品对应的描述信息
    public static ConcurrentMap<ResourceLocation, AnimatableParams> CUSTOM_HUD = new ConcurrentHashMap<>(); // 自定义HUD配置文件
    public static HashMap<ResourceLocation, LinkedHashSet<RecipeHolder<FabricatingRecipe>>> PART_RECIPES = new HashMap<>(); // 零件配方
    public static HashMap<ResourceLocation, RecipeHolder<FabricatingRecipe>> ALL_FABRICATING_RECIPES = new HashMap<>(); // 所有制造配方
    public static HashMap<ResourceLocation, RecipeHolder<ResearchRecipe>> ALL_RESEARCH_RECIPES = new HashMap<>(); // 所有研发配方
    public static HashMap<ResourceLocation, RecipeHolder<BlueprintResearchRecipe>> BLUEPRINT_RESEARCH_RECIPES = new HashMap<>(); // 蓝图研发配方
    public static ConcurrentMap<ResourceLocation, JsonElement> COLORS = new ConcurrentHashMap<>(); // 读取为自定义色彩合集 key注册路径， value是该文件的JsonElement对象

    public static List<Exception> exceptions = new ArrayList<>(); // 读取过程中出现的异常
    public static List<String> errorFiles = new ArrayList<>(); // 读取过程中出现错误的文件
    public static List<MutableComponent> errorMessages = new ArrayList<>(); // 读取过程中出现错误的提示信息

    public static void reload() {
        initResources();
    }

    public static void initResources() {
        exceptions.clear();
        errorFiles.clear();
        errorMessages.clear();
    }

 @EventBusSubscriber
    public static class DataPackReloader extends SimplePreparableReloadListener<Set<FabricatingRecipe>> {
        private static ReloadableServerResources serverResources = null;
        @Override
        protected Set<FabricatingRecipe> prepare(ResourceManager manager, ProfilerFiller profiler) {
            MMDynamicRes.reload();//异步重新读取资源
            MMDynamicRes.PART_RECIPES.clear();
            MMDynamicRes.ALL_FABRICATING_RECIPES.clear();
            MMDynamicRes.ALL_RESEARCH_RECIPES.clear();
            MMDynamicRes.BLUEPRINT_RESEARCH_RECIPES.clear();
            return Set.of();
        }

        @Override
        protected void apply(Set<FabricatingRecipe> recipes, ResourceManager manager, ProfilerFiller profiler) {
            if (serverResources!= null) {
                int count = 0;
                RecipeManager recipeManager = serverResources.getRecipeManager();
                var fabricatingRecipes = recipeManager.getAllRecipesFor(MMResources.getFABRICATION_RECIPE_TYPE().get());
                for (RecipeHolder<FabricatingRecipe> recipeHolder : fabricatingRecipes) {
                    MMDynamicRes.ALL_FABRICATING_RECIPES.put(recipeHolder.id(), recipeHolder);
                    FabricatingRecipe recipe = recipeHolder.value();
                    ItemStack stack = recipe.getResultItem(serverResources.getRegistryLookup());
                    if (stack.has(MMDataComponents.getPART_TYPE())) {
                        ResourceLocation partType = stack.get(MMDataComponents.getPART_TYPE());
                        PART_RECIPES.computeIfAbsent(partType, k -> new LinkedHashSet<>()).add(recipeHolder);
                        count++;
                    }
                }

                var researchRecipes = recipeManager.getAllRecipesFor(MMResources.getRESEARCH_RECIPE_TYPE().get());
                for (RecipeHolder<ResearchRecipe> recipeHolder : researchRecipes) {
                    MMDynamicRes.ALL_RESEARCH_RECIPES.put(recipeHolder.id(), recipeHolder);
                }

                var blueprintResearchRecipes = recipeManager.getAllRecipesFor(MMResources.getBLUEPRINT_RESEARCH_RECIPE_TYPE().get());
                for (RecipeHolder<BlueprintResearchRecipe> recipeHolder : blueprintResearchRecipes) {
                    MMDynamicRes.BLUEPRINT_RESEARCH_RECIPES.put(recipeHolder.id(), recipeHolder);
                    MMDynamicRes.ALL_RESEARCH_RECIPES.put(recipeHolder.id(), (RecipeHolder<ResearchRecipe>) (RecipeHolder<?>) recipeHolder);
                }
//                LOGGER.debug("从服务器数据为{}种个零件配方添加了{}种配方", PART_RECIPES.size(), count);
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void register(AddReloadListenerEvent event) {
            event.addListener(new DataPackReloader());
            DataPackReloader.serverResources = event.getServerResources();
        }

    }

    public static void sendErrorToPlayer(Player player) {
        for (String file : errorFiles) {
            int i = errorFiles.indexOf(file);
            MutableComponent message = errorMessages.get(i).withColor(Color.RED.getRGB());
            player.sendSystemMessage(Component.translatable("error.machine_max.load", file).withColor(Color.WHITE.getRGB()).append(message));
        }
    }

    public static void sendErrorToConsole(MinecraftServer server) {
        for (String file : errorFiles) {
            int i = errorFiles.indexOf(file);
            Component message = MMDynamicRes.errorMessages.get(i);
            server.sendSystemMessage(Component.translatable("error.machine_max.load", file).append(message).withColor(Color.red.getRGB()));
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
}

