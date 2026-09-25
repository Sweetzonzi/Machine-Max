package io.github.sweetzonzi.machine_max.external;

import com.google.gson.JsonElement;
import io.github.sweetzonzi.machine_max.MachineMax;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
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

    // ==================== 研发分组与依赖层级索引 ====================
    /** 未声明任何组的研发项目归入的组 */
    public static final ResourceLocation MISC_GROUP = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "misc");
    /** 组 id -> 该组成员的研发 id，按 id 字母序；组本身按 id 字母序排列 */
    public static Map<ResourceLocation, List<ResourceLocation>> GROUP_MEMBERS = new LinkedHashMap<>();
    /** 组 id -> (研发 id -> 该条目在该组内的依赖层级)；不可达条目按根层记录 */
    public static Map<ResourceLocation, Map<ResourceLocation, NodeDepth>> NODE_DEPTH = new LinkedHashMap<>();
    /** 不可达的研发 id -> 原因；不在表中的条目即全局可达 */
    public static Map<ResourceLocation, UnreachableResearch> UNREACHABLE_RESEARCH = new LinkedHashMap<>();

    /**
     * 一个研发条目在某个组内的依赖层级。
     *
     * @param depth                     依赖层级，组根层为 0
     * @param primaryPrerequisite       主前置（嵌套在其之下）；无组内前置时为 {@code null}
     * @param additionalPrerequisites   除主前置外还需完成的前置数量（组内其余前置 + 组外前置）
     */
    public record NodeDepth(int depth, ResourceLocation primaryPrerequisite, int additionalPrerequisites) {
    }

    /** 研发条目不可达的成因 */
    public enum UnreachableCause {
        /** 项目把自己列为前置 */
        SELF_PREREQUISITE,
        /** 位于依赖环上 */
        ON_CYCLE,
        /** 位于依赖环下游，所依赖的项目不可达 */
        DOWNSTREAM_OF_UNREACHABLE,
        /** 前置引用了未注册的研发 id */
        DANGLING_PREREQUISITE
    }

    /**
     * 研发条目不可达的判定结果。
     *
     * @param cause    成因
     * @param culprits 导致不可达的前置 id；自环时为自己
     */
    public record UnreachableResearch(UnreachableCause cause, List<ResourceLocation> culprits) {
    }
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

    // ==================== 研发索引与分组依赖层级的构建 ====================

    /** 全部索引排序与遍历起点的统一顺序：按 id 字符串字母序 */
    private static final Comparator<ResourceLocation> ID_ORDER = Comparator.comparing(ResourceLocation::toString);

    /**
     * 整体重建指定逻辑侧的研发索引与研发分组依赖索引（主线程）。
     *
     * <p>先收集研发配方，再在全局图上判定可达成性与依赖环，最后在每个组内分层。分组与依赖相关
     * 的校验项经 {@code recordIfServer} 上报，只有服务端记录。</p>
     *
     * @param recipeManager 数据源
     * @param serverSide    true 表示重建服务端索引
     */
    public static void rebuildResearchIndex(RecipeManager recipeManager, boolean serverSide) {
        ALL_RESEARCH_RECIPES.clear();
        BLUEPRINT_RESEARCH_RECIPES.clear();
        if (serverSide) {
            SERVER_RESEARCH_BY_PART.clear();
            SERVER_RESEARCH_BY_FABRICATING_RECIPE.clear();
        }

        for (RecipeHolder<ResearchRecipe> recipeHolder : recipeManager.getAllRecipesFor(
                MMResources.getRESEARCH_RECIPE_TYPE().get())) {
            ALL_RESEARCH_RECIPES.put(recipeHolder.id(), recipeHolder);
        }

        for (RecipeHolder<BlueprintResearchRecipe> recipeHolder : recipeManager.getAllRecipesFor(
                MMResources.getBLUEPRINT_RESEARCH_RECIPE_TYPE().get())) {
            BLUEPRINT_RESEARCH_RECIPES.put(recipeHolder.id(), recipeHolder);
            ALL_RESEARCH_RECIPES.put(recipeHolder.id(), (RecipeHolder<ResearchRecipe>) (RecipeHolder<?>) recipeHolder);
            if (!serverSide) continue;
            ResourceLocation unlockRecipe = recipeHolder.value().getUnlockRecipe();
            // 反向索引：制造配方 id -> 研发配方 id
            SERVER_RESEARCH_BY_FABRICATING_RECIPE.put(unlockRecipe, recipeHolder.id());
            // 正向索引：零件 id -> 研发配方 id，供装配门禁按零件反查
            RecipeHolder<PartFabricatingRecipe> partRecipe = SERVER_ALL_PART_FABRICATING_RECIPES.get(unlockRecipe);
            if (partRecipe != null && partRecipe.value().getPartType() != null) {
                SERVER_RESEARCH_BY_PART.put(partRecipe.value().getPartType(), recipeHolder.id());
            }
        }

        buildResearchGraph(serverSide);
    }

    /**
     * 重建分组索引、全局可达成性与组内依赖层级。
     *
     * <p>判定分两个阶段：先在全局图上做正向传播求可达成集合、用强连通分量提取依赖环，
     * 再在每个组内做拓扑分层与主前置定案。</p>
     *
     * @param serverSide true 表示将校验结果记入错误上报通道
     */
    private static void buildResearchGraph(boolean serverSide) {
        GROUP_MEMBERS.clear();
        NODE_DEPTH.clear();
        UNREACHABLE_RESEARCH.clear();

        List<ResourceLocation> allIds = sortedIds(ALL_RESEARCH_RECIPES.keySet());
        Set<ResourceLocation> registered = new HashSet<>(allIds);

        // 前置列表去重（保持声明顺序），自环保留
        Map<ResourceLocation, List<ResourceLocation>> prerequisites = new LinkedHashMap<>();
        for (ResourceLocation id : allIds) {
            ResearchRecipe recipe = ALL_RESEARCH_RECIPES.get(id).value();
            prerequisites.put(id, new ArrayList<>(new LinkedHashSet<>(recipe.getPrerequisites())));
        }
        // 已注册前置构成的边集：前置 -> 依赖它的研发 id
        Map<ResourceLocation, List<ResourceLocation>> successors = new HashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> registeredPrerequisites = new HashMap<>();
        for (ResourceLocation id : allIds) {
            List<ResourceLocation> known = new ArrayList<>();
            for (ResourceLocation prerequisite : prerequisites.get(id)) {
                if (!registered.contains(prerequisite)) continue;
                known.add(prerequisite);
                successors.computeIfAbsent(prerequisite, k -> new ArrayList<>()).add(id);
            }
            registeredPrerequisites.put(id, known);
        }

        // 阶段一之一：正向传播求可达成集合。判据是「前置全部已达」——悬空前置与环上的节点
        // 永远等不到前置齐全，因而整块留在可达集合之外。
        Set<ResourceLocation> reachable = new HashSet<>();
        Map<ResourceLocation, Integer> pending = new HashMap<>();
        PriorityQueue<ResourceLocation> ready = new PriorityQueue<>(ID_ORDER);
        for (ResourceLocation id : allIds) {
            int count = prerequisites.get(id).size();
            pending.put(id, count);
            if (count == 0) ready.add(id);
        }
        while (!ready.isEmpty()) {
            ResourceLocation id = ready.poll();
            reachable.add(id);
            for (ResourceLocation dependent : successors.getOrDefault(id, List.of())) {
                if (pending.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }

        // 阶段一之二：强连通分量提取依赖环。分量成员与遍历起点无关。
        List<List<ResourceLocation>> components = stronglyConnectedComponents(allIds, successors, registeredPrerequisites);
        Set<ResourceLocation> onCycle = new HashSet<>();
        for (List<ResourceLocation> component : components) {
            boolean selfLoop = component.size() == 1 && prerequisites.get(component.get(0)).contains(component.get(0));
            if (component.size() >= 2 || selfLoop) {
                onCycle.addAll(component);
            }
        }

        // 不可达归类：按 自环 > 环成员 > 环下游 > 悬空前置 取第一条
        for (ResourceLocation id : allIds) {
            if (reachable.contains(id)) continue;
            List<ResourceLocation> own = prerequisites.get(id);
            if (own.contains(id)) {
                UNREACHABLE_RESEARCH.put(id, new UnreachableResearch(UnreachableCause.SELF_PREREQUISITE, List.of(id)));
                continue;
            }
            if (onCycle.contains(id)) {
                UNREACHABLE_RESEARCH.put(id, new UnreachableResearch(UnreachableCause.ON_CYCLE, List.of()));
                continue;
            }
            List<ResourceLocation> blocking = new ArrayList<>();
            List<ResourceLocation> dangling = new ArrayList<>();
            for (ResourceLocation prerequisite : own) {
                if (reachable.contains(prerequisite)) continue;
                if (registered.contains(prerequisite)) {
                    blocking.add(prerequisite);
                } else {
                    dangling.add(prerequisite);
                }
            }
            blocking.sort(ID_ORDER);
            dangling.sort(ID_ORDER);
            if (!blocking.isEmpty()) {
                UNREACHABLE_RESEARCH.put(id, new UnreachableResearch(UnreachableCause.DOWNSTREAM_OF_UNREACHABLE, blocking));
            } else {
                UNREACHABLE_RESEARCH.put(id, new UnreachableResearch(UnreachableCause.DANGLING_PREREQUISITE, dangling));
            }
        }

        // 错误上报：每个含环分量一条环路径，其余按节点各一条
        if (serverSide) {
            for (List<ResourceLocation> component : components) {
                // 只含自环的单节点分量由该节点自身的信息表述，不重复报环路径
                if (component.size() < 2) continue;
                String path = findCyclePath(component.get(0), new HashSet<>(component), successors);
                if (path == null) continue;
                recordIfServer(true, component.get(0),
                        Component.translatable("error.machine_max.research.prerequisite_cycle", path));
            }
            for (ResourceLocation id : allIds) {
                UnreachableResearch info = UNREACHABLE_RESEARCH.get(id);
                if (info == null) continue;
                switch (info.cause()) {
                    case SELF_PREREQUISITE -> recordIfServer(true, id,
                            Component.translatable("error.machine_max.research.self_prerequisite"));
                    case DOWNSTREAM_OF_UNREACHABLE -> recordIfServer(true, id,
                            Component.translatable("error.machine_max.research.unreachable_prerequisite", joinIds(info.culprits())));
                    case DANGLING_PREREQUISITE -> recordIfServer(true, id,
                            Component.translatable("error.machine_max.research.missing_prerequisite", joinIds(info.culprits())));
                    // 环成员已由所在分量的环路径信息覆盖
                    case ON_CYCLE -> {
                    }
                }
            }
        }

        // 阶段二：组索引与组内分层
        Map<ResourceLocation, List<ResourceLocation>> groupMembers = new HashMap<>();
        for (ResourceLocation id : allIds) {
            List<ResourceLocation> groups = ALL_RESEARCH_RECIPES.get(id).value().getGroups();
            for (ResourceLocation group : new LinkedHashSet<>(groups.isEmpty() ? List.of(MISC_GROUP) : groups)) {
                groupMembers.computeIfAbsent(group, k -> new ArrayList<>()).add(id);
            }
        }
        List<ResourceLocation> groupIds = sortedIds(groupMembers.keySet());
        for (ResourceLocation group : groupIds) {
            List<ResourceLocation> members = groupMembers.get(group);
            members.sort(ID_ORDER);
            GROUP_MEMBERS.put(group, members);
        }
        for (ResourceLocation group : groupIds) {
            NODE_DEPTH.put(group, buildGroupDepth(GROUP_MEMBERS.get(group), reachable, prerequisites));
        }
    }

    /**
     * 在单个组内按拓扑序定案每个成员的层级与主前置。
     *
     * <p>入度只计组内前置，且不可达成员不参与入度计算——它们一律置于组根层、不缩进、不承载子节点。
     * 阶段一已把不可达节点排除在可达成集合外，组内图必定无环，因此队列不会被卡住。</p>
     *
     * @param members       该组成员，按 id 字母序
     * @param reachable     全局可达成集合
     * @param prerequisites 去重后的前置列表
     * @return 成员到层级信息的映射，键顺序与 {@code members} 一致
     */
    private static Map<ResourceLocation, NodeDepth> buildGroupDepth(
            List<ResourceLocation> members,
            Set<ResourceLocation> reachable,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites) {
        Set<ResourceLocation> memberSet = new HashSet<>(members);
        Map<ResourceLocation, NodeDepth> depths = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> inDegree = new HashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> inGroupPrerequisites = new HashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> dependents = new HashMap<>();

        for (ResourceLocation member : members) {
            if (!reachable.contains(member)) {
                depths.put(member, new NodeDepth(0, null, prerequisites.get(member).size()));
                continue;
            }
            List<ResourceLocation> inGroup = new ArrayList<>();
            for (ResourceLocation prerequisite : prerequisites.get(member)) {
                if (memberSet.contains(prerequisite) && reachable.contains(prerequisite)) {
                    inGroup.add(prerequisite);
                }
            }
            inGroupPrerequisites.put(member, inGroup);
            inDegree.put(member, inGroup.size());
            for (ResourceLocation prerequisite : inGroup) {
                dependents.computeIfAbsent(prerequisite, k -> new ArrayList<>()).add(member);
            }
        }

        PriorityQueue<ResourceLocation> queue = new PriorityQueue<>(ID_ORDER);
        for (ResourceLocation member : members) {
            if (inDegree.getOrDefault(member, -1) == 0) {
                depths.put(member, new NodeDepth(0, null, prerequisites.get(member).size()));
                queue.add(member);
            }
        }
        while (!queue.isEmpty()) {
            ResourceLocation member = queue.poll();
            for (ResourceLocation dependent : dependents.getOrDefault(member, List.of())) {
                if (inDegree.merge(dependent, -1, Integer::sum) != 0) continue;
                // 入度归零意味着全部组内前置都已定案，此时才能选出 depth 最大的主前置
                ResourceLocation primary = choosePrimary(inGroupPrerequisites.get(dependent), depths);
                depths.put(dependent, new NodeDepth(
                        depths.get(primary).depth() + 1,
                        primary,
                        prerequisites.get(dependent).size() - 1));
                queue.add(dependent);
            }
        }
        return depths;
    }

    /**
     * 选出主前置：组内前置中层级最深者；并列时取前置列表中声明顺序靠前者。
     *
     * @param inGroup 组内前置列表，按配方中的声明顺序
     * @param depths  已定案的层级信息
     * @return 主前置 id；调用时组内前置均已定案，因此必有结果
     */
    private static ResourceLocation choosePrimary(List<ResourceLocation> inGroup, Map<ResourceLocation, NodeDepth> depths) {
        ResourceLocation primary = null;
        int deepest = -1;
        for (ResourceLocation prerequisite : inGroup) {
            int depth = depths.get(prerequisite).depth();
            if (depth > deepest) {
                deepest = depth;
                primary = prerequisite;
            }
        }
        return primary;
    }

    /**
     * 求有向图的强连通分量（Kosaraju，两趟迭代遍历）。
     *
     * @param ids                     全部节点，按 id 字母序
     * @param successors              前置 -> 依赖它的节点
     * @param registeredPrerequisites 节点 -> 已注册的前置（反图邻接）
     * @return 分量列表，分量内按 id 字母序
     */
    private static List<List<ResourceLocation>> stronglyConnectedComponents(
            List<ResourceLocation> ids,
            Map<ResourceLocation, List<ResourceLocation>> successors,
            Map<ResourceLocation, List<ResourceLocation>> registeredPrerequisites) {
        Set<ResourceLocation> visited = new HashSet<>();
        List<ResourceLocation> finishOrder = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            if (!visited.add(id)) continue;
            Deque<ResourceLocation> stack = new ArrayDeque<>();
            Deque<Integer> cursor = new ArrayDeque<>();
            stack.push(id);
            cursor.push(0);
            while (!stack.isEmpty()) {
                ResourceLocation current = stack.peek();
                List<ResourceLocation> out = successors.getOrDefault(current, List.of());
                int index = cursor.pop();
                if (index < out.size()) {
                    cursor.push(index + 1);
                    ResourceLocation next = out.get(index);
                    if (visited.add(next)) {
                        stack.push(next);
                        cursor.push(0);
                    }
                } else {
                    finishOrder.add(current);
                    stack.pop();
                }
            }
        }

        List<List<ResourceLocation>> components = new ArrayList<>();
        Set<ResourceLocation> assigned = new HashSet<>();
        for (int i = finishOrder.size() - 1; i >= 0; i--) {
            ResourceLocation start = finishOrder.get(i);
            if (!assigned.add(start)) continue;
            List<ResourceLocation> component = new ArrayList<>();
            Deque<ResourceLocation> stack = new ArrayDeque<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                ResourceLocation current = stack.pop();
                component.add(current);
                for (ResourceLocation previous : registeredPrerequisites.getOrDefault(current, List.of())) {
                    if (assigned.add(previous)) stack.push(previous);
                }
            }
            component.sort(ID_ORDER);
            components.add(component);
        }
        return components;
    }

    /**
     * 在分量内沿出边前进直到重访节点，截取重访节点起始的环，拼成 {@code a → b → a} 形式的路径。
     *
     * @param start     起始节点，取分量内 id 字母序最靠前者
     * @param component 分量成员
     * @param successors 前置 -> 依赖它的节点
     * @return 环路径文本；分量内找不到可走的出边时返回 {@code null}
     */
    private static String findCyclePath(ResourceLocation start,
                                        Set<ResourceLocation> component,
                                        Map<ResourceLocation, List<ResourceLocation>> successors) {
        List<ResourceLocation> path = new ArrayList<>();
        Map<ResourceLocation, Integer> visitedAt = new HashMap<>();
        ResourceLocation current = start;
        while (true) {
            Integer seen = visitedAt.get(current);
            if (seen != null) {
                List<ResourceLocation> cycle = new ArrayList<>(path.subList(seen, path.size()));
                cycle.add(current);
                return joinIds(cycle);
            }
            visitedAt.put(current, path.size());
            path.add(current);
            ResourceLocation next = null;
            for (ResourceLocation candidate : successors.getOrDefault(current, List.of())) {
                if (component.contains(candidate)) {
                    next = candidate;
                    break;
                }
            }
            if (next == null) return null;
            current = next;
        }
    }

    /** 拼接 id 列表为 {@code a → b → c} 形式的可读文本 */
    private static String joinIds(List<ResourceLocation> ids) {
        StringBuilder builder = new StringBuilder();
        for (ResourceLocation id : ids) {
            if (!builder.isEmpty()) builder.append(" → ");
            builder.append(id);
        }
        return builder.toString();
    }

    /** 按 id 字符串字母序排序，作为一切遍历起点与同层排序的唯一依据 */
    private static List<ResourceLocation> sortedIds(Collection<ResourceLocation> ids) {
        List<ResourceLocation> sorted = new ArrayList<>(ids);
        sorted.sort(ID_ORDER);
        return sorted;
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
            // 研发索引与分组、依赖层级
            rebuildResearchIndex(recipeManager, true);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void register(AddReloadListenerEvent event) {
            event.addListener(new DataPackReloader());
            DataPackReloader.serverResources = event.getServerResources();
        }
    }

    /**
     * 清空指定逻辑侧的配方索引（客户端退出服务器时调用，避免残留上一个服务器的配方）。
     *
     * @param serverSide true 清服务端容器
     */
    public static void clearRecipeIndex(boolean serverSide) {
        if (serverSide) {
            SERVER_ALL_FABRICATING_RECIPES.clear();
            SERVER_ALL_PART_FABRICATING_RECIPES.clear();
            SERVER_PART_RECIPES.clear();
            SERVER_RESEARCH_BY_PART.clear();
            SERVER_RESEARCH_BY_FABRICATING_RECIPE.clear();
        } else {
            ALL_FABRICATING_RECIPES.clear();
            ALL_PART_FABRICATING_RECIPES.clear();
            PART_RECIPES.clear();
        }
        ALL_RESEARCH_RECIPES.clear();
        BLUEPRINT_RESEARCH_RECIPES.clear();
        GROUP_MEMBERS.clear();
        NODE_DEPTH.clear();
        UNREACHABLE_RESEARCH.clear();
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
