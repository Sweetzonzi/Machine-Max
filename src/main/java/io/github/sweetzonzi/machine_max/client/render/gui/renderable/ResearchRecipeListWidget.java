package io.github.sweetzonzi.machine_max.client.render.gui.renderable;

import io.github.sweetzonzi.machine_max.client.render.gui.screen.ResearchState;
import io.github.sweetzonzi.machine_max.common.item.prop.FabricatingBlueprintItem;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 科研配方列表 Widget
 * <p>
 * UI 仅用于展示科研状态与点击意图，
 * 不参与任何科研逻辑计算。
 */
public class ResearchRecipeListWidget extends AbstractScrollWidget {

    /* =========================
     *  扁平化工业风配色
     * ========================= */

    private static final int BG_ODD = new Color(32, 32, 32).getRGB();
    private static final int BG_EVEN = new Color(40, 40, 40).getRGB();
    private static final int BG_HOVER = new Color(56, 56, 56).getRGB();
    private static final int BG_SELECTED = new Color(72, 92, 72).getRGB();

    private static final int TEXT_NORMAL = new Color(220, 220, 220).getRGB();
    private static final int TEXT_MUTED = new Color(140, 140, 140).getRGB();

    private static final int PROGRESS_BG = new Color(40, 40, 40).getRGB();
    private static final int PROGRESS_FG_ACTIVATE = new Color(250, 200, 100).getRGB();
    private static final int PROGRESS_FG_ON_HOLD = new Color(150, 150, 150).getRGB();

    private static final int BTN_ACTIVE = new Color(80, 140, 220).getRGB();
    private static final int BTN_ACCELERATE = new Color(220, 100, 0, 255).getRGB();
    private static final int BTN_INACTIVE = new Color(72, 72, 72).getRGB();
    private static final int BTN_CANCEL = new Color(200, 80, 80).getRGB();
    private static final int BTN_CLAIM = new Color(120, 200, 120).getRGB();
    private static final int BTN_RECLAIM = new Color(200, 160, 80).getRGB();

    /* ========================= */

    private static final int ENTRY_HEIGHT = 28;
    private static final int BUTTON_WIDTH = 10;
    private static final int BUTTON_HEIGHT = 10;

    private final Minecraft minecraft;
    private final List<ResearchState> states = new ArrayList<>();

    private int selectedIndex = -1;
    private ResearchState hovered = null;
    @Setter
    private Callbacks callbacks;

    public ResearchRecipeListWidget(Minecraft minecraft, int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.minecraft = minecraft;
    }

    /* =========================
     *  数据注入
     * ========================= */

    public void setStates(List<ResearchState> list) {
        states.clear();
        states.addAll(list);
    }

    public ResearchState getSelected() {
        return selectedIndex >= 0 && selectedIndex < states.size()
                ? states.get(selectedIndex)
                : null;
    }

    /* ========================= */

