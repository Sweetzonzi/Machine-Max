package io.github.sweetzonzi.machine_max.client.render.gui.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.ApricityScreen;
import io.github.sweetzonzi.machine_max.client.render.gui.pda.PdaHtml;
import io.github.sweetzonzi.machine_max.common.item.prop.PdaData;
import io.github.sweetzonzi.machine_max.common.item.prop.PdaHelper;
import io.github.sweetzonzi.machine_max.common.item.prop.PdaItem;
import io.github.sweetzonzi.machine_max.network.payload.pda.PdaBindShortcutPayload;
import io.github.sweetzonzi.machine_max.network.payload.pda.PdaDepositPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 蓝图终端管理界面：已收纳列表、背包蓝图列表与设计模式快捷栏。
 *
 * <p>宿主为 {@link ApricityScreen}：页面由 AUI 创建并绘制，本类只负责生命周期钩子、DOM 装配与交互发包，
 * HTML 片段由 {@link PdaHtml} 生成，与研究台界面同构。</p>
 *
 * <p>界面不持任何权威状态，只做展示与发包：存入与绑定都发往服务端执行，由服务端改写数据组件后触发
 * 物品同步，客户端据数据签名变化重建列表。{@code selected} 与 {@code designMode} 不是本界面的写入对象，
 * 本界面只改 {@code shortcuts}。</p>
 */
@OnlyIn(Dist.CLIENT)
public class PdaScreen extends ApricityScreen {
    private static final String AUI_DOC_PATH = "machine_max/pda/pda_ui.html";

    /** 打开本界面所用的手；界面内所有发包都带上它。 */
    private final InteractionHand hand;
    private Document auiDocument;
    /** 上次渲染的数据签名；未变化时不重建 DOM。 */
    private String shownSignature = "";
    /** 待绑定条目的配方 id；{@code null} 表示无。 */
    @Nullable
    private String pendingEntry;
    /** 待绑定格位序号；{@code -1} 表示无。 */
    private int pendingSlot = -1;

    public PdaScreen(InteractionHand hand) {
        super(AUI_DOC_PATH);
        this.hand = hand;
        setPauseGame(false);
        setShowDefaultBackground(true);
    }

