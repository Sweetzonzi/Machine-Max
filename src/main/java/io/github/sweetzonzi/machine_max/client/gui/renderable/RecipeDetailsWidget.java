package io.github.sweetzonzi.machine_max.client.gui.renderable;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static io.github.sweetzonzi.machine_max.client.gui.screen.FabricatingScreen.formatTime;

@Getter
public class RecipeDetailsWidget extends AbstractScrollWidget {
    private final Minecraft minecraft;
    private FabricatingRecipe currentRecipe;
    private float efficiency = 1.0f;

    // 滚动相关
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int PADDING = 5;
    private static final int LINE_SPACING = 8;

    // 内容行
    private final List<FormattedCharSequence> contentLines = new ArrayList<>();
    private int contentHeight = 0;

    public RecipeDetailsWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.minecraft = Minecraft.getInstance();
    }

    public void setRecipe(FabricatingRecipe recipe) {
        this.currentRecipe = recipe;
        updateContent();
    }

    private void updateContent() {
        contentLines.clear();
        contentHeight = 0;

        if (currentRecipe == null) {
            // 添加提示文本
            Component hint = Component.translatable("gui.machine_max.fabricator.select_recipe_hint");
            contentLines.addAll(minecraft.font.split(hint, width - SCROLLBAR_WIDTH - PADDING * 2));
            calculateContentHeight();
            return;
        }

        ItemStack result;
        if (minecraft.level != null) {
            result = currentRecipe.getResultItem(minecraft.level.registryAccess());
        } else return;

        // 添加标题
        MutableComponent title = Component.translatable("gui.machine_max.fabricator.recipe_details").append(": ").withStyle(ChatFormatting.BOLD);
        title.append(result.getHoverName()).withStyle(result.getRarity().getStyleModifier());
        contentLines.addAll(minecraft.font.split(title, width - SCROLLBAR_WIDTH - PADDING * 2));

        // 添加制造时间
        int baseTime = currentRecipe.getProcessingTime();
        int actualTime = (int) (baseTime / efficiency);
        Component timeText = Component.translatable("gui.machine_max.fabricator.actual_time", formatTime(actualTime));
        contentLines.addAll(minecraft.font.split(timeText, width - SCROLLBAR_WIDTH - PADDING * 2));

        // 添加基础时间（如果效率不是1）
        if (efficiency != 1.0f) {
            Component baseTimeText = Component.translatable("gui.machine_max.fabricator.base_time", formatTime(baseTime));
            contentLines.addAll(minecraft.font.split(baseTimeText, width - SCROLLBAR_WIDTH - PADDING * 2));

            Component efficiencyText = Component.translatable("gui.machine_max.fabricator.efficiency", String.format("%.1f", efficiency));
            contentLines.addAll(minecraft.font.split(efficiencyText, width - SCROLLBAR_WIDTH - PADDING * 2));
        }

        // 添加输出数量
        Component outputText = Component.translatable("gui.machine_max.fabricator.output_count", result.getCount());
        contentLines.addAll(minecraft.font.split(outputText, width - SCROLLBAR_WIDTH - PADDING * 2));
        //TODO: 添加配方描述
        String tip = currentRecipe.getTooltip();
        if (!tip.isEmpty() && MMDynamicRes.BLUEPRINT_INFO.get(ResourceLocation.parse(tip)) instanceof String content) {
            tip = content;
        }
        // 添加物品描述
        if (minecraft.player != null) {
            List<Component> tooltip = result.getTooltipLines(
                    Item.TooltipContext.of(minecraft.level),
                    minecraft.player,
                    TooltipFlag.Default.NORMAL
            );

            if (tooltip.size() > 2) {
                Component descriptionTitle = Component.translatable("gui.machine_max.fabricator.item_description").withStyle(ChatFormatting.BOLD);
                contentLines.addAll(minecraft.font.split(descriptionTitle, width - SCROLLBAR_WIDTH - PADDING * 2));

                // 添加描述文本（跳过物品名称），保留原有格式
                for (int i = 1; i < tooltip.size(); i++) {
                    Component line = tooltip.get(i);

                    // 保留原有格式，直接分割文本
                    List<FormattedCharSequence> splitLines = minecraft.font.split(line, width - SCROLLBAR_WIDTH - PADDING * 2);

                    // 如果分割后有多行，说明文本过长需要换行
                    contentLines.addAll(splitLines);

                    // 在描述行之间添加适当的间距（除了最后一行）
                    if (i < tooltip.size() - 1) {
                        contentLines.add(FormattedCharSequence.EMPTY);
                    }
                }
            }
        }

        calculateContentHeight();
    }

    private void calculateContentHeight() {
        contentHeight = contentLines.size() * (minecraft.font.lineHeight + LINE_SPACING) + PADDING * 2;
    }

    @Override
    protected void renderContents(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int yPos = getY() + PADDING;

        for (FormattedCharSequence line : contentLines) {
            if (line != FormattedCharSequence.EMPTY) {
                // 使用默认颜色，让文本保持原有的格式颜色
                graphics.drawString(minecraft.font, line, getX() + PADDING, yPos, 0xFFFFFF, false);
            }
            yPos += minecraft.font.lineHeight + LINE_SPACING;

            // 如果超出可见区域则停止渲染（优化性能）
            if (yPos - scrollAmount() > contentHeight) {
                break;
            }
        }
    }

    @Override
    protected void renderBackground(GuiGraphics graphics) {
        // 渲染背景
        graphics.fill(getX(), getY(), getX() + width, getY() + height, new Color(16, 16, 16, 128).getRGB());

        // 绘制边框
        graphics.renderOutline(getX(), getY(), width, height, 0xFF555555);

        // 渲染滚动条背景（如果需要）
        if (scrollbarVisible()) {
            graphics.fill(getX() + width - SCROLLBAR_WIDTH, getY(),
                    getX() + width, getY() + height, 0xCC333333);
        }
    }


    private void renderScrollBar(GuiGraphics graphics) {
        if (scrollbarVisible()) {
            int scrollbarHeight = (int) ((float) height * height / (float) getInnerHeight());
            scrollbarHeight = Mth.clamp(scrollbarHeight, 32, height);

            int scrollbarY = (int) ((float) scrollAmount() * (height - scrollbarHeight) / (float) getMaxScrollAmount() + getY());

            RenderSystem.enableBlend();
            graphics.fill(getX() + width - SCROLLBAR_WIDTH, scrollbarY,
                    getX() + width - 1, scrollbarY + scrollbarHeight, 0xFF888888);
            RenderSystem.disableBlend();
        }
    }

    @Override
    protected int getInnerHeight() {
        return contentHeight;
    }

    @Override
    protected double scrollRate() {
        return 10;
    }

    @Override
    protected boolean scrollbarVisible() {
        return getInnerHeight() > height;
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.visible) {
            this.renderBackground(graphics);

            // 启用裁剪区域
            graphics.enableScissor(getX() + 1, getY() + 1, getX() + width - (scrollbarVisible() ? SCROLLBAR_WIDTH : 1), getY() + height - 1);

            graphics.pose().pushPose();
            graphics.pose().translate(0.0, -scrollAmount(), 0.0);
            this.renderContents(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();

            graphics.disableScissor();

            if (scrollbarVisible()) {
                this.renderScrollBar(graphics);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        // narration implementation
    }

    // 当效率改变时更新内容
    public void setEfficiency(float efficiency) {
        if (this.efficiency != efficiency) {
            this.efficiency = efficiency;
            updateContent();
        }
    }
}