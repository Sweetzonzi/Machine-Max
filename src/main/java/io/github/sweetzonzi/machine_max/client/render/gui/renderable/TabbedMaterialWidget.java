package io.github.sweetzonzi.machine_max.client.render.gui.renderable;

import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.IngredientCountPair;
import io.github.sweetzonzi.machine_max.common.recipe.ResearchRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 选项卡式紧凑材料组件
 * <p>
 * 上方为选项卡标签（研发材料/制造材料），下方为图标网格。
 * 每个材料仅渲染物品图标 + 数量角标，悬停时显示名称 tooltip。
 */
public class TabbedMaterialWidget extends AbstractWidget {
    private final Minecraft minecraft;

    private ResearchRecipe currentResearchRecipe;
    private FabricatingRecipe currentFabricatingRecipe;

    private final List<MaterialEntry> researchEntries = new ArrayList<>();
    private final List<MaterialEntry> fabricatingEntries = new ArrayList<>();

    private TabType activeTab = TabType.RESEARCH;

    /* 布局常量 */
    private static final int TAB_HEIGHT = 12;
    private static final int SLOT_SIZE = 20;
    private static final int SLOT_PADDING = 2;
    private static final int GRID_START_Y_OFFSET = 16;

    /* 配色 */
    private static final int BG_COLOR = new Color(16, 16, 16, 128).getRGB();
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int TAB_INACTIVE_BG = new Color(30, 30, 30, 180).getRGB();
    private static final int TAB_ACTIVE_BG = new Color(50, 50, 50, 200).getRGB();
    private static final int TAB_HOVER_BG = new Color(60, 60, 60, 200).getRGB();
    private static final int TEXT_COLOR = new Color(200, 200, 200).getRGB();
    private static final int TEXT_MUTED = new Color(140, 140, 140).getRGB();
    private static final int COLOR_ENOUGH = 0x3344AA44;
    private static final int COLOR_MISSING = 0x33AA4444;

