package io.github.sweetzonzi.machine_max.client.render.gui.research;

import io.github.sweetzonzi.machine_max.common.attachment.BlueprintAttachment;
import io.github.sweetzonzi.machine_max.common.recipe.BlueprintResearchRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.IngredientCountPair;
import io.github.sweetzonzi.machine_max.common.recipe.ResearchRecipe;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 研发标签页右窗格的详情片段渲染。
 *
 * <p>全部文案在 Java 侧经 {@code Component.translatable} 格式化后写入片段：AUI 的
 * {@code <translation>} 元素不支持参数插值。</p>
 */
public final class ResearchDetailRenderer {

    private ResearchDetailRenderer() {
    }

    /** 空态详情 */
    public static String empty() {
        return "<div class=\"detail-empty\">"
                + ResearchHtml.escape(Component.translatable("gui.machine_max.research.detail.empty").getString())
                + "</div>";
    }

    /**
     * 组详情：进度总览与状态分布。
     *
     * @param groupNode 组节点，其子树即该组条目
     * @param hint      说明区文案，两个标签页各自提供
     */
    public static String group(ResearchTreeNode groupNode, String hint) {
        List<String> names = new ArrayList<>();
        List<String> tones = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        collectStatusCounts(groupNode, names, tones, counts);

        StringBuilder body = new StringBuilder("<div class=\"detail-body\">");
        body.append(ResearchHtml.section(
                Component.translatable("gui.machine_max.research.detail.distribution").getString(),
                distributionHtml(names, tones, counts)));
        body.append(ResearchHtml.section(
                Component.translatable("gui.machine_max.research.detail.description").getString(),
                "<div class=\"sect-text\">" + ResearchHtml.escape(hint) + "</div>"));
        body.append("</div>");

        String progress = Component.translatable("gui.machine_max.research.group.progress",
                groupNode.progressDone(), groupNode.progressTotal()).getString();
        String head = "<div class=\"detail-head\">"
                + "<div class=\"detail-title\">" + ResearchHtml.escape(groupNode.label()) + "</div>"
                + "<div class=\"detail-sub\">" + ResearchHtml.escape(groupNode.id()) + "</div>"
                + "<div class=\"detail-status tone-" + ResearchTreeData.TONE_READY + "\">"
                + ResearchHtml.escape(progress) + "</div>"
                + "</div>";
        String foot = "<div class=\"detail-foot\">"
                + ResearchHtml.actionButton(ResearchAction.EXPAND, true, false)
                + ResearchHtml.actionButton(ResearchAction.COLLAPSE, true, false)
                + "</div>";
        return head + body + foot;
    }

    /**
     * 研发条目详情：不可研发原因 / 产物预览 / 研发点 / 研发材料 / 前置条件 / 说明。
     *
     * @param player   玩家，用于统计背包中的材料
     * @param research 玩家科研状态
     * @param groupNode 该条目所在的组节点，用于区分组内与组外前置
     * @param node     选中的研发条目节点
     */
    public static String research(Player player, BlueprintAttachment research, ResearchTreeNode groupNode,
                                  ResearchTreeNode node) {
        ResourceLocation researchId = ResourceLocation.tryParse(node.id());
        RecipeHolder<ResearchRecipe> holder = researchId == null ? null : MMDynamicRes.ALL_RESEARCH_RECIPES.get(researchId);
        if (holder == null) return empty();
        ResearchRecipe recipe = holder.value();

        StringBuilder body = new StringBuilder("<div class=\"detail-body\">");

        MMDynamicRes.UnreachableResearch unreachable = MMDynamicRes.UNREACHABLE_RESEARCH.get(researchId);
        if (unreachable != null) {
            body.append("<div class=\"sect\"><div class=\"block-reason\">")
                    .append("<span class=\"br-title\">")
                    .append(ResearchHtml.escape(ResearchTreeData.statusText(ResearchTreeData.TONE_BAD)))
                    .append("</span><span class=\"br-text\">")
                    .append(ResearchHtml.escape(unreachableReason(unreachable)))
                    .append("</span></div></div>");
        }

        body.append(ResearchHtml.section(
                Component.translatable("gui.machine_max.research.detail.preview").getString(),
                previewHtml(player, researchId, node)));

        body.append(ResearchHtml.section(
                Component.translatable("gui.machine_max.research.detail.points").getString(),
                "<div class=\"cost-row\"><span class=\"cost-value\">" + recipe.getResearchCost()
                        + "<span class=\"cost-unit\">RP</span></span></div>"));

        if (!recipe.getResearchIngredientPairs().isEmpty()) {
            body.append(ResearchHtml.section(
                    Component.translatable("gui.machine_max.research.detail.materials").getString(),
                    materialsHtml(player, recipe.getResearchIngredientPairs())));
        }

        body.append(ResearchHtml.section(
                Component.translatable("gui.machine_max.research.detail.prerequisites").getString(),
                prerequisitesHtml(research, groupNode, recipe)));

        StringBuilder descriptionHtml = new StringBuilder("<div class=\"sect-text\">")
                .append(ResearchHtml.escape(descriptionText(recipe))).append("</div>");
        if (node.note() != null) {
            descriptionHtml.append("<div class=\"sect-text\">").append(ResearchHtml.escape(node.note())).append("</div>");
        }
        body.append(ResearchHtml.section(
                Component.translatable("gui.machine_max.research.detail.description").getString(),
                descriptionHtml.toString()));
        body.append("</div>");

        return headHtml(node.label(), node.id(), node.statusText(), node.tone())
                + body
                + "<div class=\"detail-foot\">" + actionsHtml(node.actions()) + "</div>";
    }

