package io.github.sweetzonzi.machine_max.client.gui.renderable;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.sweetzonzi.machine_max.common.block.fabricator.FabricatorBlockEntity;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMResources;
import lombok.Setter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.github.sweetzonzi.machine_max.client.gui.screen.FabricatingScreen.formatTime;

public class RecipeListWidget extends AbstractScrollWidget {
    private final ResourceLocation SCROLLER = ResourceLocation.withDefaultNamespace("widget/scroller");

    private final Minecraft minecraft;
    private final FabricatorBlockEntity fabricator;//TODO: 按制造机属性选择性展示配方
    private final List<RecipeHolder<FabricatingRecipe>> allRecipes = new ArrayList<>();
    private List<RecipeHolder<FabricatingRecipe>> filteredRecipes = new ArrayList<>();
    private String searchFilter = "";
    private int selectedIndex = -1;
    private final int itemHeight = 20;

    @Setter
    private Consumer<RecipeHolder<FabricatingRecipe>> onRecipeSelected;

    public RecipeListWidget(Minecraft minecraft, int x, int y, int width, int height, FabricatorBlockEntity fabricator) {
        super(x, y, width, height, Component.empty());
        this.minecraft = minecraft;
        this.fabricator = fabricator;
        loadRecipes();
    }

    private void loadRecipes() {
        if (minecraft.level == null) return;

        RecipeManager recipeManager = minecraft.level.getRecipeManager();
        allRecipes.clear();

        allRecipes.addAll(recipeManager.getAllRecipesFor(MMResources.getFABRICATION_RECIPE_TYPE().get()));

        applySearchFilter();
    }

    public void setSearchFilter(String filter) {
        this.searchFilter = filter;
        applySearchFilter();
    }

    private void applySearchFilter() {
        if (searchFilter.isEmpty()) {
            filteredRecipes = new ArrayList<>(allRecipes);
        } else {
            String lowerFilter = searchFilter.toLowerCase();
            filteredRecipes = allRecipes.stream()
                    .filter(recipe -> {
                        ItemStack result = recipe.value().getResultItem(minecraft.level.registryAccess());
                        String displayName = result.getHoverName().getString().toLowerCase();
                        return displayName.contains(lowerFilter);
                    })
                    .collect(Collectors.toList());
        }
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 渲染背景
        graphics.fill(getX(), getY(), getX()+width, getY()+filteredRecipes.size() * itemHeight + height, 0xFF3A3A3A);

        FabricatingRecipe hoveredRecipe = null;

        for (int i = 0; i < filteredRecipes.size(); i++) {
            RecipeHolder<FabricatingRecipe> recipe = filteredRecipes.get(i);
            ItemStack result = recipe.value().getResultItem(minecraft.level.registryAccess());

            int yPos = getY() + i * itemHeight;
            boolean isSelected = i == selectedIndex;
            boolean isHovered = isMouseOver(mouseX, mouseY) &&
                    mouseY >= yPos - scrollAmount() && mouseY < yPos + itemHeight - scrollAmount();

            // 绘制条目背景
            if (isSelected) {
                graphics.fill(getX(), yPos, getX() +width, yPos + itemHeight, 0xFF4A6A4A);
                if (isHovered) hoveredRecipe = recipe.value();
            } else if (isHovered) {
                graphics.fill(getX(), yPos, getX() +width, yPos + itemHeight, 0xFF3A5A3A);
                hoveredRecipe = recipe.value();
            } else {
                graphics.fill(getX(), yPos, getX() +width, yPos + itemHeight, i % 2 == 0 ? 0xFF212121 : 0xFF333333);
            }

            // 绘制物品图标
            graphics.renderItem(result, getX() + 3, yPos + 2);
            graphics.renderItemDecorations(minecraft.font, result, getX() + 3, yPos + 2);

            // 绘制物品名称 - 支持滚动
            Component displayName = result.getHoverName();
            String nameStr = displayName.getString();

            boolean needsScrolling = minecraft.font.width(nameStr) > width - 25;
            String displayNameStr;

            if (needsScrolling) {
                displayNameStr = createScrollingText(nameStr, width - 25, scrollOffset);
            } else {
                displayNameStr = minecraft.font.plainSubstrByWidth(nameStr, width);
            }

            graphics.drawString(minecraft.font, displayNameStr, getX() + 23, yPos + 6, 0xFFFFFF, false);
        }

        graphics.disableScissor();
        // 渲染悬停配方的tooltip
        if (hoveredRecipe != null && minecraft.player != null) {
            ItemStack result = hoveredRecipe.getResultItem(minecraft.level.registryAccess());
            // 添加配方信息
            List<Component> tooltip = Screen.getTooltipFromItem(this.minecraft, result);
            // 添加制造时间信息
            int time = (int) (hoveredRecipe.getProcessingTime() / fabricator.efficiency);
            String timeText = formatTime(time);
            tooltip.set(0, tooltip.getFirst().copy().append(Component.literal(" " + timeText).withStyle(ChatFormatting.YELLOW)));
            graphics.renderTooltip(minecraft.font, tooltip, result.getTooltipImage(), mouseX, (int) (mouseY + scrollAmount()));
        }
        graphics.enableScissor(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1);
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.visible) {
            this.renderBackground(guiGraphics);
            guiGraphics.enableScissor(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0, -this.scrollAmount(), 0.0);
            this.renderContents(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.pose().popPose();
            guiGraphics.disableScissor();
            this.renderScrollBar(guiGraphics);
        }
    }

