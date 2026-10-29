package io.github.sweetzonzi.machine_max.client.render.gui.creative;

import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import io.github.sweetzonzi.machine_max.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 系列选择弹层：顶部一个搜索框，下面是固定最大高度的滚动列表。
 *
 * <p>弹层的输入不经过屏幕的控件系统：{@link CreativePartBrowser} 在键盘、字符、滚轮与鼠标
 * 入口把事件直接交给本类，因此弹层打开期间原版主搜索框、快捷栏数字键与物品槽位都收不到输入。
 * 列表首行固定是「全部系列」，其余按完整标签 ID 排序。</p>
 *
 * <p>弹层自己的搜索只匹配系列 ID 与译名，不修改主搜索条件；每次展开时清空。</p>
 *
 * <p>线程：主线程（20tps）。</p>
 */
final class SeriesPickerWidget {

    private static final int WIDTH = 150;
    private static final int EDIT_HEIGHT = 12;
    private static final int ROW_HEIGHT = 11;
    private static final int DEFAULT_ROWS = 6;
    private static final int PADDING = 3;
    private static final int BACKGROUND = 0xF0101010;
    private static final int BORDER = 0xFFB0B0B0;
    private static final int ROW_HIGHLIGHT = 0xE02E6FBF;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int DISABLED_TEXT_COLOR = 0xFF808080;

    private final CreativePartBrowser browser;
    private final CreativeModeInventoryScreen screen;
    private final EditBox queryBox;

    private final List<ResourceLocation> allOptions = new ArrayList<>();
    /** 当前可见行：首个元素为 {@code null}，表示「全部系列」。 */
    private final List<ResourceLocation> entries = new ArrayList<>();

    private boolean open;
    private int x;
    private int y;
    private int height;
    private int visibleRows;
    private int firstRow;
    private int selectedRow;

    SeriesPickerWidget(CreativePartBrowser browser, CreativeModeInventoryScreen screen) {
        this.browser = browser;
        this.screen = screen;
        Font font = Minecraft.getInstance().font;
        this.queryBox = new EditBox(font, 0, 0, WIDTH - PADDING * 2, EDIT_HEIGHT,
                MMLanguageRegistry.CREATIVE_SERIES_SEARCH.translated());
        this.queryBox.setMaxLength(CreativePartBrowser.SEARCH_MAX_LENGTH);
        this.queryBox.setTextColor(0xFFFFFFFF);
    }

    boolean isOpen() {
        return this.open;
    }

    boolean isMouseOver(double mouseX, double mouseY) {
        return this.open
                && mouseX >= this.x && mouseX < this.x + WIDTH
                && mouseY >= this.y && mouseY < this.y + this.height;
    }

    /** 展开弹层；列表成员来自本轮完整候选的系列选项。 */
    void open(List<ResourceLocation> options, @Nullable ResourceLocation selected) {
        this.allOptions.clear();
        this.allOptions.addAll(options);
        this.queryBox.setValue("");
        this.browser.suspendMainSearchFocus();
        this.queryBox.setFocused(true);
        this.rebuildEntries();

        this.x = Mth.clamp(this.screen.getGuiLeft() + 8, 2, Math.max(2, this.screen.width - WIDTH - 2));
        int anchorY = this.screen.getGuiTop() + 17;
        int maxRows = Math.max(1, Math.min(DEFAULT_ROWS,
                (this.screen.height - anchorY - EDIT_HEIGHT - PADDING * 3) / ROW_HEIGHT));
        this.visibleRows = Math.min(maxRows, Math.max(1, this.entries.size()));
        this.y = Mth.clamp(anchorY, 2, Math.max(2, this.screen.height - this.heightOf() - 2));
        this.height = this.heightOf();

        this.firstRow = 0;
        this.selectedRow = 0;
        if (selected != null) {
            int index = this.entries.indexOf(selected);
            if (index >= 0) this.selectedRow = index;
        }
        this.ensureSelectionVisible();
        this.open = true;
    }

    void close() {
        if (!this.open) return;
        this.open = false;
        this.queryBox.setFocused(false);
        this.browser.restoreMainSearchFocus();
    }

    private int heightOf() {
        return EDIT_HEIGHT + PADDING * 2 + this.visibleRows * ROW_HEIGHT;
    }

    private int listTop() {
        return this.y + EDIT_HEIGHT + PADDING;
    }

    /** 系列选项随目录变化时同步列表；弹层关闭时只保存。 */
    void onOptionsChanged(List<ResourceLocation> options) {
        this.allOptions.clear();
        this.allOptions.addAll(options);
        if (!this.open) return;
        ResourceLocation selected = this.selectedEntry();
        this.rebuildEntries();
        int index = selected == null ? 0 : this.entries.indexOf(selected);
        this.selectedRow = Math.max(0, index);
        this.firstRow = Mth.clamp(this.firstRow, 0, Math.max(0, this.entries.size() - this.visibleRows));
        this.ensureSelectionVisible();
    }

    private void rebuildEntries() {
        List<String> words = CreativePartBrowser.tokenize(this.queryBox.getValue());
        this.entries.clear();
        this.entries.add(null);
        for (ResourceLocation option : this.allOptions) {
            if (!words.isEmpty()) {
                List<String> fields = List.of(
                        TextUtil.normalize(option.toString()),
                        TextUtil.normalize(TextUtil.getTranslatedOrRawTag(option)));
                if (!CreativePartBrowser.matchesAllWords(fields, words)) continue;
            }
            this.entries.add(option);
        }
    }

    private @Nullable ResourceLocation selectedEntry() {
        if (this.selectedRow < 0 || this.selectedRow >= this.entries.size()) return null;
        return this.entries.get(this.selectedRow);
    }

