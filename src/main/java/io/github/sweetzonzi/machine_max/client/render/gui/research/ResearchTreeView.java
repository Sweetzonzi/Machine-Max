package io.github.sweetzonzi.machine_max.client.render.gui.research;

import com.sighs.apricityui.init.Element;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 可折叠树的渲染与交互。两个标签页共用本类，只消费 {@link ResearchTreeNode} 模型。
 *
 * <p>整棵树由 HTML 片段一次性重建（{@code setInnerHTML}），重建后逐行绑定一次监听器；
 * 展开、折叠、选中都只切换 class，不重建 DOM。</p>
 */
public final class ResearchTreeView {
    /** 视觉缩进在深度 4 之后停止增长（层号 = 深度 + 1） */
    private static final int DEPTH_CAP = 4;

    private final Element container;
    private final Consumer<ResearchTreeNode> onSelect;
    private final BiConsumer<ResearchTreeNode, ResearchAction> onAction;
    /** 折叠的节点 id；由调用方长期持有，跨界面开关保留 */
    private final Set<String> collapsedIds;
    /** 搜索期间的折叠快照，清空搜索后据此恢复 */
    private final Set<String> collapseSnapshot = new LinkedHashSet<>();

    private final Map<String, ResearchTreeNode> nodesById = new HashMap<>();
    private final Map<String, Element> nodeElements = new HashMap<>();
    private final Map<String, Element> rowElements = new HashMap<>();
    private List<ResearchTreeNode> roots = List.of();
    private String selectedId;
    private String lastQuery = "";

    public ResearchTreeView(Element container, Set<String> collapsedIds,
                            Consumer<ResearchTreeNode> onSelect,
                            BiConsumer<ResearchTreeNode, ResearchAction> onAction) {
        this.container = container;
        this.collapsedIds = collapsedIds;
        this.onSelect = onSelect;
        this.onAction = onAction;
    }

    /** 当前选中的节点 id；未选中时为 {@code null} */
    @Nullable
    public String selectedId() {
        return selectedId;
    }

    /** 在模型里按 id 查节点；找不到时返回 {@code null} */
    @Nullable
    public ResearchTreeNode findNode(String id) {
        return id == null ? null : nodesById.get(id);
    }

    /**
     * 用新数据整体重建树。折叠状态与选中项按 id 恢复，搜索词非空时重建后重新应用过滤。
     *
     * @param roots 根节点列表
     */
    public void render(List<ResearchTreeNode> roots) {
        this.roots = roots;
        nodesById.clear();
        indexNodes(roots);
        container.setInnerHTML(buildNodes(roots, 0));
        cacheElements();
        bindInteractions();
        if (!lastQuery.isEmpty()) {
            applyFilter(lastQuery);
        }
    }

    /** 选中一个节点：更新选中样式并通知调用方重绘详情 */
    public void select(ResearchTreeNode node) {
        Element previous = selectedId == null ? null : rowElements.get(selectedId);
        if (previous != null) setClassToken(previous, "selected", false);
        selectedId = node.id();
        Element row = rowElements.get(selectedId);
        if (row != null) setClassToken(row, "selected", true);
        onSelect.accept(node);
    }

    /** 切换一个节点的折叠状态 */
    public void toggleCollapsed(String id) {
        Element treeNode = nodeElements.get(id);
        if (treeNode == null) return;
        boolean collapsed = !collapsedIds.contains(id);
        if (collapsed) {
            collapsedIds.add(id);
        } else {
            collapsedIds.remove(id);
        }
        setClassToken(treeNode, "collapsed", collapsed);
    }

    /** 展开或折叠全部可折叠节点 */
    public void setAllCollapsed(boolean collapsed) {
        for (Map.Entry<String, Element> entry : nodeElements.entrySet()) {
            ResearchTreeNode node = nodesById.get(entry.getKey());
            if (node == null || node.children().isEmpty()) continue;
            if (collapsed) {
                collapsedIds.add(entry.getKey());
            } else {
                collapsedIds.remove(entry.getKey());
            }
            setClassToken(entry.getValue(), "collapsed", collapsed);
        }
    }

    /** 为指定节点设置折叠状态，供详情面板的展开 / 折叠按钮使用 */
    public void setCollapsed(String id, boolean collapsed) {
        ResearchTreeNode node = nodesById.get(id);
        Element treeNode = nodeElements.get(id);
        if (node == null || treeNode == null || node.children().isEmpty()) return;
        if (collapsed) {
            collapsedIds.add(id);
        } else {
            collapsedIds.remove(id);
        }
        setClassToken(treeNode, "collapsed", collapsed);
    }

