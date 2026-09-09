package io.github.sweetzonzi.machine_max.client.render.gui.renderable;

import io.github.sweetzonzi.machine_max.client.blueprint.BlueprintLibraryClient;
import io.github.sweetzonzi.machine_max.common.blueprint.BlueprintLibraryServerHelper;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 研究台「蓝图库」标签页的列表控件。
 *
 * <p>分两区呈现：</p>
 * <ul>
 *   <li><b>我的设计</b>：客户端 {@code saved_blueprints/} 的 valid + broken 条目；</li>
 *   <li><b>内容包蓝图</b>：{@code MMDynamicRes.BLUEPRINTS} 中的全部蓝图。</li>
 * </ul>
 *
 * <p>控件只负责展示与点击意图，不参与任何收费 / 发放逻辑；取出走网络包，删除 / 重命名只改本地文件。</p>
 */
public class BlueprintLibraryListWidget extends AbstractScrollWidget {

    /* 扁平化工业风配色（与研发列表保持一致） */
    private static final int BG_ODD = new Color(32, 32, 32).getRGB();
    private static final int BG_EVEN = new Color(40, 40, 40).getRGB();
    private static final int BG_HOVER = new Color(56, 56, 56).getRGB();
    private static final int BG_SELECTED = new Color(72, 92, 72).getRGB();
    private static final int BG_HEADER = new Color(24, 24, 24).getRGB();
    private static final int BG_BROKEN = new Color(52, 32, 32).getRGB();

    private static final int TEXT_NORMAL = new Color(220, 220, 220).getRGB();
    private static final int TEXT_MUTED = new Color(140, 140, 140).getRGB();
    private static final int TEXT_ERROR = new Color(230, 130, 130).getRGB();
    private static final int TEXT_HEADER = new Color(255, 160, 60).getRGB();

    private static final int BTN_EXTRACT = new Color(80, 140, 220).getRGB();
    private static final int BTN_DELETE = new Color(190, 90, 90).getRGB();
    private static final int BTN_RENAME = new Color(150, 150, 90).getRGB();

    private static final int ENTRY_HEIGHT = 28;
    private static final int HEADER_HEIGHT = 12;
    private static final int BUTTON_WIDTH = 14;
    private static final int BUTTON_HEIGHT = 10;

    /** 行类型 */
    public enum RowType {
        /** 分区标题 */
        HEADER,
        /** 玩家本地蓝图 */
        LOCAL,
        /** 内容包蓝图 */
        PACK,
        /** 解析或校验失败 */
        BROKEN
    }

    /**
     * 列表行。
     *
     * @param type    行类型
     * @param label   标题文本（分区标题 / 文件名）
     * @param fileName 本地文件名（LOCAL / BROKEN）
     * @param payload 载具数据（LOCAL / PACK）
     * @param packId  内容包蓝图 id（PACK）
     * @param error   错误条目（BROKEN）
     */
    public record Row(RowType type, Component label, @Nullable String fileName,
                      @Nullable VehicleData payload, @Nullable ResourceLocation packId,
                      @Nullable BlueprintLibraryClient.BlueprintLibraryError error) {
    }

    private final Minecraft minecraft;
    private final List<Row> rows = new ArrayList<>();
    private int selectedIndex = -1;
    /** 当前鼠标悬停的行；tooltip 需在 {@link #renderWidget} 中 PoseStack 复位后再画，否则会被滚动位移带偏 */
    @Nullable
    private Row hoveredRow;

    @Setter
    private Callbacks callbacks;

    public BlueprintLibraryListWidget(Minecraft minecraft, int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.minecraft = minecraft;
    }