    private void renderScrollBar(GuiGraphics guiGraphics) {
        if (this.scrollbarVisible()) {
            int i = Mth.clamp((int) ((float) (this.height * this.height) / (float) this.getInnerHeight() + 4), 32, this.height);
            int j = this.getX();
            int k = Math.max(this.getY(), (int) this.scrollAmount() * (this.height - i) / this.getMaxScrollAmount() + this.getY());
            RenderSystem.enableBlend();
            guiGraphics.blitSprite(SCROLLER, j, k, 2, i);
            RenderSystem.disableBlend();
        }
    }

    @Override
    protected void renderBackground(@NotNull GuiGraphics graphics) {
        renderBorder(graphics, getX(), getY(), width, height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isMouseOver(mouseX, mouseY)) return false;

        int relativeY = (int) (mouseY - getY() + scrollAmount());
        int index = relativeY / itemHeight;

        if (index >= 0 && index < filteredRecipes.size()) {
            selectedIndex = index;
            RecipeHolder<FabricatingRecipe> selected = filteredRecipes.get(index);
            // 触发选择回调
            if (onRecipeSelected != null) {
                onRecipeSelected.accept(selected);
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected int getInnerHeight() {
        return filteredRecipes.size() * itemHeight;
    }

    @Override
    protected double scrollRate() {
        return 3;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        //  narration implementation
    }

    // 添加滚动文本支持到RecipeListWidget
    private long lastScrollUpdate = 0;
    private int scrollOffset = 0;

    private void updateScrollingText() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScrollUpdate > 40) {
            scrollOffset++;
            lastScrollUpdate = currentTime;
        }
    }

    private String createScrollingText(String text, int maxWidth, int offset) {
        // 实现与MaterialRequirementsWidget相同的滚动逻辑
        int textWidth = minecraft.font.width(text);
        if (textWidth <= maxWidth) {
            return text;
        }

        String paddedText = text + "   ";
        int paddedWidth = minecraft.font.width(paddedText);
        int scrollPos = offset % (paddedWidth + 20);

        if (scrollPos < 20) {
            return minecraft.font.plainSubstrByWidth(paddedText, maxWidth);
        } else if (scrollPos < paddedWidth + 20) {
            int startPos = scrollPos - 20;
            String scrollingText = paddedText + paddedText;
            return minecraft.font.plainSubstrByWidth(scrollingText.substring(startPos / 4), maxWidth);
        }

        return minecraft.font.plainSubstrByWidth(paddedText, maxWidth);
    }
}