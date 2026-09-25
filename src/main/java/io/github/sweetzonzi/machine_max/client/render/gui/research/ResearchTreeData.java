package io.github.sweetzonzi.machine_max.client.render.gui.research;

import io.github.sweetzonzi.machine_max.common.attachment.BlueprintAttachment;
import io.github.sweetzonzi.machine_max.common.recipe.ResearchRecipe;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 研发标签页的树数据装配：把组索引、依赖层级与玩家科研状态合成 {@link ResearchTreeNode} 树。
 */
public final class ResearchTreeData {
    /** 状态色调 */
    public static final String TONE_OK = "ok";
    public static final String TONE_READY = "ready";
    public static final String TONE_WARN = "warn";
    public static final String TONE_LOCKED = "locked";
    public static final String TONE_BAD = "bad";

    private ResearchTreeData() {
    }

    /**
     * 构建研发标签页的整棵树：每个组一个可折叠节点，组内按依赖层级嵌套。
     *
     * @param player   玩家，用于判定完成态、材料与抄录条件
     * @param research 玩家科研状态
     * @return 组节点的有序列表，组之间按组 id 字母序
     */
    public static List<ResearchTreeNode> buildResearchTree(Player player, BlueprintAttachment research) {
        List<ResearchTreeNode> groups = new ArrayList<>();
        for (Map.Entry<ResourceLocation, List<ResourceLocation>> entry : MMDynamicRes.GROUP_MEMBERS.entrySet()) {
            ResourceLocation groupId = entry.getKey();
            List<ResourceLocation> members = entry.getValue();
            Map<ResourceLocation, MMDynamicRes.NodeDepth> depths =
                    MMDynamicRes.NODE_DEPTH.getOrDefault(groupId, Map.of());
            int done = 0;
            for (ResourceLocation member : members) {
                if (research.isResearched(member)) done++;
            }
            groups.add(ResearchTreeNode.group(
                    groupId.toString(),
                    groupLabel(groupId),
                    done,
                    members.size(),
                    buildGroupChildren(player, research, members, depths)));
        }
        return groups;
    }

    /**
     * 找出包含指定节点的组节点。
     *
     * @param roots  根节点列表
     * @param nodeId 目标节点 id
     * @return 所在的组节点；未找到时返回 {@code null}
     */
    @Nullable
    public static ResearchTreeNode findGroup(List<ResearchTreeNode> roots, String nodeId) {
        for (ResearchTreeNode root : roots) {
            if (root.kind() == ResearchTreeNode.Kind.GROUP && containsNode(root, nodeId)) return root;
        }
        return null;
    }

    private static boolean containsNode(ResearchTreeNode node, String nodeId) {
        if (node.id().equals(nodeId)) return true;
        for (ResearchTreeNode child : node.children()) {
            if (containsNode(child, nodeId)) return true;
        }
        return false;
    }

    /** 组内按依赖层级嵌套：主前置为空者位于组根层，其余挂在主前置之下 */
    private static List<ResearchTreeNode> buildGroupChildren(Player player, BlueprintAttachment research,
                                                            List<ResourceLocation> members,
                                                            Map<ResourceLocation, MMDynamicRes.NodeDepth> depths) {
        List<ResourceLocation> roots = new ArrayList<>();
        Map<ResourceLocation, List<ResourceLocation>> childrenOf = new LinkedHashMap<>();
        for (ResourceLocation member : members) {
            MMDynamicRes.NodeDepth depth = depths.get(member);
            ResourceLocation primary = depth == null ? null : depth.primaryPrerequisite();
            if (primary == null) {
                roots.add(member);
            } else {
                childrenOf.computeIfAbsent(primary, key -> new ArrayList<>()).add(member);
            }
        }
        return buildMembers(player, research, roots, depths, childrenOf);
    }

    /**
     * 按「主前置 → 直接子节点」表递归展开。
     *
     * <p>组内的嵌套关系已在 {@link #buildGroupChildren} 里一次性定案，逐层取子表即可；
     * 不能把子节点列表再交给 {@link #buildGroupChildren}——子节点的主前置是父节点、不在该子列表里，
     * 会被重新判成「没有根」而整批丢失。</p>
     */
    private static List<ResearchTreeNode> buildMembers(Player player, BlueprintAttachment research,
                                                       List<ResourceLocation> members,
                                                       Map<ResourceLocation, MMDynamicRes.NodeDepth> depths,
                                                       Map<ResourceLocation, List<ResourceLocation>> childrenOf) {
        List<ResearchTreeNode> nodes = new ArrayList<>(members.size());
        for (ResourceLocation member : members) {
            nodes.add(buildMember(player, research, member, depths, childrenOf));
        }
        return nodes;
    }