    /**
     * 重建行数据：我的设计（valid + broken）在前，内容包蓝图在后。
     *
     * @param valid  校验通过的本地条目
     * @param broken 解析 / 校验失败的本地条目
     * @param level  当前世界，用于统计质量
     */
    public void setData(List<BlueprintLibraryClient.BlueprintLibraryEntry> valid,
                        List<BlueprintLibraryClient.BlueprintLibraryError> broken,
                        @Nullable Level level) {
        rows.clear();

        rows.add(new Row(RowType.HEADER, Component.translatable("gui.machine_max.blueprint_library.section.local"),
                null, null, null, null));
        for (BlueprintLibraryClient.BlueprintLibraryEntry entry : valid) {
            rows.add(new Row(RowType.LOCAL, Component.literal(entry.payload().getName()), entry.fileName(),
                    entry.payload(), null, null));
        }
        for (BlueprintLibraryClient.BlueprintLibraryError error : broken) {
            rows.add(new Row(RowType.BROKEN, Component.literal(error.fileName()), error.fileName(),
                    null, null, error));
        }

        rows.add(new Row(RowType.HEADER, Component.translatable("gui.machine_max.blueprint_library.section.pack"),
                null, null, null, null));
        for (var packEntry : MMDynamicRes.BLUEPRINTS.entrySet()) {
            BlueprintData blueprintData = packEntry.getValue();
            VehicleData template = MMDynamicRes.TEMPLATES.get(blueprintData.getTemplate());
            rows.add(new Row(RowType.PACK, Component.translatable(packEntry.getKey().toLanguageKey()),
                    null, template, packEntry.getKey(), null));
        }

        if (selectedIndex >= rows.size()) {
            selectedIndex = -1;
        }
    }

