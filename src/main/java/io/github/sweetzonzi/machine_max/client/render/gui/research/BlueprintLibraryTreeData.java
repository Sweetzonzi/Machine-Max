package io.github.sweetzonzi.machine_max.client.render.gui.research;

import io.github.sweetzonzi.machine_max.client.blueprint.BlueprintLibraryClient;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintMeta;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.network.payload.library.BlueprintExtractLocalPayload;
import io.github.sweetzonzi.machine_max.network.payload.library.BlueprintExtractPackPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 蓝图库标签页的树数据装配与详情片段渲染。
 *
 * <p>树按「来源 → 内容包 → 条目」分层：来源分「我的设计」与「内容包蓝图」，
 * 内容包蓝图之下再按命名空间分出一个内容包节点。</p>
 */
public final class BlueprintLibraryTreeData {
    /** 来源节点 id：我的设计 */
    public static final String LOCAL_SOURCE_ID = "library:local";
    /** 来源节点 id：内容包蓝图 */
    public static final String PACK_SOURCE_ID = "library:pack";

    private static final String TONE_OK = ResearchTreeData.TONE_OK;
    private static final String TONE_WARN = ResearchTreeData.TONE_WARN;
    private static final String TONE_BAD = ResearchTreeData.TONE_BAD;

    private BlueprintLibraryTreeData() {
    }