    /**
     * 应用搜索过滤：命中节点时保留其祖先链，命中组名时整组展开。
     *
     * <p>进入搜索时记录当前折叠状态，清空搜索词时恢复；恢复走整树重建，避免逐节点回写样式。</p>
     *
     * @param rawQuery 搜索框当前内容
     */
    public void onSearchChanged(@Nullable String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        if (query.equals(lastQuery)) return;
        if (!query.isEmpty() && lastQuery.isEmpty()) {
            collapseSnapshot.clear();
            collapseSnapshot.addAll(collapsedIds);
        }
        lastQuery = query;
        if (query.isEmpty()) {
            collapsedIds.clear();
            collapsedIds.addAll(collapseSnapshot);
            collapseSnapshot.clear();
            render(roots);
            return;
        }
        applyFilter(query);
    }

    // ==================== 建树 ====================

    private void indexNodes(List<ResearchTreeNode> nodes) {
        for (ResearchTreeNode node : nodes) {
            nodesById.put(node.id(), node);
            indexNodes(node.children());
        }
    }

    private String buildNodes(List<ResearchTreeNode> nodes, int level) {
        StringBuilder builder = new StringBuilder();
        for (ResearchTreeNode node : nodes) buildNode(builder, node, level);
        return builder.toString();
    }

    private void buildNode(StringBuilder builder, ResearchTreeNode node, int level) {
        boolean collapsible = !node.children().isEmpty();
        builder.append("<div class=\"tree-node");
        if (collapsible && collapsedIds.contains(node.id())) builder.append(" collapsed");
        builder.append("\">");
        buildRow(builder, node, collapsible, level);
        if (collapsible) {
            builder.append("<div class=\"tree-children\">")
                    .append(buildNodes(node.children(), level + 1)).append("</div>");
        }
        builder.append("</div>");
    }

    /**
     * 行的层级缩进与引导线：每个上级各占一个 {@code .n-guide} 元素，缩进由它撑开、竖线由它内部的
     * {@code .n-guide-line} 画（元素 12px + 行内 gap 4px = 每层 16px）。
     *
     * <p>缩进不能交给容器的 margin/padding：AUI 会把块级子元素的 margin-left 计入两次，且嵌套后代的
     * 宽度基准不随祖先内容盒收缩，容器留白会让每深一层多跑 10px、行右侧内容被滚动容器裁掉；
     * 也不能用 inset box-shadow 画线——AUI 会把它渲染成色块。两条都实测过（AUI 的
     * {@code TreeNestingProbeTest}）。</p>
     *
     * @param level 层号，组头与来源层为 0，其子节点为 1，依此类推
     */
    private static void appendGuides(StringBuilder builder, int level) {
        int count = Math.min(level, DEPTH_CAP + 1);
        for (int guide = 0; guide < count; guide++) {
            builder.append("<span class=\"n-guide\"><span class=\"n-guide-line\"></span></span>");
        }
    }

    private void buildRow(StringBuilder builder, ResearchTreeNode node, boolean collapsible, int level) {
        builder.append("<div class=\"").append(rowClasses(node)).append("\" data-id=\"")
                .append(ResearchHtml.escape(node.id())).append("\">");
        appendGuides(builder, level);
        builder.append("<span class=\"n-toggle").append(collapsible ? "" : " empty").append("\">▾</span>");

        if (node.kind() != ResearchTreeNode.Kind.GROUP) {
            builder.append("<span class=\"n-icon\">");
            if (node.icon() != null) {
                builder.append("<texture class=\"n-icon-tex\" src=\"")
                        .append(ResearchHtml.escape(node.icon())).append("\"></texture>");
            }
            builder.append("</span>");
        }

        builder.append("<span class=\"n-label\">").append(ResearchHtml.escape(node.label())).append("</span>");
        if (node.note() != null) {
            builder.append("<span class=\"n-note\">").append(ResearchHtml.escape(node.note())).append("</span>");
        }
        if (node.kind() == ResearchTreeNode.Kind.GROUP) {
            builder.append("<span class=\"n-progress\">").append(node.progressDone())
                    .append(" / ").append(node.progressTotal()).append("</span>");
        }
        if (node.statusText() != null) {
            builder.append("<span class=\"n-status tone-").append(ResearchHtml.escape(node.tone())).append("\">")
                    .append(ResearchHtml.escape(node.statusText())).append("</span>");
        }
        StringBuilder actions = new StringBuilder();
        for (ResearchTreeNode.Action action : node.actions()) {
            if (!action.inRow()) continue;
            actions.append(ResearchHtml.actionButton(action.type(), action.enabled(), true));
        }
        if (!actions.isEmpty()) {
            builder.append("<span class=\"n-actions\">").append(actions).append("</span>");
        }
        builder.append("</div>");
    }