    private static ResearchTreeNode buildMember(Player player, BlueprintAttachment research, ResourceLocation researchId,
                                                Map<ResourceLocation, MMDynamicRes.NodeDepth> depths,
                                                Map<ResourceLocation, List<ResourceLocation>> childrenOf) {
        RecipeHolder<ResearchRecipe> holder = MMDynamicRes.ALL_RESEARCH_RECIPES.get(researchId);
        if (holder == null) {
            return ResearchTreeNode.research(researchId.toString(), researchId.toString(), null, null,
                    statusText(TONE_BAD), TONE_BAD, 0, true, List.of(), List.of());
        }
        ResearchRecipe recipe = holder.value();
        MMDynamicRes.NodeDepth depth = depths.get(researchId);
        MMDynamicRes.UnreachableResearch unreachable = MMDynamicRes.UNREACHABLE_RESEARCH.get(researchId);
        boolean completed = research.isResearched(researchId);

        String status;
        String tone;
        if (unreachable != null) {
            status = statusText(TONE_BAD);
            tone = TONE_BAD;
        } else if (completed) {
            status = statusText(TONE_OK);
            tone = TONE_OK;
        } else if (!research.hasSatisfiedPrerequisites(player, researchId)) {
            status = statusText(TONE_LOCKED);
            tone = TONE_LOCKED;
        } else if (research.canCompleteResearch(player, researchId)) {
            status = statusText(TONE_READY);
            tone = TONE_READY;
        } else {
            status = statusText(TONE_WARN);
            tone = TONE_WARN;
        }

        String note = null;
        if (depth != null && depth.additionalPrerequisites() > 0 && unreachable == null) {
            note = Component.translatable("gui.machine_max.research.note.additional",
                    depth.additionalPrerequisites()).getString();
        }

        return ResearchTreeNode.research(
                researchId.toString(),
                researchLabel(researchId),
                recipe.getIcon().toString(),
                note,
                status,
                tone,
                depth == null ? 0 : depth.depth(),
                unreachable != null,
                buildActions(player, research, researchId, completed, unreachable != null),
                buildMembers(player, research, childrenOf.getOrDefault(researchId, List.of()), depths, childrenOf));
    }

    /** 行内动作：已完成条目提供取出（抄录制造蓝图），未完成且可达成条目提供研发；每条最多一个动作 */
    private static List<ResearchTreeNode.Action> buildActions(Player player, BlueprintAttachment research,
                                                             ResourceLocation researchId, boolean completed,
                                                             boolean unreachable) {
        if (unreachable) return List.of();
        if (completed) {
            if (MMDynamicRes.BLUEPRINT_RESEARCH_RECIPES.containsKey(researchId)) {
                return List.of(new ResearchTreeNode.Action(
                        ResearchAction.TAKE, research.canTranscribe(player, researchId), true));
            }
            return List.of();
        }
        return List.of(new ResearchTreeNode.Action(
                ResearchAction.RESEARCH, research.canCompleteResearch(player, researchId), true));
    }

    /** 研发条目的显示名：沿用裸 RL 约定，取研发项目自身的语言键 */
    public static String researchLabel(ResourceLocation researchId) {
        return Component.translatable(languageKey(researchId)).getString();
    }

    /** 组的显示名：取组 id 的语言键，缺失时回退显示组 id */
    public static String groupLabel(ResourceLocation groupId) {
        String key = languageKey(groupId);
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? groupId.toString() : translated;
    }

    /**
     * 语言键：注册名里的 `:` 与 `/` 都换成 `.`。
     *
     * <p>`ResourceLocation.toLanguageKey()` 只把命名空间与路径用 `.` 连接，路径里的 `/` 原样保留
     * （`machine_max:research/ae86_chassis` → `machine_max.research/ae86_chassis`），带目录的研发 id
     * 因此取不到内容包写的 `machine_max.research.ae86_chassis`。这里按语言文件约定补齐替换。</p>
     */
    private static String languageKey(ResourceLocation id) {
        return id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    /** 状态文案：按色调取对应的语言键 */
    public static String statusText(String tone) {
        String key = switch (tone) {
            case TONE_OK -> "gui.machine_max.research.status.done";
            case TONE_READY -> "gui.machine_max.research.status.available";
            case TONE_WARN -> "gui.machine_max.research.status.insufficient";
            case TONE_LOCKED -> "gui.machine_max.research.status.prerequisite_missing";
            default -> "gui.machine_max.research.status.unreachable";
        };
        return Component.translatable(key).getString();
    }
}