    // ==================== 片段 ====================

    private static String headHtml(String label, String sub, String statusText, String tone) {
        return "<div class=\"detail-head\">"
                + "<div class=\"detail-title\">" + ResearchHtml.escape(label) + "</div>"
                + "<div class=\"detail-sub\">" + ResearchHtml.escape(sub) + "</div>"
                + "<div class=\"detail-status tone-" + ResearchHtml.escape(tone) + "\">"
                + ResearchHtml.escape(statusText) + "</div>"
                + "</div>";
    }

    /** 产物预览：有解锁配方的条目展示图标与产物名，通用研究条目没有可预览的产物 */
    private static String previewHtml(Player player, ResourceLocation researchId, ResearchTreeNode node) {
        RecipeHolder<BlueprintResearchRecipe> blueprintResearch = MMDynamicRes.BLUEPRINT_RESEARCH_RECIPES.get(researchId);
        if (blueprintResearch == null) {
            return "<div class=\"preview-box\"><span class=\"pb-hint\">" + ResearchHtml.escape(
                    Component.translatable("gui.machine_max.research.detail.no_preview").getString())
                    + "</span></div>";
        }
        ItemStack product = unlockedProduct(player, researchId);
        String name = product.isEmpty() ? node.label() : product.getHoverName().getString();
        String icon = node.icon() == null ? "" : node.icon();
        return "<div class=\"preview-box\"><texture class=\"pb-tex\" src=\"" + ResearchHtml.escape(icon)
                + "\"></texture><span class=\"pb-name\">" + ResearchHtml.escape(name) + " ×1</span></div>";
    }

    private static String materialsHtml(Player player, List<IngredientCountPair> pairs) {
        StringBuilder builder = new StringBuilder();
        for (IngredientCountPair pair : pairs) {
            int have = countInInventory(player, pair.ingredient());
            int need = pair.count();
            boolean enough = have >= need;
            int percent = need <= 0 ? 100 : Math.min(100, (int) Math.round(have * 100.0 / need));
            builder.append("<div class=\"mat-row ").append(enough ? "ok" : "lack").append("\">")
                    .append("<div class=\"mat-head\"><span class=\"mat-name\">")
                    .append(ResearchHtml.escape(ingredientName(pair.ingredient())))
                    .append("</span><span class=\"mat-count\">").append(have).append(" / ").append(need)
                    .append("</span></div>")
                    .append("<div class=\"mat-track\"><div class=\"mat-fill\" style=\"width:")
                    .append(percent).append("%\"></div></div></div>");
        }
        return builder.toString();
    }

    private static String prerequisitesHtml(BlueprintAttachment research, ResearchTreeNode groupNode, ResearchRecipe recipe) {
        if (recipe.getPrerequisites().isEmpty()) {
            return "<div class=\"sect-text\">"
                    + ResearchHtml.escape(Component.translatable("gui.machine_max.research.detail.no_prerequisite").getString())
                    + "</div>";
        }
        Set<ResourceLocation> groupMembers = groupMembers(groupNode);
        String doneText = ResearchTreeData.statusText(ResearchTreeData.TONE_OK);
        String todoText = Component.translatable("gui.machine_max.research.detail.unfinished").getString();
        String outsideText = Component.translatable("gui.machine_max.research.detail.other_group").getString();

        StringBuilder builder = new StringBuilder();
        for (ResourceLocation prerequisite : new LinkedHashSet<>(recipe.getPrerequisites())) {
            boolean done = research.isResearched(prerequisite);
            builder.append("<div class=\"pre-row ").append(done ? "done" : "todo").append("\">")
                    .append("<span class=\"pre-mark\"></span>")
                    .append("<span class=\"pre-name\">")
                    .append(ResearchHtml.escape(ResearchTreeData.researchLabel(prerequisite)))
                    .append("</span>");
            if (!groupMembers.contains(prerequisite)) {
                builder.append("<span class=\"pre-outside\">").append(ResearchHtml.escape(outsideText)).append("</span>");
            }
            builder.append("<span class=\"pre-state\">").append(ResearchHtml.escape(done ? doneText : todoText))
                    .append("</span></div>");
        }
        return builder.toString();
    }

