package io.github.sweetzonzi.machine_max.client.render.gui.renderable;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.IngredientCountPair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MaterialRequirementsWidget extends AbstractWidget {
    private final Minecraft minecraft;
    private FabricatingRecipe currentRecipe;
    private final List<MaterialEntry> materialEntries = new ArrayList<>();

    // 布局配置
    private static final int ENTRY_HEIGHT = 30;
    private static final int ENTRY_WIDTH = 60;
    private static final int HORIZONTAL_PADDING = 2;
    private static final int VERTICAL_PADDING = 2;

    // 滚动文本相关
    private long lastScrollUpdate = 0;
    private static final long SCROLL_INTERVAL = 40; // 滚动间隔（毫秒）
    private int scrollOffset = 0;

    public MaterialRequirementsWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.minecraft = Minecraft.getInstance();
    }

    public void setRecipe(FabricatingRecipe recipe) {
        this.currentRecipe = recipe;
        updateMaterialEntries();
        this.scrollOffset = 0; // 重置滚动位置
        this.lastScrollUpdate = System.currentTimeMillis();
    }

    public void updateMaterialEntries() {
        materialEntries.clear();
        if (currentRecipe == null || minecraft.player == null) return;

        Player player = minecraft.player;
        boolean isCreative = player.isCreative();

        for (IngredientCountPair ingredientPair : currentRecipe.getIngredientPairs()) {
            ItemStack[] matchingItems = ingredientPair.ingredient().getItems();
            if (matchingItems.length == 0) continue;

            ItemStack displayStack = matchingItems[0].copy();
            displayStack.setCount(ingredientPair.count());

            // 计算玩家拥有的数量
            int playerCount = getPlayerItemCount(ingredientPair.ingredient(), player);
            boolean hasEnough = isCreative || playerCount >= ingredientPair.count();

            materialEntries.add(new MaterialEntry(
                    displayStack,
                    ingredientPair.count(),
                    playerCount,
                    hasEnough,
                    isCreative
            ));
        }
    }

    private int getPlayerItemCount(Ingredient ingredient, Player player) {
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
        // 渲染背景
        graphics.fill(getX(), getY(), getX() + width, getY() + height, new Color(16, 16, 16, 128).getRGB());

        // 绘制边框
        graphics.renderOutline(getX(), getY(), width, height, 0xFF555555);

        if (currentRecipe == null) {
            // 居中显示提示文本
            String text = "选择配方查看材料";
            int textWidth = minecraft.font.width(text);
            graphics.drawString(minecraft.font, text,
                    getX() + (width - textWidth) / 2,
                    getY() + height / 2 - 4,
                    0xAAAAAA, false);
            return;
        }

        // 更新滚动文本
        updateScrollingText();

        // 渲染标题
        graphics.drawString(minecraft.font, Component.translatable("gui.machine_max.fabricator.materials"),
                getX() + 4, getY() + 5, Color.WHITE.getRGB(), false);

        // 计算网格布局
        int columns = Math.max(1, (width - HORIZONTAL_PADDING * 2) / ENTRY_WIDTH);
        int rows = (int) Math.ceil((double) materialEntries.size() / columns);

        // 计算起始位置
        int startX = getX() + 3;
        int startY = getY() + 15;

        // 渲染所有材料条目
        MaterialEntry hoveredEntry = null;
        for (int i = 0; i < materialEntries.size(); i++) {
            int row = i / columns;
            int col = i % columns;

            int x = startX + col * ENTRY_WIDTH + col * HORIZONTAL_PADDING;
            int y = startY + row * ENTRY_HEIGHT + row * VERTICAL_PADDING;

            renderMaterialEntry(graphics, materialEntries.get(i), x, y, ENTRY_WIDTH, ENTRY_HEIGHT);

            // 检查鼠标悬停
            if (isMouseOver(mouseX, mouseY) &&
                    mouseX >= x && mouseX < x + ENTRY_WIDTH &&
                    mouseY >= y && mouseY < y + ENTRY_HEIGHT) {
                hoveredEntry = materialEntries.get(i);
            }
        }

        // 渲染悬停物品的tooltip
        if (hoveredEntry != null) {
            renderMaterialTooltip(graphics, hoveredEntry, mouseX, mouseY);
        }
    }

    private void updateScrollingText() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScrollUpdate > SCROLL_INTERVAL) {
            scrollOffset++;
            lastScrollUpdate = currentTime;
        }
    }

    private void renderMaterialEntry(GuiGraphics graphics, MaterialEntry entry, int x, int y, int width, int height) {
        PoseStack poseStack = graphics.pose();
        // 渲染条目背景
        int bgColor = entry.hasEnough ? 0x3344AA44 : 0x33AA4444; // 绿色或红色半透明背景
        graphics.fill(x, y, x + width, y + height, bgColor);

        // 渲染物品图标（左侧）
        graphics.renderItem(entry.displayStack, x + 3, y + (height - 17) / 2);

        // 渲染文本区域（右侧）
        int textAreaX = x + 23;
        int textAreaWidth = width - 30;

        // 渲染物品名称（第一行）- 支持滚动
        Component itemName = entry.displayStack.getHoverName();
        String nameStr = itemName.getString();

        // 检查是否需要滚动
        boolean needsScrolling = minecraft.font.width(nameStr) > textAreaWidth;
        String displayName;

        if (needsScrolling) {
            // 创建滚动文本
            displayName = createScrollingText(nameStr, textAreaWidth, scrollOffset);
        } else {
            // 普通截断
            displayName = minecraft.font.plainSubstrByWidth(nameStr, textAreaWidth - 10);
        }

        int nameColor = entry.hasEnough ? 0xFFFFFF : 0xFF5555;
        graphics.drawString(minecraft.font, displayName, textAreaX-1, y + 6, nameColor, false);

        // 渲染数量信息（第二行，小字号）
        String countText = formatCountText(entry.requiredCount, entry.playerCount, entry.isCreative);
        poseStack.pushPose();
        poseStack.scale(0.6f, 0.6f, 1.0f);

        int countColor = entry.hasEnough ? 0xCCCCCC : 0xFF8888;
        int scaledX = (int) ((textAreaX) / 0.6f);
        int scaledY = (int) ((y + 19) / 0.6f);

        graphics.drawString(minecraft.font, countText, scaledX, scaledY, countColor, false);

        poseStack.popPose();
    }

    private String createScrollingText(String text, int maxWidth, int offset) {
        int textWidth = minecraft.font.width(text);
        if (textWidth <= maxWidth) {
            return text;
        }

        // 添加空格作为缓冲
        String paddedText = text + "   ";
        int paddedWidth = minecraft.font.width(paddedText);

        // 计算滚动位置
        int scrollPos = offset % (paddedWidth + 20); // 20像素的暂停

        if (scrollPos < 20) {
            // 暂停期 - 显示开头部分
            return minecraft.font.plainSubstrByWidth(paddedText, maxWidth);
        } else if (scrollPos < paddedWidth + 20) {
            // 滚动期 - 从适当位置开始
            int startPos = scrollPos - 20;
            String scrollingText = paddedText + paddedText; // 重复文本以实现无缝滚动
            return minecraft.font.plainSubstrByWidth(scrollingText.substring(startPos / 4), maxWidth);
        }

        return minecraft.font.plainSubstrByWidth(paddedText, maxWidth);
    }

    private void renderMaterialTooltip(GuiGraphics graphics, MaterialEntry entry, int mouseX, int mouseY) {
        // 渲染tooltip
        ItemStack stack = entry.displayStack;
//        graphics.renderTooltip(minecraft.font, Screen.getTooltipFromItem(this.minecraft, stack), stack.getTooltipImage(), mouseX, mouseY);
    }

    private String formatCountText(int required, int playerCount, boolean isCreative) {
        if (isCreative) {
            return "∞ / " + required;
        } else {
            return playerCount + " / " + required;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        // narration implementation
    }

    // 内部类：材料条目数据
    private static class MaterialEntry {
        public final ItemStack displayStack;
        public final int requiredCount;
        public final int playerCount;
        public final boolean hasEnough;
        public final boolean isCreative;

        public MaterialEntry(ItemStack displayStack, int requiredCount, int playerCount, boolean hasEnough, boolean isCreative) {
            this.displayStack = displayStack;
            this.requiredCount = requiredCount;
            this.playerCount = playerCount;
            this.hasEnough = hasEnough;
            this.isCreative = isCreative;
        }
    }
}