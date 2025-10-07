package io.github.sweetzonzi.machine_max.client.gui.renderable;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.sweetzonzi.machine_max.common.block.FabricatorBlockEntity;
import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static io.github.sweetzonzi.machine_max.client.gui.screen.FabricatingScreen.formatTime;

public class TaskListWidget extends AbstractScrollWidget {
    private final ResourceLocation SCROLLER = ResourceLocation.withDefaultNamespace("widget/scroller");
    private final Minecraft minecraft;
    private final FabricatorBlockEntity fabricator;
    private List<FabricatorBlockEntity.ProductionTask> tasks = new ArrayList<>();
    private static final int taskHeight = 50;
    // 获取当前选中的任务索引
    @Getter
    private int selectedIndex = -1;

    private Consumer<Integer> onTaskSelected;

    public TaskListWidget(Minecraft minecraft, int x, int y, int width, int height, FabricatorBlockEntity fabricator) {
        super(x, y, width, height, Component.empty());
        this.minecraft = minecraft;
        this.fabricator = fabricator;
        updateTasks();
    }

    public void tick() {
        updateTasks();
    }

    private void updateTasks() {
        if (fabricator != null) {
            this.tasks = fabricator.getAllTasks();
            if (onTaskSelected != null && selectedIndex >= 0 && selectedIndex < tasks.size()) {
                var task = tasks.get(selectedIndex);
                if (task != null)
                    onTaskSelected.accept(task.id);
                else
                    onTaskSelected.accept(-1);
            }
        }
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.visible) {
            this.renderBackground(guiGraphics);
            guiGraphics.enableScissor(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0, -this.scrollAmount(), 110.0);
            this.renderContents(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.pose().popPose();
            guiGraphics.disableScissor();
            this.renderScrollBar(guiGraphics);
        }
    }

    private void renderScrollBar(GuiGraphics guiGraphics) {
        if (this.scrollbarVisible()) {
            int i = Mth.clamp((int) ((float) (this.height * this.height) / (float) this.getInnerHeight() + 4), 32, this.height);
            int j = this.getX() + this.width;
            int k = Math.max(this.getY(), (int) this.scrollAmount() * (this.height - i) / this.getMaxScrollAmount() + this.getY());
            RenderSystem.enableBlend();
            guiGraphics.blitSprite(SCROLLER, j, k, 2, i);
            RenderSystem.disableBlend();
        }
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 渲染背景
        graphics.fill(getX(), getY(), getX()+width, getY()+tasks.size() * taskHeight + height, 0xFF3A3A3A);

        for (int i = 0; i < tasks.size(); i++) {
            FabricatorBlockEntity.ProductionTask task = tasks.get(i);
            int yPos = getY() + i * taskHeight;

            boolean isSelected = i == selectedIndex;
            boolean isHovered = isMouseOver(mouseX, mouseY) &&
                    mouseY >= yPos - scrollAmount() && mouseY < yPos + taskHeight - scrollAmount();

            // 绘制任务背景
            graphics.fill(getX(), yPos, getX() + width, yPos + taskHeight, i % 2 == 0 ? 0xFF212121 : 0xFF333333);

            // 绘制边框
            if (isSelected) {
                graphics.fill(getX(), yPos, getX() + 2, yPos + taskHeight, 0xFF00FF00); // 左侧选中指示器
            }

            // 绘制任务物品
            graphics.renderItem(task.result, getX() + 4, yPos + 5);
            graphics.renderItemDecorations(minecraft.font, task.result, getX() + 4, yPos + 5);

            // 绘制物品名称（截断以适应宽度）
            Component displayName = task.result.getHoverName();
            String nameStr = displayName.getString();
            String displayNameStr = minecraft.font.plainSubstrByWidth(nameStr, width - 25);
            graphics.drawString(minecraft.font, displayNameStr, getX() + 24, yPos + 9, 0xFFFFFF, false);
            // 进度百分比
            String progressPercent = String.format("%.1f%%", task.getProgressPercent() * 100);
            int textWidth = minecraft.font.width(progressPercent);
            int rightMargin = 2; // 距离右侧的边距，可以根据需要调整
            int textX = getX() + width - rightMargin - textWidth;
            graphics.drawString(minecraft.font, progressPercent, textX, yPos + 35, 0xFFFFFF, false);
            // 进度信息
            if (task.status == FabricatorBlockEntity.TaskStatus.PRODUCING ||
                    task.status == FabricatorBlockEntity.TaskStatus.COMPLETED) {

                // 进度条背景
                int progressBarY = yPos + 25;
                graphics.fill(getX() + 4, progressBarY, getX() + width - 4, progressBarY + 6, 0xFF555555);

                // 进度条填充
                int progressWidth = (int) ((width - 8) * task.getProgressPercent());
                if (progressWidth > 0) {
                    int progressColor = task.status == FabricatorBlockEntity.TaskStatus.COMPLETED ?
                            0xFF00FF00 : 0xFF55AAFF; // 完成时绿色，生产中蓝色
                    graphics.fill(getX() + 4, progressBarY, getX() + 4 + progressWidth, progressBarY + 6, progressColor);
                }

                // 时间信息
                if (task.status == FabricatorBlockEntity.TaskStatus.PRODUCING || task.status == FabricatorBlockEntity.TaskStatus.QUEUED) {
                    int remainingTime = (int) ((task.totalTime - task.progress) / fabricator.getEfficiency());
                    String timeText = formatTime(remainingTime);
                    graphics.drawString(minecraft.font, timeText, getX() + 4, yPos + 35, 0xAAAAAA, false);
                } else if (task.status == FabricatorBlockEntity.TaskStatus.COMPLETED) {
                    graphics.drawString(minecraft.font,
                            Component.translatable("gui.machine_max.fabricator.ready_for_collection"),
                            getX() + 4, yPos + 35, 0x55FF55, false);
                }
            } else if (task.status == FabricatorBlockEntity.TaskStatus.QUEUED) {
                // 绘制任务状态
                Component statusText = getStatusText(task.status);
                int statusColor = getStatusColor(task.status);
                graphics.drawString(minecraft.font, statusText, getX() + 4, yPos + 23, statusColor, false);
                // 排队中的任务显示预计时间
                int estimatedTime = (int) (task.totalTime / fabricator.getEfficiency());
                String timeText = formatTime(estimatedTime);
                graphics.drawString(minecraft.font, timeText, getX() + 4, yPos + 35, 0xAAAAAA, false);
            }

            // 悬停时显示详细tooltip
            if (isHovered && minecraft.player != null) {
                graphics.disableScissor();
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(task.result.getHoverName().copy().withStyle(ChatFormatting.YELLOW));

                // 添加状态信息
                tooltip.add(getStatusText(task.status).copy().withStyle(ChatFormatting.GRAY));

                // 添加进度信息
                if (task.status == FabricatorBlockEntity.TaskStatus.PRODUCING) {
                    int remainingTime = (int) ((task.totalTime - task.progress) / fabricator.getEfficiency());
                    tooltip.add(Component.translatable("gui.machine_max.fabricator.remaining_time",
                            formatTime(remainingTime)).withStyle(ChatFormatting.GREEN));
                } else if (task.status == FabricatorBlockEntity.TaskStatus.QUEUED) {
                    int estimatedTime = (int) (task.totalTime / fabricator.getEfficiency());
                    tooltip.add(Component.translatable("gui.machine_max.fabricator.estimated_time",
                            formatTime(estimatedTime)).withStyle(ChatFormatting.GRAY));
                }
                graphics.renderTooltip(minecraft.font, tooltip, task.result.getTooltipImage(), mouseX, (int) (mouseY + scrollAmount()));
                graphics.enableScissor(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1);
            }
        }
    }