    @Override
    protected void renderContents(
            @NotNull GuiGraphics g,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        for (int i = 0; i < states.size(); i++) {
            ResearchState state = states.get(i);
            RecipeHolder<FabricatingRecipe> holder = state.recipe();
            ItemStack result = holder.value()
                    .getResultItem(minecraft.level.registryAccess());

            int yPos = getY() + i * ENTRY_HEIGHT;
            boolean hoveredRow = isMouseOver(mouseX, mouseY)
                    && mouseY >= yPos - scrollAmount()
                    && mouseY < yPos + ENTRY_HEIGHT - scrollAmount();

            // === 背景 ===
            int bg = (i == selectedIndex)
                    ? BG_SELECTED
                    : hoveredRow ? BG_HOVER : (i % 2 == 0 ? BG_EVEN : BG_ODD);
            g.fill(getX(), yPos, getX() + width, yPos + ENTRY_HEIGHT, bg);

            // === 图标 ===
            g.renderItem(result, getX() + 4, yPos + 6);

            // === 名称 + 版本号 ===
            Component name = Component.translatable(holder.id().toLanguageKey())
                    .append(FabricatingBlueprintItem.buildVersion(Math.max(0, state.researchLevel() - 1)));
            int nameColor = state.unlocked()
                    ? TEXT_NORMAL
                    : TEXT_MUTED;

            g.drawString(
                    minecraft.font,
                    name,
                    getX() + 26,
                    yPos + 4,
                    nameColor,
                    false
            );

            // === 进度条 ===
            int barX = getX() + 26;
            int barY = yPos + 22;
            int barW = width - 30;

            g.fill(barX, barY, barX + barW, barY + 4, PROGRESS_BG);
            g.fill(
                    barX,
                    barY,
                    barX + (int) (barW * Mth.clamp(state.levelProgress(), 0f, 1f)),
                    barY + 4,
                    state.researching() ? PROGRESS_FG_ACTIVATE : PROGRESS_FG_ON_HOLD
            );

            String rpText = state.currentRp() + "/" + state.requiredRp();
            g.pose().pushPose();
            g.pose().translate(barX + barW - minecraft.font.width(rpText) * 0.7f, barY - 6, 0);
            g.pose().scale(0.7f, 0.7f, 1);
            g.drawString(
                    minecraft.font,
                    rpText,
                    0,
                    0,
                    TEXT_MUTED,
                    false
            );
            g.pose().popPose();

            // === 按钮区域 ===
            int btn1X = getX() + width - 3 * (BUTTON_WIDTH + 4);
            int btn2X = getX() + width - 2 * (BUTTON_WIDTH + 4);
            int btn3X = getX() + width - BUTTON_WIDTH - 4;
            int btnY = yPos + 3;

            // ---- 按钮 1：开始 / 取消 ----
            boolean researching = state.researching();
            boolean canStart = state.canResearch();

            int btn1Color = researching
                    ? BTN_CANCEL
                    : canStart ? BTN_ACTIVE : BTN_INACTIVE;

            g.fill(btn1X, btnY, btn1X + BUTTON_WIDTH, btnY + BUTTON_HEIGHT, btn1Color);
            g.drawCenteredString(
                    minecraft.font,
                    researching ? "⏸" : state.started() ? (state.researchLevel() >= 1 ? "↑" : "▶") : "\uD83D\uDD2C",
                    btn1X + BUTTON_WIDTH / 2,
                    btnY + 1,
                    0xFFFFFF
            );

            // ---- 按钮 2：应用自由研发点 ----

            int btn2Color = state.currentFreeRp() > 0 && state.started() ? BTN_ACCELERATE : BTN_INACTIVE;

            g.fill(btn2X, btnY, btn2X + BUTTON_WIDTH, btnY + BUTTON_HEIGHT, btn2Color);
            g.drawCenteredString(
                    minecraft.font,
                    "⏭",
                    btn2X + BUTTON_WIDTH / 2,
                    btnY + 1,
                    0xFFFFFF
            );

            // ---- 按钮 3：获取 / 重新获取 ----
            boolean hasProduct = state.hasProduct();
            boolean canReclaim = state.canReclaim();

            int btn3Color = hasProduct
                    ? BTN_CLAIM
                    : canReclaim ? BTN_RECLAIM : BTN_INACTIVE;

            g.fill(btn3X, btnY, btn3X + BUTTON_WIDTH, btnY + BUTTON_HEIGHT, btn3Color);
            g.drawCenteredString(
                    minecraft.font,
                    hasProduct ? "↓" : "+",
                    btn3X + BUTTON_WIDTH / 2,
                    btnY + 1,
                    0xFFFFFF
            );

            if (hoveredRow) this.hovered = state;
        }
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        int index = (int) ((mouseY - getY() + scrollAmount()) / ENTRY_HEIGHT);
        int btn1X = getX() + width - 3 * (BUTTON_WIDTH + 4);
        int btn2X = getX() + width - 2 * (BUTTON_WIDTH + 4);
        int btn3X = getX() + width - BUTTON_WIDTH - 4;
        if (withinContentAreaPoint(mouseX, mouseY)) {
            ResearchState state = states.get(index);
            // 按钮 1
            if (mouseX >= btn1X && mouseX < btn1X + BUTTON_WIDTH) {
                if (state.researching()) {
                    guiGraphics.renderComponentTooltip(minecraft.font, List.of(
                            Component.translatable("gui.machine_max.research.recipe.on_hold_1"),
                            Component.translatable("gui.machine_max.research.recipe.on_hold_2").withColor(TEXT_MUTED)
                    ), mouseX, mouseY);
                } else if (state.canResearch()) {
                    if (!state.unlocked() && !state.started()) {
                        guiGraphics.renderComponentTooltip(minecraft.font, List.of(
                                Component.translatable("gui.machine_max.research.recipe.start_1"),
                                Component.translatable("gui.machine_max.research.recipe.start_2").withColor(TEXT_MUTED)
                        ), mouseX, mouseY);
                    } else {
                        guiGraphics.renderComponentTooltip(minecraft.font, List.of(
                                Component.translatable("gui.machine_max.research.recipe.continue"),
                                Component.translatable("gui.machine_max.research.recipe.start_2").withColor(TEXT_MUTED)
                        ), mouseX, mouseY);
                    }
                } else {
                    guiGraphics.renderComponentTooltip(minecraft.font, List.of(
                            Component.translatable("gui.machine_max.research.recipe.insufficient_material_1").withColor(Color.YELLOW.getRGB()),
                            Component.translatable("gui.machine_max.research.recipe.insufficient_material_2").withColor(TEXT_MUTED)
                    ), mouseX, mouseY);
                }
                return;
            }
            // 按钮 2
            if (mouseX >= btn2X && mouseX < btn2X + BUTTON_WIDTH) {
                if (!state.started()) {
                    guiGraphics.renderComponentTooltip(minecraft.font, List.of(
                            Component.translatable("gui.machine_max.research.recipe.cant_apply_free_rp_1").withColor(Color.YELLOW.getRGB()),
                            Component.translatable("gui.machine_max.research.recipe.cant_apply_free_rp_2").withColor(TEXT_MUTED)
                    ), mouseX, mouseY);
                } else if (state.currentFreeRp() <= 0) {
                    guiGraphics.renderComponentTooltip(minecraft.font, List.of(
                            Component.translatable("gui.machine_max.research.recipe.no_apply_free_rp_1").withColor(Color.YELLOW.getRGB()),
                            Component.translatable("gui.machine_max.research.recipe.no_apply_free_rp_2").withColor(TEXT_MUTED)
                    ), mouseX, mouseY);
                } else if (state.currentFreeRp() >= state.requiredRp() - state.currentRp()){
                    guiGraphics.renderComponentTooltip(minecraft.font, List.of(
                            Component.translatable("gui.machine_max.research.recipe.apply_free_rp_complete_1").withColor(BTN_CLAIM),
                            Component.translatable("gui.machine_max.research.recipe.apply_free_rp_complete_2", state.requiredRp() - state.currentRp()).withColor(TEXT_MUTED)
                    ), mouseX, mouseY);
                } else {
                    guiGraphics.renderComponentTooltip(minecraft.font, List.of(
                            Component.translatable("gui.machine_max.research.recipe.apply_free_rp_1"),
                            Component.translatable("gui.machine_max.research.recipe.apply_free_rp_2", state.currentFreeRp() + state.currentRp(), state.requiredRp()).withColor(TEXT_MUTED)
                    ), mouseX, mouseY);
                }
                return;
            }
            // 按钮 3
            if (mouseX >= btn3X && mouseX < btn3X + BUTTON_WIDTH) {
                if (state.hasProduct()) {
                    guiGraphics.renderComponentTooltip(minecraft.font, List.of(
                            Component.translatable("gui.machine_max.research.recipe.claim_1").withColor(BTN_CLAIM),
                            Component.translatable("gui.machine_max.research.recipe.claim_2").withColor(TEXT_MUTED)
                    ), mouseX, mouseY);
                } else if (state.canReclaim()) {
                    guiGraphics.renderComponentTooltip(minecraft.font, List.of(
                            Component.translatable("gui.machine_max.research.recipe.reclaim_1").withColor(BTN_CLAIM),
                            Component.translatable("gui.machine_max.research.recipe.reclaim_2", state.reclaimRp()).withColor(TEXT_MUTED)
                    ), mouseX, mouseY);
                } else if (state.unlocked()) {
                    guiGraphics.renderComponentTooltip(minecraft.font, List.of(
                            Component.translatable("gui.machine_max.research.recipe.insufficient_free_rp_1").withColor(Color.YELLOW.getRGB()),
                            Component.translatable("gui.machine_max.research.recipe.insufficient_free_rp_2", state.reclaimRp()).withColor(TEXT_MUTED)
                    ), mouseX, mouseY);
                } else {
                    guiGraphics.renderComponentTooltip(minecraft.font, List.of(
                            Component.translatable("gui.machine_max.research.recipe.research_unfinished_1").withColor(Color.YELLOW.getRGB()),
                            Component.translatable("gui.machine_max.research.recipe.research_unfinished_2").withColor(TEXT_MUTED)
                    ), mouseX, mouseY);
                }
                return;
            }
            if (hovered != null)
                guiGraphics.renderTooltip(minecraft.font, hovered.recipe().value().getResultItem(minecraft.level.registryAccess()), mouseX, mouseY);
        }
    }