    public TabbedMaterialWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.minecraft = Minecraft.getInstance();
    }

    /** 设置研发配方和制造配方 */
    public void setRecipes(ResearchRecipe researchRecipe, FabricatingRecipe fabricatingRecipe) {
        this.currentResearchRecipe = researchRecipe;
        this.currentFabricatingRecipe = fabricatingRecipe;
        rebuildEntries();
    }

    /** 清空所有配方数据 */
    public void clearRecipes() {
        this.currentResearchRecipe = null;
        this.currentFabricatingRecipe = null;
        researchEntries.clear();
        fabricatingEntries.clear();
    }

    private void rebuildEntries() {
        researchEntries.clear();
        fabricatingEntries.clear();

        Player player = minecraft.player;
        if (player == null) return;
        boolean isCreative = player.isCreative();

        if (currentResearchRecipe != null) {
            for (IngredientCountPair pair : currentResearchRecipe.getResearchIngredientPairs()) {
                addEntry(researchEntries, pair, player, isCreative);
            }
        }
        if (currentFabricatingRecipe != null) {
            for (IngredientCountPair pair : currentFabricatingRecipe.getIngredientPairs()) {
                addEntry(fabricatingEntries, pair, player, isCreative);
            }
        }
    }

    private void addEntry(List<MaterialEntry> list, IngredientCountPair pair, Player player, boolean isCreative) {
        ItemStack[] matchingItems = pair.ingredient().getItems();
        if (matchingItems.length == 0) return;
        ItemStack displayStack = matchingItems[0].copy();
        displayStack.setCount(pair.count());
        int playerCount = getPlayerItemCount(pair.ingredient(), player);
        boolean hasEnough = isCreative || playerCount >= pair.count();
        list.add(new MaterialEntry(displayStack, pair.count(), playerCount, hasEnough, isCreative));
    }

    private static int getPlayerItemCount(Ingredient ingredient, Player player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && ingredient.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(getX(), getY(), getX() + width, getY() + height, BG_COLOR);
        graphics.renderOutline(getX(), getY(), width, height, BORDER_COLOR);

        boolean hasResearch = currentResearchRecipe != null && !currentResearchRecipe.getResearchIngredientPairs().isEmpty();
        boolean hasFabricating = currentFabricatingRecipe != null && !currentFabricatingRecipe.getIngredientPairs().isEmpty();

        if (currentResearchRecipe == null && currentFabricatingRecipe == null) {
            String text = Component.translatable("gui.machine_max.fabricator.select_recipe_hint").getString();
            int tw = minecraft.font.width(text);
            graphics.drawString(minecraft.font, text,
                    getX() + (width - tw) / 2, getY() + height / 2 - 4, TEXT_MUTED, false);
            return;
        }

        /* 渲染选项卡 */
        renderTabs(graphics, mouseX, mouseY, hasResearch, hasFabricating);

        /* 渲染当前选项卡的网格 */
        List<MaterialEntry> entries = getActiveEntries();
        if (entries.isEmpty()) {
            String text = Component.translatable("gui.machine_max.research.no_materials").getString();
            int tw = minecraft.font.width(text);
            graphics.drawString(minecraft.font, text,
                    getX() + (width - tw) / 2, getY() + height / 2 + 2, TEXT_MUTED, false);
            return;
        }

        int columns = Math.max(1, (width - SLOT_PADDING * 2) / (SLOT_SIZE + SLOT_PADDING));
        int startX = getX() + 3;
        int startY = getY() + GRID_START_Y_OFFSET;

        MaterialEntry hovered = null;
        for (int i = 0; i < entries.size(); i++) {
            int row = i / columns;
            int col = i % columns;
            int x = startX + col * (SLOT_SIZE + SLOT_PADDING);
            int y = startY + row * (SLOT_SIZE + SLOT_PADDING);

            MaterialEntry entry = entries.get(i);
            if (x <= mouseX && mouseX < x + SLOT_SIZE && y <= mouseY && mouseY < y + SLOT_SIZE) {
                graphics.renderTooltip(minecraft.font, entry.displayStack, mouseX, mouseY);
            }
            int bg = entry.hasEnough ? COLOR_ENOUGH : COLOR_MISSING;
            graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, bg);
            graphics.renderItem(entry.displayStack, x + 2, y + 2);
            graphics.renderItemDecorations(minecraft.font, entry.displayStack, x + 2, y + 2);
        }
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY,
                            boolean hasResearch, boolean hasFabricating) {
        int tabCount = 0;
        if (hasResearch) tabCount++;
        if (hasFabricating) tabCount++;
        if (tabCount == 0) return;

        int tabWidth = Math.min((width - 4) / tabCount, 70);
        int tabX = getX() + 2;

        if (hasResearch) {
            boolean hovered = mouseX >= tabX && mouseX < tabX + tabWidth
                    && mouseY >= getY() + 2 && mouseY < getY() + 2 + TAB_HEIGHT;
            int bg = activeTab == TabType.RESEARCH ? TAB_ACTIVE_BG : hovered ? TAB_HOVER_BG : TAB_INACTIVE_BG;
            graphics.fill(tabX, getY() + 2, tabX + tabWidth, getY() + 2 + TAB_HEIGHT, bg);
            Component label = Component.translatable("gui.machine_max.research.tab.research_materials");
            int lw = minecraft.font.width(label);
            graphics.drawString(minecraft.font, label,
                    tabX + (tabWidth - lw) / 2, getY() + 3, TEXT_COLOR, false);
            tabX += tabWidth + 2;
        }
        if (hasFabricating) {
            boolean hovered = mouseX >= tabX && mouseX < tabX + tabWidth
                    && mouseY >= getY() + 2 && mouseY < getY() + 2 + TAB_HEIGHT;
            int bg = activeTab == TabType.FABRICATING ? TAB_ACTIVE_BG : hovered ? TAB_HOVER_BG : TAB_INACTIVE_BG;
            graphics.fill(tabX, getY() + 2, tabX + tabWidth, getY() + 2 + TAB_HEIGHT, bg);
            Component label = Component.translatable("gui.machine_max.research.tab.fabricating_materials");
            int lw = minecraft.font.width(label);
            graphics.drawString(minecraft.font, label,
                    tabX + (tabWidth - lw) / 2, getY() + 3, TEXT_COLOR, false);
        }
    }

    private List<MaterialEntry> getActiveEntries() {
        return activeTab == TabType.RESEARCH ? researchEntries : fabricatingEntries;
    }

    /**
     * 获取鼠标位置对应的材料物品，用于 JEI 查询
     */
    public ItemStack getIngredientAt(double mouseX, double mouseY) {
        List<MaterialEntry> entries = getActiveEntries();
        if (entries.isEmpty()) return ItemStack.EMPTY;

        int columns = Math.max(1, (width - SLOT_PADDING * 2) / (SLOT_SIZE + SLOT_PADDING));
        int startX = getX() + 3;
        int startY = getY() + GRID_START_Y_OFFSET;

        for (int i = 0; i < entries.size(); i++) {
            int row = i / columns;
            int col = i % columns;
            int x = startX + col * (SLOT_SIZE + SLOT_PADDING);
            int y = startY + row * (SLOT_SIZE + SLOT_PADDING);

            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                return entries.get(i).displayStack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY)) return false;

        boolean hasResearch = currentResearchRecipe != null && !currentResearchRecipe.getResearchIngredientPairs().isEmpty();
        boolean hasFabricating = currentFabricatingRecipe != null && !currentFabricatingRecipe.getIngredientPairs().isEmpty();

        int tabCount = 0;
        if (hasResearch) tabCount++;
        if (hasFabricating) tabCount++;
        if (tabCount == 0) return false;

        int tabWidth = Math.min((width - 4) / tabCount, 70);
        int tabX = getX() + 2;

        if (hasResearch) {
            if (mouseX >= tabX && mouseX < tabX + tabWidth
                    && mouseY >= getY() + 2 && mouseY < getY() + 2 + TAB_HEIGHT) {
                activeTab = TabType.RESEARCH;
                return true;
            }
            tabX += tabWidth + 2;
        }
        if (hasFabricating) {
            if (mouseX >= tabX && mouseX < tabX + tabWidth
                    && mouseY >= getY() + 2 && mouseY < getY() + 2 + TAB_HEIGHT) {
                activeTab = TabType.FABRICATING;
                return true;
            }
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    public boolean hasResearchContent() {
        return currentResearchRecipe != null && !currentResearchRecipe.getResearchIngredientPairs().isEmpty();
    }

    public boolean hasFabricatingContent() {
        return currentFabricatingRecipe != null && !currentFabricatingRecipe.getIngredientPairs().isEmpty();
    }

    private enum TabType {
        RESEARCH,
        FABRICATING
    }

    private static class MaterialEntry {
        public final ItemStack displayStack;
        public final int requiredCount;
        public final int playerCount;
        public final boolean hasEnough;
        @SuppressWarnings("unused")
        public final boolean isCreative;

        MaterialEntry(ItemStack displayStack, int requiredCount, int playerCount, boolean hasEnough, boolean isCreative) {
            this.displayStack = displayStack;
            this.requiredCount = requiredCount;
            this.playerCount = playerCount;
            this.hasEnough = hasEnough;
            this.isCreative = isCreative;
        }
    }
}
