package io.github.sweetzonzi.machine_max.client.render.gui.renderable;

import io.github.sweetzonzi.machine_max.client.render.gui.screen.ResearchState;
import io.github.sweetzonzi.machine_max.common.recipe.BlueprintResearchRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.ResearchRecipe;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
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
    private static final int TEXT_OK = new Color(130, 220, 130).getRGB();

    private static final int BTN_ACTIVE = new Color(80, 140, 220).getRGB();
    private static final int BTN_INACTIVE = new Color(72, 72, 72).getRGB();
    private static final int BTN_CLAIM = new Color(120, 200, 120).getRGB();
    private static final int BTN_RECLAIM = new Color(200, 160, 80).getRGB();

    private static final int ENTRY_HEIGHT = 28;
    private static final int BUTTON_WIDTH = 10;
    private static final int BUTTON_HEIGHT = 10;
    private static final ResourceLocation FALLBACK_ICON = ResourceLocation.withDefaultNamespace("textures/missingno.png");

    private final Minecraft minecraft;
    private final List<ResearchState> states = new ArrayList<>();

    private int selectedIndex = -1;
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
        return selectedIndex >= 0 && selectedIndex < states.size() ? states.get(selectedIndex) : null;
    }

    @Override
    protected void renderContents(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        for (int i = 0; i < states.size(); i++) {
            ResearchState state = states.get(i);
            int yPos = getY() + i * ENTRY_HEIGHT;
            boolean hoveredRow = isMouseOver(mouseX, mouseY)
                    && mouseY >= yPos - scrollAmount()
                    && mouseY < yPos + ENTRY_HEIGHT - scrollAmount();

            int bg = (i == selectedIndex) ? BG_SELECTED : hoveredRow ? BG_HOVER : (i % 2 == 0 ? BG_EVEN : BG_ODD);
            g.fill(getX(), yPos, getX() + width, yPos + ENTRY_HEIGHT, bg);

            ResourceLocation icon = state.recipe().value().getIcon();
            g.blit(icon != null ? icon : FALLBACK_ICON, getX() + 4, yPos + 6, 0, 0, 16, 16, 16, 16);

            int nameColor = state.completed() ? TEXT_OK : (state.unlockable() ? TEXT_NORMAL : TEXT_MUTED);
            Component title = Component.translatable(state.recipe().id().toLanguageKey());
            if (state.recipe().value() instanceof BlueprintResearchRecipe blueprint){
                title = Component.translatable(blueprint.getUnlockRecipe().toLanguageKey());
            }
            g.drawString(minecraft.font, title, getX() + 26, yPos + 4, nameColor, false);

            String rpText = state.currentFreeRp() + "/" + state.requiredRp();
            g.drawString(minecraft.font, rpText, getX() + 26, yPos + 16, TEXT_MUTED, false);

            int btn1X = getX() + width - 2 * (BUTTON_WIDTH + 4);
            int btn2X = getX() + width - BUTTON_WIDTH - 4;
            int btnY = yPos + 3;

            int btn1Color;
            String btn1Text;
            if (state.completed()) {
                btn1Color = BTN_CLAIM;
                btn1Text = "✓";
            } else {
                btn1Color = state.canComplete() ? BTN_ACTIVE : BTN_INACTIVE;
                btn1Text = "▶";
            }
            g.fill(btn1X, btnY, btn1X + BUTTON_WIDTH, btnY + BUTTON_HEIGHT, btn1Color);
            g.drawCenteredString(minecraft.font, btn1Text, btn1X + BUTTON_WIDTH / 2, btnY + 1, 0xFFFFFF);

            boolean canClaim = state.blueprintResearch() && (state.hasProduct() || state.canReclaim());
            int btn2Color = state.hasProduct() ? BTN_CLAIM : state.canReclaim() ? BTN_RECLAIM : BTN_INACTIVE;
            g.fill(btn2X, btnY, btn2X + BUTTON_WIDTH, btnY + BUTTON_HEIGHT, btn2Color);
            g.drawCenteredString(minecraft.font, state.hasProduct() ? "↓" : "+", btn2X + BUTTON_WIDTH / 2, btnY + 1, 0xFFFFFF);

            if (hoveredRow) {
                if (!state.unlockable() && state.missingPrerequisites() > 0) {
                    g.drawString(minecraft.font, "Prereq x" + state.missingPrerequisites(), getX() + 90, yPos + 16, 0xFFAAAA, false);
                } else if (!state.completed() && !state.canComplete()) {
                    g.drawString(minecraft.font, "Need materials / RP", getX() + 90, yPos + 16, 0xFFAAAA, false);
                } else if (canClaim) {
                    g.drawString(minecraft.font, state.hasProduct() ? "Claim" : "Reprint", getX() + 90, yPos + 16, 0xAAFFAA, false);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = (int) ((mouseY - getY() + scrollAmount()) / ENTRY_HEIGHT);
        if (index < 0 || index >= states.size()) return false;

        int btn1X = getX() + width - 2 * (BUTTON_WIDTH + 4);
        int btn2X = getX() + width - BUTTON_WIDTH - 4;

        if (withinContentAreaPoint(mouseX, mouseY)) {
            selectedIndex = index;
            ResearchState state = states.get(index);
            callbacks.onSelect(state);

            if (mouseX >= btn1X && mouseX < btn1X + BUTTON_WIDTH) {
                if (!state.completed() && state.unlockable()) {
                    callbacks.onComplete(state);
                }
            }

            if (mouseX >= btn2X && mouseX < btn2X + BUTTON_WIDTH) {
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

    public interface Callbacks {
        void onSelect(ResearchState state);

        void onComplete(ResearchState state);

        void onClaim(ResearchState state);

        void onReclaim(ResearchState state);
    }
}
