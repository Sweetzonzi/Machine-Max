package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.SupplierStatus;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.LauncherSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.WeaponControllerSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.WeaponControllerSubsystem.LoaderEntry;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 弹药 HUD。<br>
 * 玩家乘坐于座椅时，在屏幕右下角显示所有活跃武器控制器的弹药状态：<br>
 * - 按武器控制器分组，依次展示其下所有发射器的当前弹种、弹药余量、工作状态。<br>
 * - 逐发装填的供给者显示为 1，整体装填显示为供给者余量。<br>
 * - 总剩余弹药为该弹种在所有供给者中的合计。<br>
 * - 第一行显示弹种名 + 计数，第二行仅在装填中时显示进度条 + 百分比。<br>
 * <p>
 * 数据源：从 WeaponController 的 getAmmoPool() / getSelectedBelt() 读取，
 * 而非从 Launcher 的 getSupplierSummaries() 读取。
 */
@OnlyIn(Dist.CLIENT)
public class AmmoHud implements LayeredDraw.Layer {

    // ==================== 颜色常量（可整体替换为夜光绿等主题色） ====================
    /** 主文字色 */
    private static final int COLOR_TEXT         = 0xFFFFFFFF;
    /** 辅助文字色（半透白，用于计数、EMPTY 等） */
    private static final int COLOR_DIM          = 0x80FFFFFF;
    /** 面板背景色 */
    private static final int COLOR_BACKGROUND   = 0x80000000;
    /** 进度条前景色 */
    private static final int COLOR_PROGRESS     = 0xFFFFFFFF;
    /** 进度条背景色 */
    private static final int COLOR_PROGRESS_BG  = 0x40FFFFFF;
    /** READY 状态文字色 */
    private static final int COLOR_READY        = 0xFFFFFFFF;

    // ==================== 布局常量 ====================
    private static final int MARGIN_RIGHT       = 4;
    private static final int MARGIN_BOTTOM      = 80;
    private static final int PADDING            = 4;
    private static final int LINE_HEIGHT        = 10;
    private static final int LINE_GAP           = 1;
    /** 发射器行内两行之间的行距 */
    private static final int INNER_LINE_GAP     = 1;
    /** 武器控制器标题行与下方发射器行之间的缩进宽度 */
    private static final int INDENT             = 8;
    /** 弹药名称与计数之间的间距 */
    private static final int LABEL_COUNT_GAP    = 6;
    /** 进度条宽度（像素） */
    private static final int BAR_WIDTH          = 48;
    /** 进度条高度（像素） */
    private static final int BAR_HEIGHT         = 4;
    /** 进度条与百分比文字间距 */
    private static final int BAR_PCT_GAP        = 2;
    /** 武器控制器标题行之间的额外间距 */
    private static final int SECTION_GAP        = 3;