    /** 当前选中的行 */
    @Nullable
    public Row getSelected() {
        return selectedIndex >= 0 && selectedIndex < rows.size() ? rows.get(selectedIndex) : null;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(g, mouseX, mouseY, partialTick);
        // super 内部已恢复 PoseStack 与裁剪，此时再画 tooltip 才会落在鼠标真实位置
        if (hoveredRow != null) {
            List<Component> tooltip = buildTooltip(hoveredRow, minecraft.level);
            if (!tooltip.isEmpty()) {
                g.renderTooltip(minecraft.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderContents(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Level level = minecraft.level;
        int scroll = (int) scrollAmount();
        int y = getY();
        hoveredRow = null;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rowHeight = rowHeight(row);
            // AbstractScrollWidget 调用本方法前已把 PoseStack 上移 scrollAmount：
            // 这里的 yPos 是内容坐标（直接用于绘制），screenY 才是屏幕坐标（用于鼠标命中判定）
            int yPos = y;
            int screenY = yPos - scroll;
            y += rowHeight;
            if (screenY + rowHeight < getY() || screenY > getY() + height) continue;

            if (row.type() == RowType.HEADER) {
                g.fill(getX(), yPos, getX() + width, yPos + rowHeight, BG_HEADER);
                g.drawString(minecraft.font, row.label(), getX() + 4, yPos + 2, TEXT_HEADER, false);
                continue;
            }

            boolean hovered = isMouseOver(mouseX, mouseY)
                    && mouseY >= screenY && mouseY < screenY + rowHeight;
            if (hovered) this.hoveredRow = row;
            int bg = row.type() == RowType.BROKEN ? BG_BROKEN
                    : (i == selectedIndex) ? BG_SELECTED : hovered ? BG_HOVER : (i % 2 == 0 ? BG_EVEN : BG_ODD);
            g.fill(getX(), yPos, getX() + width, yPos + rowHeight, bg);

            if (row.type() == RowType.BROKEN) {
                g.drawString(minecraft.font, row.label(), getX() + 4, yPos + 4, TEXT_ERROR, false);
                String detail = row.error() != null ? row.error().detail() : "";
                g.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(detail, width - 8),
                        getX() + 4, yPos + 16, TEXT_MUTED, false);
                continue;
            }

            g.drawString(minecraft.font, row.label(), getX() + 4, yPos + 4, TEXT_NORMAL, false);
            g.drawString(minecraft.font, subtitle(row, level), getX() + 4, yPos + 16, TEXT_MUTED, false);

            // 右侧操作按钮
            int btnY = yPos + 3;
            if (row.type() == RowType.LOCAL) {
                drawButton(g, getX() + width - BUTTON_WIDTH - 4, btnY, "改", BTN_RENAME);
                drawButton(g, getX() + width - BUTTON_WIDTH * 2 - 6, btnY, "删", BTN_DELETE);
                drawButton(g, getX() + width - BUTTON_WIDTH * 3 - 8, btnY, "取", BTN_EXTRACT);
            } else {
                drawButton(g, getX() + width - BUTTON_WIDTH - 4, btnY, "取", BTN_EXTRACT);
            }
        }
    }

    /** 副标题：作者 / 零件数 / 质量 / 预估费用 */
    private String subtitle(Row row, @Nullable Level level) {
        VehicleData payload = row.payload();
        if (payload == null) {
            return Component.translatable("gui.machine_max.blueprint_library.template_missing").getString();
        }
        float mass = level != null ? payload.computeDesignMass(level) : 0f;
        int cost = BlueprintLibraryServerHelper.computeCost(mass);
        String author = payload.getMeta().author();
        String authorPart = author.isEmpty() ? "-" : author;
        return authorPart + " | " + payload.getParts().size() + " " +
                Component.translatable("gui.machine_max.blueprint_library.parts").getString()
                + " | " + (int) mass + "kg | " + cost + " RP";
    }

    /** 悬停提示：描述、统计与研发点余额 */
    private List<Component> buildTooltip(Row row, @Nullable Level level) {
        List<Component> lines = new ArrayList<>();
        VehicleData payload = row.payload();
        if (payload == null) return lines;
        if (!payload.getMeta().description().isEmpty()) {
            lines.add(Component.literal(payload.getMeta().description()));
        }
        float mass = level != null ? payload.computeDesignMass(level) : 0f;
        lines.add(Component.translatable("gui.machine_max.blueprint_library.tooltip.stats",
                payload.getParts().size(), (int) mass, BlueprintLibraryServerHelper.computeCost(mass)));
        if (minecraft.player != null) {
            lines.add(Component.translatable("gui.machine_max.blueprint_library.tooltip.balance",
                    minecraft.player.getData(MMAttachments.getBLUEPRINT()).getFreeResearchPoint()));
        }
        return lines;
    }

    private void drawButton(GuiGraphics g, int x, int y, String text, int color) {
        g.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, color);
        g.drawCenteredString(minecraft.font, text, x + BUTTON_WIDTH / 2, y + 1, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!withinContentAreaPoint(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);

        // 定位行
        double contentY = mouseY - getY() + scrollAmount();
        int index = -1;
        int accumulated = 0;
        for (int i = 0; i < rows.size(); i++) {
            int h = rowHeight(rows.get(i));
            if (contentY >= accumulated && contentY < accumulated + h) {
                index = i;
                break;
            }
            accumulated += h;
        }
        if (index < 0) return super.mouseClicked(mouseX, mouseY, button);

        Row row = rows.get(index);
        if (row.type() == RowType.HEADER || row.type() == RowType.BROKEN) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        selectedIndex = index;
        if (callbacks != null) callbacks.onSelect(row);

        int btnY = (int) (getY() + accumulated - scrollAmount() + 3);
        int extractX = row.type() == RowType.LOCAL
                ? getX() + width - BUTTON_WIDTH * 3 - 8
                : getX() + width - BUTTON_WIDTH - 4;
        if (inButton(mouseX, mouseY, extractX, btnY)) {
            if (callbacks != null) callbacks.onExtract(row);
            return true;
        }
        if (row.type() == RowType.LOCAL) {
            int deleteX = getX() + width - BUTTON_WIDTH * 2 - 6;
            int renameX = getX() + width - BUTTON_WIDTH - 4;
            if (inButton(mouseX, mouseY, deleteX, btnY)) {
                if (callbacks != null) callbacks.onDelete(row);
                return true;
            }
            if (inButton(mouseX, mouseY, renameX, btnY)) {
                if (callbacks != null) callbacks.onRename(row);
                return true;
            }
        }
        return true;
    }

    private static boolean inButton(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    private static int rowHeight(Row row) {
        return row.type() == RowType.HEADER ? HEADER_HEIGHT : ENTRY_HEIGHT;
    }

    @Override
    protected int getInnerHeight() {
        int total = 0;
        for (Row row : rows) {
            total += rowHeight(row);
        }
        return total;
    }

    @Override
    protected double scrollRate() {
        return 15;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
    }

    public interface Callbacks {
        void onSelect(Row row);

        void onExtract(Row row);

        void onDelete(Row row);

        void onRename(Row row);
    }
}