    /* =========================
     *  输入处理
     * ========================= */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = (int) ((mouseY - getY() + scrollAmount()) / ENTRY_HEIGHT);
        if (index < 0 || index >= states.size()) return false;

        int btn1X = getX() + width - 3 * (BUTTON_WIDTH + 4);
        int btn2X = getX() + width - 2 * (BUTTON_WIDTH + 4);
        int btn3X = getX() + width - BUTTON_WIDTH - 4;

        if (withinContentAreaPoint(mouseX, mouseY)) {
            selectedIndex = index;
            ResearchState state = states.get(index);
            callbacks.onSelect(state);
            // 按钮 1
            if (mouseX >= btn1X && mouseX < btn1X + BUTTON_WIDTH) {
                if (state.researching()) {
                    callbacks.onCancel(state);
                } else if (state.canResearch()) {
                    callbacks.onStart(state);
                }
            }
            // 按钮 2
            if (mouseX >= btn2X && mouseX < btn2X + BUTTON_WIDTH) {
                if (state.started() && state.currentFreeRp() > 0) {
                    callbacks.onApplyFreeRp(state);
                }
            }
            // 按钮 3
            if (mouseX >= btn3X && mouseX < btn3X + BUTTON_WIDTH) {
                if (state.hasProduct()) {
                    callbacks.onClaim(state);
                } else if (state.canReclaim()) {
                    callbacks.onReclaim(state);
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected int getInnerHeight() {
        return states.size() * ENTRY_HEIGHT;
    }

    @Override
    protected double scrollRate() {
        return 15;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
    }

    /* =========================
     *  工具方法
     * ========================= */

    @Override
    protected void setScrollAmount(double amount) {
        super.setScrollAmount(amount);
    }

    /* =========================
     *  接口定义
     * ========================= */

    public interface Callbacks {
        void onSelect(ResearchState state);

        void onStart(ResearchState state);

        void onApplyFreeRp(ResearchState state);

        void onCancel(ResearchState state);

        void onClaim(ResearchState state);

        void onReclaim(ResearchState state);
    }
}