    private void ensureSelectionVisible() {
        if (this.selectedRow < this.firstRow) this.firstRow = this.selectedRow;
        int lastVisible = this.firstRow + this.visibleRows - 1;
        if (this.selectedRow > lastVisible) this.firstRow = this.selectedRow - this.visibleRows + 1;
        this.firstRow = Mth.clamp(this.firstRow, 0, Math.max(0, this.entries.size() - this.visibleRows));
    }

    private void moveSelection(int delta) {
        if (this.entries.isEmpty()) return;
        this.selectedRow = Mth.clamp(this.selectedRow + delta, 0, this.entries.size() - 1);
        this.ensureSelectionVisible();
    }

    private void applySelected() {
        this.browser.applySeries(this.selectedEntry());
    }

    // ==================== 输入 ====================

    /** @return 点击是否落在弹层内 */
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.open) return false;
        if (!isMouseOver(mouseX, mouseY)) return false;
        if (mouseX >= this.x + PADDING && mouseX < this.x + WIDTH - PADDING
                && mouseY >= this.y + PADDING && mouseY < this.y + PADDING + EDIT_HEIGHT) {
            this.queryBox.setFocused(true);
            this.queryBox.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        int listTop = this.listTop();
        if (mouseY >= listTop && mouseY < listTop + this.visibleRows * ROW_HEIGHT) {
            int row = this.firstRow + (int) ((mouseY - listTop) / ROW_HEIGHT);
            if (row >= 0 && row < this.entries.size()) {
                this.selectedRow = row;
                this.applySelected();
            }
        }
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!this.open) return false;
        if (scrollY > 0) this.firstRow--;
        else if (scrollY < 0) this.firstRow++;
        this.firstRow = Mth.clamp(this.firstRow, 0, Math.max(0, this.entries.size() - this.visibleRows));
        return true;
    }

    void keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case 256 -> this.close();
            case 265 -> this.moveSelection(-1);
            case 264 -> this.moveSelection(1);
            case 257, 335 -> {
                if (!this.entries.isEmpty()) this.applySelected();
            }
            default -> {
                String before = this.queryBox.getValue();
                this.queryBox.keyPressed(keyCode, scanCode, modifiers);
                if (!before.equals(this.queryBox.getValue())) {
                    this.rebuildEntries();
                    this.selectedRow = Mth.clamp(this.selectedRow, 0, Math.max(0, this.entries.size() - 1));
                    this.firstRow = Mth.clamp(this.firstRow, 0, Math.max(0, this.entries.size() - this.visibleRows));
                    this.ensureSelectionVisible();
                }
            }
        }
    }

    void charTyped(char codePoint, int modifiers) {
        String before = this.queryBox.getValue();
        if (this.queryBox.charTyped(codePoint, modifiers) && !before.equals(this.queryBox.getValue())) {
            this.rebuildEntries();
            this.selectedRow = Mth.clamp(this.selectedRow, 0, Math.max(0, this.entries.size() - 1));
            this.firstRow = Mth.clamp(this.firstRow, 0, Math.max(0, this.entries.size() - this.visibleRows));
            this.ensureSelectionVisible();
        }
    }

    // ==================== 绘制 ====================

    void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!this.open) return;
        Font font = Minecraft.getInstance().font;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 400.0F);

        int right = this.x + WIDTH;
        int bottom = this.y + this.height;
        guiGraphics.fill(this.x, this.y, right, bottom, BACKGROUND);
        guiGraphics.fill(this.x, this.y, right, this.y + 1, BORDER);
        guiGraphics.fill(this.x, bottom - 1, right, bottom, BORDER);
        guiGraphics.fill(this.x, this.y, this.x + 1, bottom, BORDER);
        guiGraphics.fill(right - 1, this.y, right, bottom, BORDER);

        this.queryBox.setX(this.x + PADDING);
        this.queryBox.setY(this.y + PADDING);
        this.queryBox.render(guiGraphics, mouseX, mouseY, 0.0F);

        int listTop = this.listTop();
        int hoveredRow = -1;
        if (mouseX >= this.x && mouseX < right && mouseY >= listTop && mouseY < listTop + this.visibleRows * ROW_HEIGHT) {
            hoveredRow = this.firstRow + (int) ((mouseY - listTop) / ROW_HEIGHT);
        }
        for (int i = 0; i < this.visibleRows; i++) {
            int index = this.firstRow + i;
            if (index >= this.entries.size()) break;
            int rowY = listTop + i * ROW_HEIGHT;
            if (index == this.selectedRow || index == hoveredRow) {
                guiGraphics.fill(this.x + 1, rowY - 1, right - 1, rowY + ROW_HEIGHT - 1, ROW_HIGHLIGHT);
            }
            String label = this.labelOf(this.entries.get(index)).getString();
            String text = font.plainSubstrByWidth(label, WIDTH - PADDING * 2 - 2);
            guiGraphics.drawString(font, text, this.x + PADDING + 1, rowY,
                    index == this.selectedRow ? 0xFFFFFFFF : TEXT_COLOR, false);
            if (index == hoveredRow && text.length() < label.length()) {
                guiGraphics.renderTooltip(font, Component.literal(label), mouseX, mouseY);
            }
        }
        if (this.entries.size() > this.visibleRows) {
            guiGraphics.drawString(font, "▼", right - 10, bottom - 10, DISABLED_TEXT_COLOR, false);
        }

        guiGraphics.pose().popPose();
    }

    private Component labelOf(@Nullable ResourceLocation series) {
        if (series == null) return MMLanguageRegistry.CREATIVE_ALL_SERIES.translated();
        return Component.literal(TextUtil.getTranslatedOrRawTag(series));
    }
}