    private static String rowClasses(ResearchTreeNode node) {
        StringBuilder classes = new StringBuilder("node-row");
        if (node.kind() == ResearchTreeNode.Kind.GROUP) classes.append(" group");
        if (node.unreachable()) classes.append(" unreachable");
        if (node.tone() != null && "bad".equals(node.tone()) && node.kind() == ResearchTreeNode.Kind.BLUEPRINT) {
            classes.append(" broken");
        }
        return classes.toString();
    }

    // ==================== 交互绑定 ====================

    private void cacheElements() {
        nodeElements.clear();
        rowElements.clear();
        for (Element treeNode : container.querySelectorAll(".tree-node")) {
            Element row = treeNode.querySelector(".node-row");
            if (row == null) continue;
            String id = row.getAttribute("data-id");
            if (id == null) continue;
            nodeElements.put(id, treeNode);
            rowElements.put(id, row);
            setClassToken(row, "selected", id.equals(selectedId));
        }
    }

    private void bindInteractions() {
        for (Element row : container.querySelectorAll(".node-row")) {
            String id = row.getAttribute("data-id");
            ResearchTreeNode node = id == null ? null : nodesById.get(id);
            if (node == null) continue;
            row.addEventListener("click", event -> select(node));
            if (!node.children().isEmpty()) {
                Element toggle = row.querySelector(".n-toggle");
                if (toggle != null) {
                    toggle.addEventListener("click", event -> {
                        event.stopPropagation();
                        toggleCollapsed(node.id());
                    });
                }
            }
            bindActionButtons(row, ".btn-icon", node);
            bindActionButtons(row, ".btn-sm", node);
        }
    }

    private void bindActionButtons(Element row, String selector, ResearchTreeNode node) {
        for (Element button : row.querySelectorAll(selector)) {
            ResearchAction action = ResearchAction.byKey(button.getAttribute("data-act"));
            if (action == null || ResearchHtml.hasClass(button, "disabled")) continue;
            button.addEventListener("click", event -> {
                event.stopPropagation();
                onAction.accept(node, action);
            });
        }
    }

    // ==================== 搜索过滤 ====================

    private void applyFilter(String query) {
        for (Element root : container.getChildren()) {
            filterNode(root, query, false);
        }
    }

    private boolean filterNode(Element treeNode, String query, boolean forceVisible) {
        List<Element> children = treeNode.getChildren();
        if (children.isEmpty()) return false;
        Element row = children.getFirst();
        Element childrenBox = children.size() > 1 ? children.get(1) : null;

        boolean self = forceVisible;
        if (!self && !query.isEmpty()) {
            Element label = row.querySelector(".n-label");
            self = label != null && label.getInnerText().toLowerCase(Locale.ROOT).contains(query);
        }
        boolean hit = false;
        if (childrenBox != null) {
            for (Element child : new ArrayList<>(childrenBox.getChildren())) {
                if (filterNode(child, query, self)) hit = true;
            }
        }
        boolean visible = query.isEmpty() || self || hit;
        if (visible) {
            treeNode.removeInlineStyleProperty("display");
        } else {
            treeNode.setInlineStyleProperty("display", "none");
        }
        if (childrenBox != null) setClassToken(treeNode, "collapsed", !visible);
        return visible;
    }

    // ==================== DOM 工具 ====================

    /** 在元素的 class 列表里增删一个类名 */
    public static void setClassToken(Element element, String token, boolean on) {
        String current = element.getAttribute("class");
        Set<String> tokens = new LinkedHashSet<>();
        if (current != null && !current.isBlank()) {
            tokens.addAll(Arrays.asList(current.trim().split("\\s+")));
        }
        if (on) {
            tokens.add(token);
        } else {
            tokens.remove(token);
        }
        element.setAttribute("class", String.join(" ", tokens));
    }
}