    /**
     * 打开管理界面。装载该界面的调用方位于共通代码，通过 {@link PdaItem#setScreenOpener} 注入本方法，
     * 界面类本身因此不出现在共通类的常量池里。
     */
    public static void open(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new PdaScreen(hand));
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
        // body 的监听器会被 Document.refresh() 保留并转移到新 body，因此注册一次即可；
        // 开发期热重载重建 DOM 后据此重新填充面板
        if (auiDocument.body != null) {
            auiDocument.body.addEventListener("load", event -> {
                shownSignature = "";
                rebuild();
            });
        }
        bindStaticControls();
        shownSignature = "";
        rebuild();
    }

    @Override
    public void tick() {
        super.tick();
        rebuild();
    }

    @Override
    public void onClose() {
        releaseDocument();
        super.onClose();
    }

    @Override
    public void removed() {
        releaseDocument();
        super.removed();
    }

    /** 丢弃文档引用；Document 的卸载与移除由 {@link ApricityScreen} 负责。 */
    private void releaseDocument() {
        auiDocument = null;
        pendingEntry = null;
        pendingSlot = -1;
    }

    // ==================== 渲染 ====================

    /** 按数据签名决定是否重建 DOM；签名与上一次相同则不动 DOM。 */
    private void rebuild() {
        if (auiDocument == null) return;
        Player player = player();
        Level level = level();
        ItemStack stack = pdaStack();
        if (player == null || level == null || stack == null) return;
        PdaData data = PdaHelper.getData(stack);
        String signature = signature(data, player);
        if (signature.equals(shownSignature)) return;
        shownSignature = signature;
        // 数据被服务端改写：待绑定状态一并作废
        pendingEntry = null;
        pendingSlot = -1;
        renderCounts(data);
        renderShortcutBar(data, level);
        renderEntryList(data, level);
        renderInventoryList(data, player, level);
        setStatus("");
    }

    /** 一键存入全部按钮：容器本身不重建，监听只挂一次。 */
    private void bindStaticControls() {
        Element depositAll = auiDocument.getElementById("pda-btn-deposit-all");
        if (depositAll != null) depositAll.addEventListener("click", event -> depositAll());
    }

    private void renderCounts(PdaData data) {
        int total = data.entries().size();
        int infinite = 0;
        for (Integer uses : data.entries().values()) {
            if (uses != null && PdaData.isInfinite(uses)) infinite++;
        }
        setText("pda-count-stored", PdaHtml.translatable("gui.machine_max.pda.count.stored", total));
        setText("pda-count-infinite", PdaHtml.translatable("gui.machine_max.pda.count.infinite", infinite));
        setText("pda-count-limited", PdaHtml.translatable("gui.machine_max.pda.count.limited", total - infinite));
    }

    private void renderShortcutBar(PdaData data, Level level) {
        Element bar = auiDocument.getElementById("pda-shortcut-bar");
        if (bar == null) return;
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < PdaData.SHORTCUT_COUNT; i++) {
            html.append(PdaHtml.shortcutSlot(i, data.shortcutAt(i), i == data.selected(), level));
        }
        bar.setInnerHTML(html.toString());
        for (int i = 0; i < PdaData.SHORTCUT_COUNT; i++) {
            Element slot = auiDocument.getElementById("pda-shortcut-" + i);
            if (slot == null) continue;
            final int index = i;
            slot.addEventListener("click", event -> onSlotClick(index));
        }
    }

    private void renderEntryList(PdaData data, Level level) {
        Element list = auiDocument.getElementById("pda-entry-list");
        if (list == null) return;
        StringBuilder html = new StringBuilder();
        for (Map.Entry<ResourceLocation, Integer> entry : data.entries().entrySet()) {
            ResourceLocation recipeId = entry.getKey();
            html.append(PdaHtml.entryRow(recipeId, entry.getValue(),
                    PdaHtml.viewOf(level, recipeId), isBound(data, recipeId)));
        }
        list.setInnerHTML(html.toString());
        for (Element row : list.querySelectorAll(".row")) {
            String target = row.getAttribute("data-target");
            if (target == null) continue;
            row.addEventListener("click", event -> onEntryClick(target));
        }
        for (Element unbind : list.querySelectorAll("[data-act=\"" + PdaHtml.ACT_UNBIND + "\"]")) {
            Element row = unbind.closest(".row");
            if (row == null) continue;
            String target = row.getAttribute("data-target");
            if (target == null) continue;
            unbind.addEventListener("click", event -> {
                event.stopPropagation();
                onUnbindClick(target);
            });
        }
    }

    private void renderInventoryList(PdaData data, Player player, Level level) {
        Element list = auiDocument.getElementById("pda-inventory-list");
        if (list == null) return;
        StringBuilder html = new StringBuilder();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ResourceLocation recipeId = PdaHelper.recipeIdOf(inventory.items.get(slot), level);
            if (recipeId == null) continue;
            html.append(PdaHtml.inventoryRow(recipeId, slot,
                    PdaHtml.viewOf(level, recipeId), data.usesOf(recipeId)));
        }
        list.setInnerHTML(html.toString());
        for (Element button : list.querySelectorAll("[data-act=\"" + PdaHtml.ACT_DEPOSIT + "\"]")) {
            String slotAttr = button.getAttribute("data-slot");
            if (slotAttr == null) continue;
            final int slot;
            try {
                slot = Integer.parseInt(slotAttr);
            } catch (NumberFormatException ignored) {
                continue;
            }
            button.addEventListener("click", event -> depositSlot(slot));
        }
    }

    // ==================== 交互 ====================

    /** 点击已收纳条目：先选条目再选格位；若已有待绑定格位则直接绑定。 */
    private void onEntryClick(String recipeId) {
        Level level = level();
        ResourceLocation id = ResourceLocation.tryParse(recipeId);
        if (level == null || id == null) return;
        if (pendingSlot >= 0) {
            int slot = pendingSlot;
            pendingSlot = -1;
            markSlotArmed(-1);
            markPicked(null);
            sendBind(slot, id);
            setStatus("");
            return;
        }
        if (recipeId.equals(pendingEntry)) {
            pendingEntry = null;
            markPicked(null);
            setStatus("");
            return;
        }
        pendingEntry = recipeId;
        markPicked(recipeId);
        setStatus(PdaHtml.translatable("gui.machine_max.pda.status.select_entry",
                PdaHtml.viewOf(level, id).name()));
    }

    /** 点击格位：若已有待绑定条目则绑定；否则进入待绑定格位态。 */
    private void onSlotClick(int index) {
        if (pendingEntry != null) {
            ResourceLocation id = ResourceLocation.tryParse(pendingEntry);
            pendingEntry = null;
            markPicked(null);
            if (id != null) sendBind(index, id);
            setStatus("");
            return;
        }
        if (pendingSlot == index) {
            pendingSlot = -1;
            markSlotArmed(-1);
            setStatus("");
            return;
        }
        pendingSlot = index;
        markSlotArmed(index);
        setStatus(PdaHtml.translatable("gui.machine_max.pda.status.select_slot", index + 1));
    }

    /** 解绑：清除所有引用该条目的格位。 */
    private void onUnbindClick(String recipeId) {
        ItemStack stack = pdaStack();
        ResourceLocation id = ResourceLocation.tryParse(recipeId);
        if (stack == null || id == null) return;
        PdaData data = PdaHelper.getData(stack);
        for (int i = 0; i < PdaData.SHORTCUT_COUNT; i++) {
            if (id.equals(data.shortcutAt(i))) {
                PacketDistributor.sendToServer(new PdaBindShortcutPayload(hand, i, Optional.empty()));
            }
        }
        pendingEntry = null;
        pendingSlot = -1;
        markPicked(null);
        markSlotArmed(-1);
        setStatus("");
    }

    private void depositSlot(int slot) {
        PacketDistributor.sendToServer(new PdaDepositPayload(hand, List.of(slot), false));
    }

    private void depositAll() {
        PacketDistributor.sendToServer(new PdaDepositPayload(hand, List.of(), true));
    }

    private void sendBind(int slot, ResourceLocation recipeId) {
        PacketDistributor.sendToServer(new PdaBindShortcutPayload(hand, slot, Optional.of(recipeId)));
    }

    // ==================== DOM 辅助 ====================

    private void markPicked(@Nullable String recipeId) {
        if (auiDocument == null) return;
        for (Element row : auiDocument.querySelectorAll("#pda-entry-list .row")) {
            setClass(row, "picked", recipeId != null && recipeId.equals(row.getAttribute("data-target")));
        }
    }

    private void markSlotArmed(int index) {
        if (auiDocument == null) return;
        for (int i = 0; i < PdaData.SHORTCUT_COUNT; i++) {
            Element slot = auiDocument.getElementById("pda-shortcut-" + i);
            if (slot != null) setClass(slot, "armed", i == index);
        }
    }

    private void setText(String elementId, String text) {
        Element element = auiDocument.getElementById(elementId);
        if (element != null) element.setTextContent(text);
    }

    private void setStatus(String text) {
        setText("pda-status", text == null ? "" : text);
    }

    private static void setClass(Element element, String className, boolean present) {
        if (present) {
            element.getClassList().add(className);
        } else {
            element.getClassList().remove(className);
        }
    }

    // ==================== 数据 ====================

    private static boolean isBound(PdaData data, ResourceLocation recipeId) {
        for (int i = 0; i < PdaData.SHORTCUT_COUNT; i++) {
            if (recipeId.equals(data.shortcutAt(i))) return true;
        }
        return false;
    }

    /**
     * 数据签名：{@code entries} 内容、{@code shortcuts} 内容、{@code selected}、{@code designMode}
     * 与背包物品哈希任一变化都要重建列表。{@code shortcuts} 逐格拼接以避免映射迭代顺序带来的抖动。
     */
    private static String signature(PdaData data, Player player) {
        StringBuilder builder = new StringBuilder();
        builder.append(data.selected()).append('/').append(data.designMode()).append('/')
                .append(data.entries()).append('/');
        for (int i = 0; i < PdaData.SHORTCUT_COUNT; i++) {
            builder.append(i).append('=').append(data.shortcutAt(i)).append(';');
        }
        int inventoryHash = 1;
        for (ItemStack stack : player.getInventory().items) {
            inventoryHash = 31 * inventoryHash + ItemStack.hashItemAndComponents(stack);
        }
        builder.append('/').append(inventoryHash);
        return builder.toString();
    }

    @Nullable
    private ItemStack pdaStack() {
        Player player = player();
        if (player == null) return null;
        ItemStack stack = player.getItemInHand(hand);
        return PdaHelper.isPda(stack) ? stack : null;
    }

    @Nullable
    private Player player() {
        return Minecraft.getInstance().player;
    }

    @Nullable
    private Level level() {
        return Minecraft.getInstance().level;
    }
}