    @Override
    protected void renderBackground(@NotNull GuiGraphics graphics) {
        // 渲染边框
        renderBorder(graphics, getX(), getY(), width, height);
    }

    public void setTaskSelectedCallback(Consumer<Integer> callback) {
        this.onTaskSelected = callback;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isMouseOver(mouseX, mouseY)) return false;

        int relativeY = (int) (mouseY - getY() + scrollAmount());
        int taskIndex = relativeY / taskHeight;

        if (taskIndex >= 0 && taskIndex < tasks.size()) {
            selectedIndex = taskIndex;
            // 触发任务选择回调
            if (onTaskSelected != null) {
                var task = tasks.get(selectedIndex);
                if (task != null)
                    onTaskSelected.accept(task.id);
                else
                    onTaskSelected.accept(-1);
            }
            return true;
        } else {
            selectedIndex = -1;
            if (onTaskSelected != null) {
                onTaskSelected.accept(-1);
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        // 无障碍功能支持
    }

    @Override
    protected int getInnerHeight() {
        return tasks.size() * taskHeight;
    }

    @Override
    protected double scrollRate() {
        return 5;
    }

    private Component getStatusText(FabricatorBlockEntity.TaskStatus status) {
        return switch (status) {
            case QUEUED -> Component.translatable("gui.machine_max.fabricator.status.queued");
            case PRODUCING -> Component.translatable("gui.machine_max.fabricator.status.producing");
            case COMPLETED -> Component.translatable("gui.machine_max.fabricator.status.completed");
            default -> Component.translatable("gui.machine_max.fabricator.status.idle");
        };
    }

    private int getStatusColor(FabricatorBlockEntity.TaskStatus status) {
        return switch (status) {
            case QUEUED -> 0xFFFFA500; // 橙色
            case PRODUCING -> 0xFF55AAFF; // 蓝色
            case COMPLETED -> 0xFF55FF55; // 绿色
            default -> 0xFF888888; // 灰色
        };
    }

    private int getTaskBackgroundColor(FabricatorBlockEntity.TaskStatus status, boolean isSelected, boolean isHovered) {
        int baseColor = switch (status) {
            case QUEUED -> 0xFF3A2A1A;    // 深橙色背景
            case PRODUCING -> 0xFF1A2A3A; // 深蓝色背景
            case COMPLETED -> 0xFF1A3A1A; // 深绿色背景
            default -> 0xFF2A2A2A;        // 深灰色背景
        };

        if (isSelected) {
            return lightenColor(baseColor, 0.3f);
        } else if (isHovered) {
            return lightenColor(baseColor, 0.15f);
        }

        return baseColor;
    }

    private int lightenColor(int color, float factor) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        r = Mth.clamp((int)(r + (255 - r) * factor), 0, 255);
        g = Mth.clamp((int)(g + (255 - g) * factor), 0, 255);
        b = Mth.clamp((int)(b + (255 - b) * factor), 0, 255);

        return (r << 16) | (g << 8) | b;
    }

    // 清除选中状态
    public void clearSelection() {
        selectedIndex = -1;
    }
}