    private static String distributionHtml(List<String> names, List<String> tones, List<Integer> counts) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            builder.append("<div class=\"dist-row\"><span class=\"dist-name tone-").append(ResearchHtml.escape(tones.get(i)))
                    .append("\">").append(ResearchHtml.escape(names.get(i)))
                    .append("</span><span class=\"dist-count\">").append(counts.get(i)).append("</span></div>");
        }
        return builder.toString();
    }

    private static String actionsHtml(List<ResearchTreeNode.Action> actions) {
        if (actions.isEmpty()) {
            return "<span class=\"sect-text\">"
                    + ResearchHtml.escape(Component.translatable("gui.machine_max.research.detail.no_action").getString())
                    + "</span>";
        }
        StringBuilder builder = new StringBuilder();
        for (ResearchTreeNode.Action action : actions) {
            builder.append(ResearchHtml.actionButton(action.type(), action.enabled(), false));
        }
        return builder.toString();
    }

    // ==================== 取数 ====================

    private static void collectStatusCounts(ResearchTreeNode node, List<String> names, List<String> tones,
                                            List<Integer> counts) {
        for (ResearchTreeNode child : node.children()) {
            if (child.kind() == ResearchTreeNode.Kind.GROUP) continue;
            int index = names.indexOf(child.statusText());
            if (index < 0) {
                names.add(child.statusText());
                tones.add(child.tone());
                counts.add(0);
                index = names.size() - 1;
            }
            counts.set(index, counts.get(index) + 1);
            collectStatusCounts(child, names, tones, counts);
        }
    }

    /** 不可研发的原因文案 */
    private static String unreachableReason(MMDynamicRes.UnreachableResearch unreachable) {
        return switch (unreachable.cause()) {
            case SELF_PREREQUISITE -> Component.translatable("error.machine_max.research.self_prerequisite").getString();
            case ON_CYCLE -> Component.translatable("gui.machine_max.research.reason.cycle").getString();
            case DOWNSTREAM_OF_UNREACHABLE -> Component.translatable(
                    "error.machine_max.research.unreachable_prerequisite", joinIds(unreachable.culprits())).getString();
            case DANGLING_PREREQUISITE -> Component.translatable(
                    "error.machine_max.research.missing_prerequisite", joinIds(unreachable.culprits())).getString();
        };
    }

    /** 研发条目的说明文本：内容包在 description 中填写语言键 */
    private static String descriptionText(ResearchRecipe recipe) {
        String tooltip = recipe.getTooltip();
        if (tooltip == null || tooltip.isEmpty()) {
            return Component.translatable("gui.machine_max.research.detail.default_description").getString();
        }
        return Component.translatable(tooltip).getString();
    }

    private static Set<ResourceLocation> groupMembers(ResearchTreeNode groupNode) {
        if (groupNode == null) return Set.of();
        ResourceLocation groupId = ResourceLocation.tryParse(groupNode.id());
        if (groupId == null) return Set.of();
        List<ResourceLocation> members = MMDynamicRes.GROUP_MEMBERS.get(groupId);
        return members == null ? Set.of() : new LinkedHashSet<>(members);
    }

    private static String ingredientName(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        return items.length == 0 ? "" : items[0].getHoverName().getString();
    }

    private static int countInInventory(Player player, Ingredient ingredient) {
        int count = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && ingredient.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** 取该条目 {@code unlock_recipe} 的产物物品 */
    private static ItemStack unlockedProduct(Player player, ResourceLocation researchId) {
        RecipeHolder<BlueprintResearchRecipe> blueprintResearch = MMDynamicRes.BLUEPRINT_RESEARCH_RECIPES.get(researchId);
        if (blueprintResearch == null) return ItemStack.EMPTY;
        RecipeHolder<FabricatingRecipe> holder =
                MMDynamicRes.ALL_FABRICATING_RECIPES.get(blueprintResearch.value().getUnlockRecipe());
        if (holder == null) return ItemStack.EMPTY;
        return holder.value().getResultItem(player.level().registryAccess());
    }

    private static String joinIds(List<ResourceLocation> ids) {
        StringBuilder builder = new StringBuilder();
        for (ResourceLocation id : ids) {
            if (!builder.isEmpty()) builder.append("、");
            builder.append(id);
        }
        return builder.toString();
    }
}
