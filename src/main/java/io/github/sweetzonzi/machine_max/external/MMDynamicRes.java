package io.github.sweetzonzi.machine_max.external;

import com.google.gson.JsonElement;
import io.github.sweetzonzi.machine_max.common.recipe.BlueprintResearchRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.PartFabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.ResearchRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMResources;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.MaterialAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.ConnectorStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.AssemblyData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.common.visual.HudAttr;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MMDynamicRes {
    public static ConcurrentMap<ResourceLocation, MaterialAttr> MATERIALS = new ConcurrentHashMap<>();
    public static ConcurrentMap<ResourceLocation, ConnectorStaticAttr> CONNECTORS = new ConcurrentHashMap<>();
    public static ConcurrentMap<ResourceLocation, PartType> PART_TYPES = new ConcurrentHashMap<>(); // key是自带构造函数生成的registryKey， value是暂存的PartType
    public static ConcurrentMap<ResourceLocation, PartType> SERVER_PART_TYPES = new ConcurrentHashMap<>(); // key是自带构造函数生成的registryKey， value是暂存的PartType
    public static ConcurrentMap<ResourceLocation, ProjectileType> PROJECTILE_TYPES = new ConcurrentHashMap<>();
    public static ConcurrentMap<ResourceLocation, ProjectileType> SERVER_PROJECTILE_TYPES = new ConcurrentHashMap<>();
    public static ConcurrentMap<ResourceLocation, AbstractSubsystemStaticAttr> STATIC_SUBSYSTEM_ATTRS = new ConcurrentHashMap<>(); // 静态的子系统属性，所有子系统实例共享，表示单一型号如某型发动机
    public static ConcurrentMap<ResourceLocation, AbstractSubsystemStaticAttr> SERVER_STATIC_SUBSYSTEM_ATTRS = new ConcurrentHashMap<>(); // 静态的子系统属性，所有子系统实例共享，表示单一型号如某型发动机
    public static ConcurrentMap<ResourceLocation, VehicleData> TEMPLATES = new ConcurrentHashMap<>(); // 已组装的结构数据
    public static ConcurrentMap<ResourceLocation, BlueprintData> BLUEPRINTS = new ConcurrentHashMap<>(); // 蓝图数据
    public static ConcurrentMap<ResourceLocation, AssemblyData> ASSEMBLIES = new ConcurrentHashMap<>(); // 装配体数据
    public static ConcurrentMap<ResourceLocation, String> TOOLTIPS = new ConcurrentHashMap<>(); //蓝图或装配体物品对应的描述信息
    public static ConcurrentMap<ResourceLocation, HudAttr> CUSTOM_HUD = new ConcurrentHashMap<>(); // 自定义HUD配置文件
    public static ConcurrentMap<ResourceLocation, ControlGroupSet> CONTROL_GROUP_PRESETS = new ConcurrentHashMap<>(); // 控制组预设

    // ==================== 客户端制造索引（仅客户端容器） ====================
    /** 全配方索引：通用制造配方全部 + 通过校验的零件配方，供 JEI、制造机列表、研发反查使用 */
    public static HashMap<ResourceLocation, RecipeHolder<FabricatingRecipe>> ALL_FABRICATING_RECIPES = new HashMap<>();
    /** 零件配方全集，键为配方 id */
    public static HashMap<ResourceLocation, RecipeHolder<PartFabricatingRecipe>> ALL_PART_FABRICATING_RECIPES = new HashMap<>();
    /** 装配侧索引：键为零件 id，值为该零件唯一的零件配方 */
    public static HashMap<ResourceLocation, RecipeHolder<PartFabricatingRecipe>> PART_RECIPES = new HashMap<>();

    // ==================== 服务端制造索引（SERVER_ 前缀） ====================
    public static HashMap<ResourceLocation, RecipeHolder<FabricatingRecipe>> SERVER_ALL_FABRICATING_RECIPES = new HashMap<>();
    public static HashMap<ResourceLocation, RecipeHolder<PartFabricatingRecipe>> SERVER_ALL_PART_FABRICATING_RECIPES = new HashMap<>();
    public static HashMap<ResourceLocation, RecipeHolder<PartFabricatingRecipe>> SERVER_PART_RECIPES = new HashMap<>();
    /** 零件 id -> 研发配方 id，供装配门禁按零件反查 */
    public static HashMap<ResourceLocation, ResourceLocation> SERVER_RESEARCH_BY_PART = new HashMap<>();
    /** 制造配方 id -> 研发配方 id，供研发配方反查 */
    public static HashMap<ResourceLocation, ResourceLocation> SERVER_RESEARCH_BY_FABRICATING_RECIPE = new HashMap<>();

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

    // ==================== 制造索引的读取入口 ====================

    /**
     * 取本侧全配方索引（通用制造配方全部 + 通过校验的零件配方）。
     *
     * <p>制造机配方列表、JEI 配方注册、研发界面与 tooltip 的配方反查都经此入口，
     * 内部按 {@code level.isClientSide()} 在客户端容器与服务端容器之间选择。</p>
     *
     * @param level 用于判定逻辑侧
     * @return 本侧全配方索引，键为配方 id；未就绪时为只读空表
     */
    public static Map<ResourceLocation, RecipeHolder<FabricatingRecipe>> getAllFabricating(Level level) {
        return level.isClientSide() ? ALL_FABRICATING_RECIPES : SERVER_ALL_FABRICATING_RECIPES;
    }

    /**
     * 按零件 id 取本侧唯一的零件配方。
     *
     * @param level    用于判定逻辑侧
     * @param partType 零件 id
     * @return 该零件的零件配方；该零件没有零件配方或索引未就绪时返回 {@code null}
     */
    @Nullable
    public static RecipeHolder<PartFabricatingRecipe> getPartRecipe(Level level, ResourceLocation partType) {
        return level.isClientSide() ? PART_RECIPES.get(partType) : SERVER_PART_RECIPES.get(partType);
    }

    // ==================== 制造索引的构建 ====================

    /**
     * 整体重建指定逻辑侧的制造索引（主线程）。
     *
     * <p>本方法先清空本侧三个容器，再按「先通用制造配方、后零件配方」的顺序填充：零件配方在填充时完成
     * 装载期校验、{@code part_type} 注入与产物组件补齐。校验失败的零件配方不进入任何容器，其产物停留在
     * 占位形态。</p>
     *
     * <p>零件存在性与产物数量上限依赖零件类型注册表，因此需要传入本侧的注册表；注册表为空时跳过
     * 「零件存在性」校验（该情形只出现在装载顺序未就绪的窗口内）。</p>
     *
     * @param recipeManager 数据源
     * @param serverSide    true 表示重建服务端容器，false 表示重建客户端容器
     */
    public static void rebuildFabricatingIndex(RecipeManager recipeManager, boolean serverSide) {
        Map<ResourceLocation, RecipeHolder<FabricatingRecipe>> all = serverSide
                ? SERVER_ALL_FABRICATING_RECIPES : ALL_FABRICATING_RECIPES;
        Map<ResourceLocation, RecipeHolder<PartFabricatingRecipe>> allPart = serverSide
                ? SERVER_ALL_PART_FABRICATING_RECIPES : ALL_PART_FABRICATING_RECIPES;
        Map<ResourceLocation, RecipeHolder<PartFabricatingRecipe>> partByPart = serverSide
                ? SERVER_PART_RECIPES : PART_RECIPES;
        ConcurrentMap<ResourceLocation, PartType> partRegistry = serverSide ? SERVER_PART_TYPES : PART_TYPES;

        all.clear();
        allPart.clear();
        partByPart.clear();

        // 通用制造配方：目录不限，但不得占用零件配方的目录
        for (RecipeHolder<FabricatingRecipe> holder : recipeManager.getAllRecipesFor(
                MMResources.getFABRICATION_RECIPE_TYPE().get())) {
            if (holder.id().getPath().startsWith(PartFabricatingRecipe.DIRECTORY_PREFIX)) {
                recordIfServer(serverSide, holder.id(), Component.translatable("error.machine_max.recipe.wrong_type_directory"));
                continue;
            }
            all.put(holder.id(), holder);
        }

        // 零件配方：目录、类型、零件存在性、装配侧唯一性与产物数量逐一校验
        for (RecipeHolder<PartFabricatingRecipe> holder : recipeManager.getAllRecipesFor(
                MMResources.getPART_FABRICATION_RECIPE_TYPE().get())) {
            PartFabricatingRecipe recipe = holder.value();
            if (!holder.id().getPath().startsWith(PartFabricatingRecipe.DIRECTORY_PREFIX)) {
                recordIfServer(serverSide, holder.id(), Component.translatable("error.machine_max.recipe.wrong_directory"));
                continue;
            }
            ResourceLocation partType = PartFabricatingRecipe.partTypeFromRecipeId(holder.id());
            if (!partRegistry.isEmpty() && !partRegistry.containsKey(partType)) {
                recordIfServer(serverSide, holder.id(), Component.translatable("error.machine_max.recipe.unknown_part"));
                continue;
            }
            PartType type = partRegistry.get(partType);
            if (type != null && (recipe.getResultCount() < 1 || recipe.getResultCount() > type.getMaxStackSize())) {
                recordIfServer(serverSide, holder.id(), Component.translatable("error.machine_max.recipe.invalid_result_count"));
                continue;
            }
            if (partByPart.containsKey(partType)) {
                recordIfServer(serverSide, holder.id(), Component.translatable("error.machine_max.recipe.duplicate_part"));
                continue;
            }
            // 注入零件 id 与产物组件；零件缺失时无法取得堆叠上限，按保守值处理
            recipe.setResolvedProduct(partType, type != null ? type.getMaxStackSize() : 1);
            allPart.put(holder.id(), holder);
            partByPart.put(partType, holder);
            all.put(holder.id(), (RecipeHolder<FabricatingRecipe>) (RecipeHolder<?>) holder);
        }
    }

    /** 只有服务端记录装载错误，避免客户端重复提示 */
    private static void recordIfServer(boolean serverSide, ResourceLocation recipeId, Component message) {
        if (!serverSide) return;
        errorFiles.add(recipeId.toString());
        errorMessages.add(message.copy());
    }

    @EventBusSubscriber
    public static class DataPackReloader extends SimplePreparableReloadListener<Set<FabricatingRecipe>> {
        private static ReloadableServerResources serverResources = null;

        @Override
        protected Set<FabricatingRecipe> prepare(ResourceManager manager, ProfilerFiller profiler) {
            MMDynamicRes.reload();//异步重新读取资源
            return Set.of();
        }

        @Override
        protected void apply(Set<FabricatingRecipe> recipes, ResourceManager manager, ProfilerFiller profiler) {
            if (serverResources == null) return;
            RecipeManager recipeManager = serverResources.getRecipeManager();
            // 制造索引：清空与填充同一阶段完成
            rebuildFabricatingIndex(recipeManager, true);

            // 研发索引
            SERVER_RESEARCH_BY_PART.clear();
            SERVER_RESEARCH_BY_FABRICATING_RECIPE.clear();
            ALL_RESEARCH_RECIPES.clear();
            BLUEPRINT_RESEARCH_RECIPES.clear();

            for (RecipeHolder<ResearchRecipe> recipeHolder : recipeManager.getAllRecipesFor(
                    MMResources.getRESEARCH_RECIPE_TYPE().get())) {
                MMDynamicRes.ALL_RESEARCH_RECIPES.put(recipeHolder.id(), recipeHolder);
            }

            for (RecipeHolder<BlueprintResearchRecipe> recipeHolder : recipeManager.getAllRecipesFor(
                    MMResources.getBLUEPRINT_RESEARCH_RECIPE_TYPE().get())) {
                MMDynamicRes.BLUEPRINT_RESEARCH_RECIPES.put(recipeHolder.id(), recipeHolder);
                MMDynamicRes.ALL_RESEARCH_RECIPES.put(recipeHolder.id(), (RecipeHolder<ResearchRecipe>) (RecipeHolder<?>) recipeHolder);
                ResourceLocation unlockRecipe = recipeHolder.value().getUnlockRecipe();
                // 反向索引：制造配方 id -> 研发配方 id
                MMDynamicRes.SERVER_RESEARCH_BY_FABRICATING_RECIPE.put(unlockRecipe, recipeHolder.id());
                // 正向索引：零件 id -> 研发配方 id，供装配门禁按零件反查
                RecipeHolder<PartFabricatingRecipe> partRecipe = SERVER_ALL_PART_FABRICATING_RECIPES.get(unlockRecipe);
                if (partRecipe != null && partRecipe.value().getPartType() != null) {
                    MMDynamicRes.SERVER_RESEARCH_BY_PART.put(partRecipe.value().getPartType(), recipeHolder.id());
                }
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void register(AddReloadListenerEvent event) {
            event.addListener(new DataPackReloader());
            DataPackReloader.serverResources = event.getServerResources();
        }
    }

    /**
     * 清空指定逻辑侧的制造索引（客户端退出服务器时调用，避免残留上一个服务器的配方）。
     *
     * @param serverSide true 清服务端容器
     */
    public static void clearFabricatingIndex(boolean serverSide) {
        if (serverSide) {
            SERVER_ALL_FABRICATING_RECIPES.clear();
            SERVER_ALL_PART_FABRICATING_RECIPES.clear();
            SERVER_PART_RECIPES.clear();
        } else {
            ALL_FABRICATING_RECIPES.clear();
            ALL_PART_FABRICATING_RECIPES.clear();
            PART_RECIPES.clear();
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
