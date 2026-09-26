package io.github.sweetzonzi.machine_max.client.render.gui.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.ApricityScreen;
import io.github.sweetzonzi.machine_max.client.blueprint.BlueprintLibraryClient;
import io.github.sweetzonzi.machine_max.client.render.gui.research.BlueprintLibraryTreeData;
import io.github.sweetzonzi.machine_max.client.render.gui.research.ResearchAction;
import io.github.sweetzonzi.machine_max.client.render.gui.research.ResearchDetailRenderer;
import io.github.sweetzonzi.machine_max.client.render.gui.research.ResearchHtml;
import io.github.sweetzonzi.machine_max.client.render.gui.research.ResearchTreeData;
import io.github.sweetzonzi.machine_max.client.render.gui.research.ResearchTreeNode;
import io.github.sweetzonzi.machine_max.client.render.gui.research.ResearchTreeView;
import io.github.sweetzonzi.machine_max.common.attachment.BlueprintAttachment;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchCompleteRequestPayload;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchTranscribePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 研究台界面：研发与蓝图库两个标签页共用一套可折叠树与详情面板。
 *
 * <p>宿主为 {@link ApricityScreen}：页面由 AUI 创建并绘制，本类只负责生命周期钩子与
 * 「Java 侧渲染 HTML 片段 → 写入容器」的数据装配。注意不能用普通 {@code Screen} 承载
 * Document——AUI 只在没有 Screen 打开时绘制 overlay 文档，打开 Screen 时仅绘制
 * {@code reloadPersistent} 文档，普通 Screen 上的页面不会被画出来。</p>
 */
@OnlyIn(Dist.CLIENT)
public class BlueprintResearchScreen extends ApricityScreen {
    private static final String AUI_DOC_PATH = "machine_max/research/research_ui.html";

    /** 折叠状态与上次选中项：静态字段，跨界面开关保留 */
    private static final Set<String> RESEARCH_COLLAPSED = new LinkedHashSet<>();
    private static final Set<String> LIBRARY_COLLAPSED = new LinkedHashSet<>();
    private static String lastSelectedResearchId;
    private static String lastSelectedLibraryId;
    private static boolean lastLibraryTab;
    /** 首次打开时折叠除首组外的全部组，只做一次 */
    private static boolean researchDefaultsApplied;

    private Document auiDocument;

    private ResearchTreeView researchView;
    private ResearchTreeView libraryView;
    private List<ResearchTreeNode> researchRoots = List.of();
    private String researchSignature = "";
    /** 已写入标签页条的研发点数；Document 重建后置 -1，强制下一 tick 重写 */
    private int shownPoints = -1;
    private boolean libraryScanRunning;