    /**
     * 构建蓝图库的整棵树。
     *
     * @param player 玩家，用于统计设计质量
     * @return 来源节点的有序列表
     */
    public static List<ResearchTreeNode> build(Player player) {
        List<ResearchTreeNode> roots = new ArrayList<>();

        List<BlueprintLibraryClient.BlueprintLibraryEntry> valid = BlueprintLibraryClient.getValid();
        List<BlueprintLibraryClient.BlueprintLibraryError> broken = BlueprintLibraryClient.getBroken();
        List<ResearchTreeNode> localNodes = new ArrayList<>();
        for (BlueprintLibraryClient.BlueprintLibraryEntry entry : valid) {
            // 本地蓝图文件只保存载具数据本身，不含图标资源，因此本地条目不出图
            localNodes.add(ResearchTreeNode.blueprint(entry.fileName(), entry.payload().getName(), null, null,
                    statusText("normal"), TONE_OK, false, localActions(false)));
        }
        for (BlueprintLibraryClient.BlueprintLibraryError error : broken) {
            boolean parseError = error.reason() == BlueprintLibraryClient.Reason.PARSE_ERROR;
            localNodes.add(ResearchTreeNode.blueprint(error.fileName(), error.fileName(), null, error.detail(),
                    statusText(parseError ? "parse_error" : "missing_parts"),
                    parseError ? TONE_BAD : TONE_WARN, true, localActions(parseError)));
        }
        roots.add(ResearchTreeNode.group(LOCAL_SOURCE_ID, sectionLabel("local"),
                localNodes.size(), localNodes.size(), localNodes));

        Map<String, List<ResourceLocation>> byNamespace = new TreeMap<>();
        for (ResourceLocation blueprintId : MMDynamicRes.BLUEPRINTS.keySet()) {
            byNamespace.computeIfAbsent(blueprintId.getNamespace(), key -> new ArrayList<>()).add(blueprintId);
        }
        List<ResearchTreeNode> packNodes = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, List<ResourceLocation>> entry : byNamespace.entrySet()) {
            List<ResourceLocation> ids = entry.getValue();
            ids.sort(Comparator.comparing(ResourceLocation::toString));
            List<ResearchTreeNode> entries = new ArrayList<>(ids.size());
            for (ResourceLocation blueprintId : ids) {
                BlueprintData blueprintData = MMDynamicRes.BLUEPRINTS.get(blueprintId);
                ResourceLocation icon = blueprintData == null ? null : blueprintData.getIcon();
                entries.add(ResearchTreeNode.blueprint(blueprintId.toString(),
                        Component.translatable(blueprintId.toLanguageKey()).getString(),
                        icon == null || icon.equals(BlueprintData.EMPTY) ? null : icon.toString(), null,
                        statusText("normal"), TONE_OK, false, packActions()));
            }
            total += entries.size();
            packNodes.add(ResearchTreeNode.group(PACK_SOURCE_ID + ":" + entry.getKey(), entry.getKey(),
                    entries.size(), entries.size(), entries));
        }
        roots.add(ResearchTreeNode.group(PACK_SOURCE_ID, sectionLabel("pack"), total, total, packNodes));
        return roots;
    }

    /**
     * 蓝图条目的详情片段。
     *
     * @param player 玩家，用于统计设计质量
     * @param node   选中的条目节点
     */
    public static String detail(Player player, ResearchTreeNode node) {
        if (node.kind() == ResearchTreeNode.Kind.GROUP) {
            return ResearchDetailRenderer.group(node,
                    Component.translatable("gui.machine_max.research.library.group_hint").getString());
        }
        VehicleData data = vehicleData(node.id());
        String source = node.id().contains(":")
                ? Component.translatable("gui.machine_max.blueprint_library.section.pack").getString()
                : Component.translatable("gui.machine_max.blueprint_library.section.local").getString();

        StringBuilder body = new StringBuilder("<div class=\"detail-body\">");
        // 本地条目的展示名可改：改名输入框随详情一起重建，值即当前名称
        if (!node.id().contains(":")) {
            body.append(ResearchHtml.section(
                    Component.translatable("gui.machine_max.research.library.rename_label").getString(),
                    "<div class=\"search-box\"><input type=\"text\" id=\"library-rename\" value=\""
                            + ResearchHtml.escape(node.label()) + "\"></div>"));
        }
        if (data != null) {
            body.append(ResearchHtml.section(
                    Component.translatable("gui.machine_max.research.library.attributes").getString(),
                    statsHtml(player, data)));
            body.append(ResearchHtml.section(
                    Component.translatable("gui.machine_max.research.library.source").getString(),
                    sourceRows(node, data, source, templateOf(node.id()))));
            String description = data.getMeta() == null ? "" : data.getMeta().description();
            body.append(ResearchHtml.section(
                    Component.translatable("gui.machine_max.research.detail.description").getString(),
                    "<div class=\"sect-text\">" + ResearchHtml.escape(description.isEmpty()
                            ? Component.translatable("gui.machine_max.research.library.default_description").getString()
                            : description) + "</div>"));
        } else {
            body.append(ResearchHtml.section(
                    Component.translatable("gui.machine_max.research.detail.description").getString(),
                    "<div class=\"sect-text\">" + ResearchHtml.escape(node.statusText()) + "</div>"));
        }
        body.append("</div>");

        String head = "<div class=\"detail-head\">"
                + "<div class=\"detail-title\">" + ResearchHtml.escape(node.label()) + "</div>"
                + "<div class=\"detail-sub\">" + ResearchHtml.escape(node.id()) + "</div>"
                + "<div class=\"detail-status tone-" + ResearchHtml.escape(node.tone()) + "\">"
                + ResearchHtml.escape(node.statusText()) + "</div>"
                + "</div>";

        StringBuilder foot = new StringBuilder("<div class=\"detail-foot\">");
        if (node.actions().isEmpty()) {
            foot.append("<span class=\"sect-text\">")
                    .append(ResearchHtml.escape(Component.translatable("gui.machine_max.research.detail.no_action").getString()))
                    .append("</span>");
        } else {
            for (ResearchTreeNode.Action action : node.actions()) {
                foot.append(ResearchHtml.actionButton(action.type(), action.enabled(), false));
            }
        }
        foot.append("</div>");
        return head + body + foot;
    }

    /** 取出：把蓝图取到背包；本地条目取本地文件，内容包条目取包内蓝图 */
    public static void take(ResearchTreeNode node) {
        if (node.id().contains(":")) {
            ResourceLocation blueprintId = ResourceLocation.tryParse(node.id());
            if (blueprintId != null) {
                PacketDistributor.sendToServer(new BlueprintExtractPackPayload(blueprintId));
            }
            return;
        }
        VehicleData data = vehicleData(node.id());
        if (data != null) {
            PacketDistributor.sendToServer(new BlueprintExtractLocalPayload(data, data.getMeta()));
        }
    }

    /** 删除本地蓝图文件；内容包蓝图不可删除 */
    public static boolean delete(ResearchTreeNode node) {
        if (node.id().contains(":")) return false;
        return BlueprintLibraryClient.delete(node.id());
    }

    /** 重命名本地蓝图的展示名 */
    public static boolean rename(ResearchTreeNode node, String newName) {
        if (node.id().contains(":") || newName == null || newName.isBlank()) return false;
        return BlueprintLibraryClient.rename(node.id(), newName.trim());
    }

    // ==================== 片段 ====================

    private static String statsHtml(Player player, VehicleData data) {
        int partCount = data.getParts() == null ? 0 : data.getParts().size();
        int mass = Math.round(data.computeDesignMass(player.level()));
        return "<div class=\"bp-stats\">"
                + statCell(String.valueOf(partCount), Component.translatable("gui.machine_max.research.library.stat.parts").getString())
                + statCell(String.valueOf(mass), Component.translatable("gui.machine_max.research.library.stat.mass").getString())
                + "</div>";
    }

    private static String statCell(String value, String label) {
        return "<div class=\"bp-stat\"><span class=\"bp-stat-value\">" + ResearchHtml.escape(value)
                + "</span><span class=\"bp-stat-label\">" + ResearchHtml.escape(label) + "</span></div>";
    }

    private static String sourceRows(ResearchTreeNode node, VehicleData data, String source,
                                     @Nullable ResourceLocation template) {
        StringBuilder builder = new StringBuilder();
        builder.append(kvRow(Component.translatable("gui.machine_max.research.library.row.source").getString(), source));
        if (template != null) {
            builder.append(kvRow(Component.translatable("gui.machine_max.research.library.row.template").getString(),
                    template.toString()));
        }
        BlueprintMeta meta = data.getMeta();
        if (meta != null && !meta.author().isEmpty()) {
            builder.append(kvRow(Component.translatable("gui.machine_max.research.library.row.author").getString(), meta.author()));
        }
        if (meta != null && !meta.createdAt().isEmpty()) {
            builder.append(kvRow(Component.translatable("gui.machine_max.research.library.row.created_at").getString(), meta.createdAt()));
        }
        builder.append(kvRow(Component.translatable("gui.machine_max.research.library.row.writable").getString(),
                Component.translatable(node.id().contains(":")
                        ? "gui.machine_max.research.library.read_only"
                        : "gui.machine_max.research.library.read_write").getString()));
        return builder.toString();
    }

    private static String kvRow(String key, String value) {
        return "<div class=\"kv-row\"><span class=\"kv-key\">" + ResearchHtml.escape(key)
                + "</span><span class=\"kv-val\">" + ResearchHtml.escape(value) + "</span></div>";
    }

    // ==================== 取数 ====================

    /**
     * 内容包蓝图引用的装配模板；本地蓝图文件不含该字段。
     *
     * @param nodeId 节点 id
     * @return 装配模板 id；本地条目或蓝图缺失时为 {@code null}
     */
    @Nullable
    private static ResourceLocation templateOf(String nodeId) {
        if (!nodeId.contains(":")) return null;
        ResourceLocation blueprintId = ResourceLocation.tryParse(nodeId);
        if (blueprintId == null) return null;
        BlueprintData data = MMDynamicRes.BLUEPRINTS.get(blueprintId);
        return data == null ? null : data.getTemplate();
    }

    /** 按节点 id 取蓝图数据：含命名空间的 id 视为内容包蓝图 */
    @Nullable
    private static VehicleData vehicleData(String nodeId) {
        if (nodeId.contains(":")) {
            ResourceLocation blueprintId = ResourceLocation.tryParse(nodeId);
            if (blueprintId == null) return null;
            BlueprintData blueprintData = MMDynamicRes.BLUEPRINTS.get(blueprintId);
            if (blueprintData == null) return null;
            return MMDynamicRes.TEMPLATES.get(blueprintData.getTemplate());
        }
        for (BlueprintLibraryClient.BlueprintLibraryEntry entry : BlueprintLibraryClient.getValid()) {
            if (entry.fileName().equals(nodeId)) return entry.payload();
        }
        return null;
    }

    /**
     * 本地条目的动作。取出是蓝图库的常用操作，行内保留一个下箭头按钮；
     * 改名与删除用得更少且会挤掉名称，只出现在详情面板底部。
     */
    private static List<ResearchTreeNode.Action> localActions(boolean parseError) {
        List<ResearchTreeNode.Action> actions = new ArrayList<>();
        if (!parseError) {
            actions.add(new ResearchTreeNode.Action(ResearchAction.TAKE, true, true));
            actions.add(new ResearchTreeNode.Action(ResearchAction.RENAME, true, false));
        }
        actions.add(new ResearchTreeNode.Action(ResearchAction.DELETE, true, false));
        return actions;
    }

    private static List<ResearchTreeNode.Action> packActions() {
        return List.of(new ResearchTreeNode.Action(ResearchAction.TAKE, true, true));
    }

    private static String statusText(String suffix) {
        return Component.translatable("gui.machine_max.research.library.status." + suffix).getString();
    }

    private static String sectionLabel(String suffix) {
        return Component.translatable("gui.machine_max.blueprint_library.section." + suffix).getString();
    }
}