    @Override
    public void render(@NotNull GuiGraphics gui, @NotNull DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;

        // 仅当玩家坐在座椅上时显示
        if (!(((IEntityMixin) player).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat)) return;
        if (!seat.isActive()) return;

        // 获取当前激活的武器控制器列表（已合并 baseGroup + 当前子组）
        List<WeaponControllerSubsystem> wcs = seat.getActiveWeaponControllers();
        if (wcs.isEmpty()) return;

        // ——— 构建显示行数据 ———
        var lines = new ArrayList<Line>();
        for (WeaponControllerSubsystem wc : wcs) {
            if (wc.isDestroyed() || !wc.isActive()) continue;

            lines.add(new Line(Component.translatable(wc.name).getString(), true, 0, 0, null, 0f));

            for (var entry : wc.getLaunchers().entrySet()) {
                LauncherSubsystem launcher = entry.getKey();
                if (launcher.isDestroyed() || !launcher.isActive()) continue;

                ProjectileType ammoType = launcher.getCurrentAmmoType();
                String ammoLabel = ammoType != null ? ammoType.getRegistryKey().getPath() : "---";

                IAmmoSupplier supplier = launcher.getCurrentSupplier();

                // 从 Controller 的 ammoPool 按选中弹链获取总数
                int totalCount = 0;
                List<ResourceLocation> selectedBelt = wc.getSelectedBelt();
                if (selectedBelt != null) {
                    var entries = wc.getAmmoPool().get(selectedBelt);
                    if (entries != null) {
                        totalCount = entries.stream().mapToInt(LoaderEntry::availableCount).sum();
                    }
                }

                // 当前数量和状态直接从 supplier 读取（不需要 SupplierSummary）
                int curCount = supplier != null ? supplier.getRemainingCount() : 0;
                SupplierStatus status = supplier != null
                        ? supplier.getStatus(launcher) : SupplierStatus.EMPTY;
                float progress = supplier != null
                        ? supplier.getReloadProgress(launcher) : 0f;

                lines.add(new Line(ammoLabel, false, curCount, totalCount, status, progress));
            }
        }

        if (lines.isEmpty()) return;

        // ——— 计算面板尺寸 ———
        int screenW = gui.guiWidth();
        int screenH = gui.guiHeight();
        var font = mc.font;

        int line1Width = 0;  // "弹种名  cur/total" 最大宽度
        for (Line line : lines) {
            int w;
            if (line.header) {
                w = font.width(line.text);
            } else {
                // 第一行：弹种名 + 计数
                w = font.width(line.text) + LABEL_COUNT_GAP + font.width(line.countText());
            }
            if (w > line1Width) line1Width = w;
        }

        // 第二行最大宽度（进度条+百分比 / READY / EMPTY）
        int line2MaxWidth = 0;
        for (Line line : lines) {
            if (!line.header) {
                int w;
                if (line.status == SupplierStatus.RELOADING) {
                    w = font.width(line.text) + LABEL_COUNT_GAP + BAR_WIDTH + BAR_PCT_GAP + font.width("100%");
                } else if (line.status == SupplierStatus.READY) {
                    w = font.width(line.text) + LABEL_COUNT_GAP + font.width("READY");
                } else if (line.status == SupplierStatus.EMPTY) {
                    w = font.width(line.text) + LABEL_COUNT_GAP + font.width("EMPTY");
                } else {
                    w = 0;
                }
                if (w > line2MaxWidth) line2MaxWidth = w;
            }
        }

        // 面板宽度取第一行和第二行的较大值
        int maxRowWidth = Math.max(line1Width, line2MaxWidth);
        int panelW = maxRowWidth + PADDING * 2 + INDENT;
        int panelH = calcPanelHeight(lines);
        int panelX = Math.max(0, screenW - panelW - MARGIN_RIGHT);
        int panelY = Math.max(0, screenH - MARGIN_BOTTOM - panelH);

        // ——— 绘制背景 ———
        gui.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_BACKGROUND);

        // ——— 绘制内容行 ———
        int curY = panelY + PADDING;
        for (Line line : lines) {
            int x = panelX + PADDING + (line.header ? 0 : INDENT);

            if (line.header) {
                gui.drawString(font, line.text, x, curY, COLOR_TEXT, false);
                curY += LINE_HEIGHT + SECTION_GAP;
            } else {
                // ========== 第一行：弹种名 + 计数 ==========
                gui.drawString(font, line.text, x, curY, COLOR_TEXT, false);

                int cx = x + font.width(line.text) + LABEL_COUNT_GAP;
                String countStr = line.countText();
                gui.drawString(font, countStr, cx, curY, COLOR_DIM, false);

                curY += LINE_HEIGHT + INNER_LINE_GAP;

                // ========== 第二行：状态指示（进度条 / READY / EMPTY），左对齐 ==========
                int bx = x;
                if (line.status == SupplierStatus.RELOADING) {
                    int by = curY + (LINE_HEIGHT - BAR_HEIGHT) / 2;
                    gui.fill(bx, by, bx + BAR_WIDTH, by + BAR_HEIGHT, COLOR_PROGRESS_BG);
                    int fill = (int) (BAR_WIDTH * Math.clamp(line.progress, 0f, 1f));
                    gui.fill(bx, by, bx + fill, by + BAR_HEIGHT, COLOR_PROGRESS);
                    String pct = String.format("%d%%", Math.round(line.progress * 100));
                    gui.drawString(font, pct, bx + BAR_WIDTH + BAR_PCT_GAP, curY, COLOR_DIM, false);
                } else if (line.status == SupplierStatus.READY) {
                    gui.drawString(font, "READY", bx, curY, COLOR_READY, false);
                } else if (line.status == SupplierStatus.EMPTY) {
                    gui.drawString(font, "EMPTY", bx, curY, COLOR_DIM, false);
                }

                curY += LINE_HEIGHT + LINE_GAP;
            }
        }
    }

    private static int calcPanelHeight(List<Line> lines) {
        int h = PADDING * 2;
        for (Line line : lines) {
            if (line.header) {
                h += LINE_HEIGHT + SECTION_GAP;
            } else {
                h += LINE_HEIGHT + INNER_LINE_GAP   // 第一行
                   + LINE_HEIGHT + LINE_GAP;        // 第二行
            }
        }
        return h - LINE_GAP; // 最后一行不追加间隙
    }

    // ==================== 行数据 ====================

    private record Line(String text, boolean header, int curCount, int totalCount, SupplierStatus status, float progress) {
        String countText() {
            String total = totalCount >= 0 ? String.valueOf(totalCount) : "\u221E";
            return curCount + "/" + total;
        }
    }
}