    public BlueprintResearchScreen() {
        super(AUI_DOC_PATH);
        setPauseGame(false);
        // 页面自身不铺底衬（见 research_ui.css 顶部说明），底衬由原版屏幕背景提供
        setShowDefaultBackground(true);
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft == null || minecraft.player == null) {
            onClose();
            return;
        }
        auiDocument = getLinkedDocument();
        if (auiDocument == null) {
            onClose();
            return;
        }
        // 每次 init（含 resize）都会重建 Document，视图缓存与旧元素一起作废
        researchView = null;
        libraryView = null;
        // body 的监听器会被 Document.refresh() 保留并转移到新 body，因此注册一次即可；
        // 开发期热重载重建 DOM 后据此重新填充面板
        if (auiDocument.body != null) {
            auiDocument.body.addEventListener("load", event -> {
                researchView = null;
                libraryView = null;
                initPanels();
            });
        }
        initPanels();
    }

    /** 绑定两个标签页的树、搜索框与工具栏，并填充数据 */
    private void initPanels() {
        if (auiDocument == null) return;
        Player player = player();
        if (player == null) return;
        Element researchTree = auiDocument.getElementById("tree-research");
        Element libraryTree = auiDocument.getElementById("tree-library");
        if (researchTree == null || libraryTree == null) return;

        researchView = new ResearchTreeView(researchTree, RESEARCH_COLLAPSED,
                this::renderResearchDetail,
                (node, action) -> runResearchAction(node, action));
        libraryView = new ResearchTreeView(libraryTree, LIBRARY_COLLAPSED,
                this::renderLibraryDetail,
                this::handleLibraryAction);

        bindTabStrip();
        bindSearchInput("search-research", researchView);
        bindSearchInput("search-library", libraryView);
        bindToolbar("btn-research-expand", researchView, false);
        bindToolbar("btn-research-collapse", researchView, true);
        bindToolbar("btn-library-expand", libraryView, false);
        bindToolbar("btn-library-collapse", libraryView, true);

        researchSignature = "";
        shownPoints = -1;
        libraryScanRunning = false;
        applyTab();
        refreshPoints(researchOf(player));
        rebuildResearch();
        if (lastLibraryTab) {
            BlueprintLibraryClient.rescan(player.level());
            libraryScanRunning = true;
        } else {
            BlueprintLibraryClient.stop();
        }
        rebuildLibrary();
    }

    // ==================== 标签页 ====================

    private void bindTabStrip() {
        Element researchTab = auiDocument.getElementById("tab-btn-research");
        Element libraryTab = auiDocument.getElementById("tab-btn-library");
        if (researchTab != null) researchTab.addEventListener("click", event -> switchTab(false));
        if (libraryTab != null) libraryTab.addEventListener("click", event -> switchTab(true));
    }

    private void switchTab(boolean library) {
        if (lastLibraryTab == library) return;
        lastLibraryTab = library;
        if (library) {
            BlueprintLibraryClient.rescan(level());
            libraryScanRunning = true;
            rebuildLibrary();
        } else {
            BlueprintLibraryClient.stop();
            libraryScanRunning = false;
        }
        applyTab();
    }

    /** 标签页选中态：按钮与页面各切一次 class */
    private void applyTab() {
        setActiveTab("tab-btn-research", "tab-research", !lastLibraryTab);
        setActiveTab("tab-btn-library", "tab-library", lastLibraryTab);
    }

    private void setActiveTab(String buttonId, String pageId, boolean active) {
        Element button = auiDocument.getElementById(buttonId);
        if (button != null) button.setAttribute("class", active ? "tab-item active" : "tab-item");
        Element page = auiDocument.getElementById(pageId);
        if (page != null) page.setAttribute("class", active ? "page active" : "page");
    }

    /**
     * 刷新标签页条右端的自由研发点数。
     *
     * <p>数值与上次写入的一致时不碰 DOM，避免每 tick 触发一次重排。</p>
     *
     * @param research 玩家科研状态
     */
    private void refreshPoints(BlueprintAttachment research) {
        int points = research.getFreeResearchPoint();
        if (points == shownPoints) return;
        Element value = auiDocument.getElementById("tab-points-value");
        if (value == null) return;
        value.setTextContent(Integer.toString(points));
        shownPoints = points;
    }

    // ==================== 搜索与工具栏 ====================

    private void bindSearchInput(String inputId, ResearchTreeView view) {
        Element input = auiDocument.getElementById(inputId);
        if (input == null) return;
        input.setPlaceholder(Component.translatable("gui.machine_max.research.search_placeholder").getString());
        input.addEventListener("input", event -> view.onSearchChanged(input.getValue()));
    }

    private void bindToolbar(String buttonId, ResearchTreeView view, boolean collapse) {
        Element button = auiDocument.getElementById(buttonId);
        if (button == null) return;
        button.addEventListener("click", event -> view.setAllCollapsed(collapse));
    }

    // ==================== 研发标签页 ====================

    /** 重建研发树；折叠状态与选中项按 id 恢复 */
    private void rebuildResearch() {
        Player player = player();
        if (player == null || researchView == null) return;
        BlueprintAttachment research = researchOf(player);
        researchSignature = researchSignature(player, research);
        researchRoots = ResearchTreeData.buildResearchTree(player, research);
        applyDefaultCollapse(researchRoots);
        researchView.render(researchRoots);

        ResearchTreeNode selected = researchView.findNode(lastSelectedResearchId);
        if (selected == null) selected = firstSelectableNode(researchRoots);
        if (selected != null) {
            researchView.select(selected);
        } else {
            setDetailHtml("detail-research", ResearchDetailRenderer.empty());
        }
    }

    /** 首次打开时只展开首组，其余折叠 */
    private void applyDefaultCollapse(List<ResearchTreeNode> roots) {
        if (researchDefaultsApplied) return;
        researchDefaultsApplied = true;
        boolean first = true;
        for (ResearchTreeNode root : roots) {
            if (root.children().isEmpty()) continue;
            if (first) {
                first = false;
                continue;
            }
            RESEARCH_COLLAPSED.add(root.id());
        }
    }

    private void renderResearchDetail(ResearchTreeNode node) {
        lastSelectedResearchId = node.id();
        Player player = player();
        if (player == null || auiDocument == null) return;
        String html = node.kind() == ResearchTreeNode.Kind.GROUP
                ? ResearchDetailRenderer.group(node,
                Component.translatable("gui.machine_max.research.detail.group_hint").getString())
                : ResearchDetailRenderer.research(player, researchOf(player),
                ResearchTreeData.findGroup(researchRoots, node.id()), node);
        setDetailHtml("detail-research", html);
        bindDetailButtons("detail-research", node, researchView, this::runResearchAction);
    }

    private void runResearchAction(ResearchTreeNode node, ResearchAction action) {
        ResourceLocation researchId = ResourceLocation.tryParse(node.id());
        if (researchId == null) return;
        switch (action) {
            case RESEARCH -> PacketDistributor.sendToServer(new ResearchCompleteRequestPayload(researchId));
            case TAKE -> PacketDistributor.sendToServer(new ResearchTranscribePayload(researchId));
            default -> {
            }
        }
    }

    // ==================== 蓝图库标签页 ====================

    private void rebuildLibrary() {
        Player player = player();
        if (player == null || libraryView == null) return;
        List<ResearchTreeNode> roots = BlueprintLibraryTreeData.build(player);
        libraryView.render(roots);
        ResearchTreeNode selected = libraryView.findNode(lastSelectedLibraryId);
        if (selected == null) selected = firstSelectableNode(roots);
        if (selected != null) {
            libraryView.select(selected);
        } else {
            setDetailHtml("detail-library", ResearchDetailRenderer.empty());
        }
    }

    private void renderLibraryDetail(ResearchTreeNode node) {
        lastSelectedLibraryId = node.id();
        Player player = player();
        if (player == null) return;
        setDetailHtml("detail-library", BlueprintLibraryTreeData.detail(player, node));
        bindDetailButtons("detail-library", node, libraryView, this::handleLibraryAction);
    }

    /** 蓝图库动作：取出 / 改名 / 删除 */
    private void handleLibraryAction(ResearchTreeNode node, ResearchAction action) {
        switch (action) {
            case TAKE -> BlueprintLibraryTreeData.take(node);
            case DELETE -> {
                if (BlueprintLibraryTreeData.delete(node)) rebuildLibrary();
            }
            case RENAME -> {
                Element renameBox = auiDocument == null ? null : auiDocument.getElementById("library-rename");
                String name = renameBox == null ? "" : renameBox.getValue();
                if (BlueprintLibraryTreeData.rename(node, name)) rebuildLibrary();
            }
            default -> {
            }
        }
    }

    // ==================== 详情面板 ====================

    private void setDetailHtml(String elementId, String html) {
        Element detail = auiDocument.getElementById(elementId);
        if (detail != null) detail.setInnerHTML(html);
    }

    /**
     * 绑定详情面板底部的按钮。
     *
     * <p>组详情的展开 / 折叠作用于该组自身，其余按钮交给标签页自身的动作处理。</p>
     *
     * @param elementId 详情容器 id
     * @param node      当前选中的节点
     * @param view      该标签页的树视图
     * @param handler   动作分派
     */
    private void bindDetailButtons(String elementId, ResearchTreeNode node, ResearchTreeView view,
                                   BiConsumer<ResearchTreeNode, ResearchAction> handler) {
        Element detail = auiDocument.getElementById(elementId);
        if (detail == null || view == null) return;
        for (Element button : detail.querySelectorAll(".btn-sm")) {
            ResearchAction action = ResearchAction.byKey(button.getAttribute("data-act"));
            if (action == null || ResearchHtml.hasClass(button, "disabled")) continue;
            button.addEventListener("click", event -> {
                switch (action) {
                    case EXPAND -> view.setCollapsed(node.id(), false);
                    case COLLAPSE -> view.setCollapsed(node.id(), true);
                    default -> handler.accept(node, action);
                }
            });
        }
    }

    // ==================== 生命周期 ====================

    @Override
    public void tick() {
        super.tick();
        Player player = player();
        if (auiDocument == null || player == null) return;
        refreshPoints(researchOf(player));
        if (researchView != null && !researchSignature(player, researchOf(player)).equals(researchSignature)) {
            rebuildResearch();
        }
        if (lastLibraryTab && libraryView != null) {
            if (BlueprintLibraryClient.isScanning()) {
                BlueprintLibraryClient.tick(player.level());
                libraryScanRunning = true;
            } else if (libraryScanRunning) {
                libraryScanRunning = false;
                rebuildLibrary();
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 搜索框输入状态下，背包键不应关闭研究台界面
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode) && isSearchFocused()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean isSearchFocused() {
        if (auiDocument == null) return false;
        Element input = auiDocument.getElementById(lastLibraryTab ? "search-library" : "search-research");
        return input != null && Element.isElementFocusing(input);
    }

    @Override
    public void onClose() {
        BlueprintLibraryClient.stop();
        releaseDocument();
        super.onClose();
    }

    @Override
    public void removed() {
        BlueprintLibraryClient.stop();
        releaseDocument();
        super.removed();
    }

    /** 丢弃文档引用；Document 的卸载与移除由 {@link ApricityScreen} 负责 */
    private void releaseDocument() {
        auiDocument = null;
        researchView = null;
        libraryView = null;
    }

    // ==================== 工具 ====================

    @Nullable
    private Player player() {
        return Minecraft.getInstance().player;
    }

    @Nullable
    private net.minecraft.world.level.Level level() {
        return Minecraft.getInstance().level;
    }

    private static BlueprintAttachment researchOf(Player player) {
        return player.getData(MMAttachments.getBLUEPRINT());
    }

    /** 树的选中项签名：组索引版本、研发点、已完成集合与背包内容任一变化都要重建 */
    private static String researchSignature(Player player, BlueprintAttachment research) {
        StringBuilder builder = new StringBuilder();
        builder.append(MMDynamicRes.GROUP_MEMBERS.size()).append('/')
                .append(MMDynamicRes.ALL_RESEARCH_RECIPES.size()).append('/')
                .append(research.getFreeResearchPoint()).append('/')
                .append(research.getCompletedResearches().stream()
                        .map(ResourceLocation::toString).sorted().toList());
        int inventoryHash = 1;
        for (ItemStack stack : player.getInventory().items) {
            inventoryHash = 31 * inventoryHash + ItemStack.hashItemAndComponents(stack);
        }
        builder.append('/').append(inventoryHash);
        return builder.toString();
    }

    /** 取整棵树里第一个可选中的节点：优先条目，其次组 */
    @Nullable
    private static ResearchTreeNode firstSelectableNode(List<ResearchTreeNode> roots) {
        for (ResearchTreeNode root : roots) {
            ResearchTreeNode leaf = firstLeaf(root);
            if (leaf != null) return leaf;
        }
        return roots.isEmpty() ? null : roots.getFirst();
    }

    @Nullable
    private static ResearchTreeNode firstLeaf(ResearchTreeNode node) {
        for (ResearchTreeNode child : node.children()) {
            if (child.kind() != ResearchTreeNode.Kind.GROUP) return child;
            ResearchTreeNode leaf = firstLeaf(child);
            if (leaf != null) return leaf;
        }
        return null;
    }
}
