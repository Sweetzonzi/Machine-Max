package io.github.sweetzonzi.machine_max.client.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sweetzonzi.machine_max.client.gui.renderable.*;
import io.github.sweetzonzi.machine_max.common.block.fabricator.FabricatorBlockEntity;
import io.github.sweetzonzi.machine_max.common.menu.FabricatingMenu;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.network.payload.fabrication.FabricationCancelPayload;
import io.github.sweetzonzi.machine_max.network.payload.fabrication.FabricationCollectAllPayload;
import io.github.sweetzonzi.machine_max.network.payload.fabrication.FabricationCollectPayload;
import io.github.sweetzonzi.machine_max.network.payload.fabrication.FabricationStartPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public class FabricatingScreen extends AbstractContainerScreen<FabricatingMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("machine_max", "textures/gui/fabricator.png");

    private EditBox searchBox;
    private RecipeListWidget recipeListWidget;
    private TaskListWidget taskListWidget;
    private MaterialRequirementsWidget materialWidget;
    private ItemModelWidget modelWidget;
    private RecipeDetailsWidget detailsWidget;
    private Button startProductionButton;
    private Button cancelSelectedTaskButton;
    private Button collectSelectedTaskButton;
    private Button collectAllTaskButton;
    private RecipeHolder<FabricatingRecipe> selectedRecipe;
    private int selectedTaskIndex = -1;

    public FabricatingScreen(FabricatingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, Component.empty());
        this.imageWidth = 400;
        this.imageHeight = 225;
    }

    @Override
    protected void init() {
        super.init();

        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        // 获取制造机效率
        float efficiency = menu.getFabricatorBlockEntity().getEfficiency();

        // 搜索框
        this.searchBox = new EditBox(this.font, leftPos + 5, topPos + 5, 90, 15, Component.empty());
        this.searchBox.setMaxLength(32);
        this.searchBox.setHint(Component.translatable("gui.machine_max.fabricator.search_hint"));
        this.addRenderableWidget(searchBox);

        // 配方列表
        this.recipeListWidget = new RecipeListWidget(
                minecraft,
                leftPos + 5,
                topPos + 25,
                90,
                195,
                menu.getFabricatorBlockEntity()
        );
        this.recipeListWidget.setOnRecipeSelected(this::onRecipeSelected);
        this.addRenderableWidget(recipeListWidget);

        // 任务列表
        this.taskListWidget = new TaskListWidget(
                minecraft,
                leftPos + imageWidth - 95,
                topPos + 25,
                90,
                145,
                menu.getFabricatorBlockEntity()
        );
        this.taskListWidget.setTaskSelectedCallback(this::onTaskSelected);
        this.addRenderableWidget(taskListWidget);

        // 3D模型预览
        this.modelWidget = new ItemModelWidget(
                leftPos + 105,
                topPos + 25,
                80,
                80
        );
        this.modelWidget.setScale(20.0f);
        this.addRenderableWidget(modelWidget);

        // 配方详情
        this.detailsWidget = new RecipeDetailsWidget(
                leftPos + 195,
                topPos + 25,
                100,
                80
        );
        this.detailsWidget.setEfficiency(efficiency);
        this.addRenderableWidget(detailsWidget);

        // 材料需求显示
        this.materialWidget = new MaterialRequirementsWidget(
                leftPos + 105,
                topPos + 116,
                190,
                80
        );
        this.addRenderableWidget(materialWidget);

        // 开始生产按钮
        this.startProductionButton = Button.builder(
                Component.translatable("gui.machine_max.fabricator.start_production"),
                this::onStartProduction
        ).bounds(
                leftPos + imageWidth - 49,
                topPos + 176,
                44,
                20
        ).build();
        this.addRenderableWidget(startProductionButton);

        // 取消选中任务按钮
        this.cancelSelectedTaskButton = Button.builder(
                Component.translatable("gui.machine_max.fabricator.cancel_task"),
                this::onCancelTask
        ).bounds(
                leftPos + imageWidth - 95,
                topPos + 176,
                44,
                20
        ).build();
        this.cancelSelectedTaskButton.active = false;
        this.addRenderableWidget(cancelSelectedTaskButton);

        // 领取选中任务按钮
        this.collectSelectedTaskButton = Button.builder(
                Component.translatable("gui.machine_max.fabricator.collect_task"),
                this::onCollectTask
        ).bounds(
                leftPos + imageWidth - 95,
                topPos + 176,
                45,
                20
        ).build();
        this.collectSelectedTaskButton.active = false;
        this.collectSelectedTaskButton.visible = false;
        this.addRenderableWidget(collectSelectedTaskButton);

        // 领取所有任务按钮
        this.collectAllTaskButton = Button.builder(
                Component.translatable("gui.machine_max.fabricator.collect_all_task"),
                this::onCollectAllTask
        ).bounds(
                leftPos + imageWidth - 95,
                topPos + 200,
                90,
                20
        ).build();
        this.collectAllTaskButton.active = false;
        this.collectAllTaskButton.visible = true;
        this.addRenderableWidget(collectAllTaskButton);

        // 搜索框响应
        this.searchBox.setResponder(text -> {
            if (recipeListWidget != null) {
                recipeListWidget.setSearchFilter(text);
            }
        });

        // 初始选择第一个配方
//        FabricatingRecipe firstRecipe = recipeListWidget.getSelectedRecipe();
//        if (firstRecipe != null) {
//            onRecipeSelected(firstRecipe);
//        }
    }

    private void onRecipeSelected(RecipeHolder<FabricatingRecipe> recipe) {
        this.selectedRecipe = recipe;
        if (materialWidget != null) {
            materialWidget.setRecipe(recipe.value());
        }
        if (modelWidget != null && minecraft != null) {
            var result = recipe.value().getResultItem(minecraft.level.registryAccess());
            modelWidget.setItemStack(result);
        }
        if (detailsWidget != null) {
            detailsWidget.setRecipe(recipe.value());
        }
        updateProductionButtonState();
    }

    private void onTaskSelected(int taskIndex) {
        this.selectedTaskIndex = taskIndex;
        updateTaskActionButtons();
    }

    private void onStartProduction(Button button) {
        if (selectedRecipe != null && minecraft != null && minecraft.player != null) {
            //发送网络包给服务器，开始生产任务
            PacketDistributor.sendToServer(new FabricationStartPayload(selectedRecipe.id()));
            // 等待服务器同步前提前更新客户端物品栏
            Player player = minecraft.player;
            if (!player.isCreative()) {
                selectedRecipe.value().consumeIngredients(player);
            }
            updateTaskActionButtons();
        }
    }

    private void onCancelTask(Button button) {
        if (selectedTaskIndex >= 0) {
            //发送网络包取消任务
            PacketDistributor.sendToServer(new FabricationCancelPayload(selectedTaskIndex));
            updateTaskActionButtons();
        }
    }

    private void onCollectTask(Button button) {
        if (selectedTaskIndex >= 0 && minecraft != null && minecraft.player != null) {
            //发送网络包领取物品
            PacketDistributor.sendToServer(new FabricationCollectPayload(selectedTaskIndex));
            // 等待服务器同步前提前更新客户端物品栏
            Player player = minecraft.player;
            if (!player.isCreative()) {
                menu.getFabricatorBlockEntity().collectTask(selectedTaskIndex, player);
            }
            updateTaskActionButtons();
        }
    }

    private void onCollectAllTask(Button button) {
        if (menu.getFabricatorBlockEntity().getCompletedTaskCount() > 0 && minecraft != null && minecraft.player != null) {
            //发送网络包领取物品
            PacketDistributor.sendToServer(new FabricationCollectAllPayload());
            // 等待服务器同步前提前更新客户端物品栏
            Player player = minecraft.player;
            if (!player.isCreative()) {
                menu.getFabricatorBlockEntity().collectAllTasks(player);
            }
            updateTaskActionButtons();
        }
    }

    private void updateProductionButtonState() {
        if (startProductionButton == null || selectedRecipe == null || minecraft == null) return;

        boolean canProduce = selectedRecipe.value().hasRequiredIngredients(minecraft.player) || minecraft.player.isCreative();
        boolean hasFreeSlot = menu.getFabricatorBlockEntity().getIdleTaskCount() > 0;

        startProductionButton.active = canProduce && hasFreeSlot;

        if (!canProduce) {
            startProductionButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("gui.machine_max.fabricator.insufficient_materials")
            ));
        } else if (!hasFreeSlot) {
            startProductionButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("gui.machine_max.fabricator.no_free_slots")
            ));
        } else {
            startProductionButton.setTooltip(null);
        }
    }

    private void updateTaskActionButtons() {
        collectAllTaskButton.active = menu.getFabricatorBlockEntity().getCompletedTaskCount() > 0;
        if (selectedTaskIndex < 0) {
            cancelSelectedTaskButton.active = false;
            cancelSelectedTaskButton.visible = true;
            collectSelectedTaskButton.active = false;
            collectSelectedTaskButton.visible = false;
            return;
        }
        var task = menu.getFabricatorBlockEntity().getTaskAtSlot(selectedTaskIndex);
        if (task != null) {
            boolean isCompleted = task.status == FabricatorBlockEntity.TaskStatus.COMPLETED;
            cancelSelectedTaskButton.active = !isCompleted;
            cancelSelectedTaskButton.visible = !isCompleted;
            collectSelectedTaskButton.active = isCompleted;
            collectSelectedTaskButton.visible = isCompleted;
        } else {
            cancelSelectedTaskButton.active = false;
            cancelSelectedTaskButton.visible = true;
            collectSelectedTaskButton.active = false;
            collectSelectedTaskButton.visible = false;
        }

    }

    // 关键修改：重写按键处理方法
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 如果搜索框有焦点，处理特殊按键
        if (searchBox != null && searchBox.isFocused()) {
            // E 键 - 在搜索框中输入 'e' 而不是关闭界面
            if (Minecraft.getInstance().options.keyInventory.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
                // 让搜索框处理 E 键输入
                searchBox.keyPressed(keyCode, scanCode, modifiers);
                return true;
            }
            // Enter 键 - 移除搜索框焦点
            else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                searchBox.setFocused(false);
                return true;
            }
        }

        // 其他情况调用父类方法
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);

        int textColor = new Color(255, 255, 255, 255).getRGB();
        // 渲染标题和标签
        graphics.drawString(this.font, Component.translatable("gui.machine_max.fabricator.tasks").withStyle(ChatFormatting.BOLD),
                leftPos + imageWidth - 95, topPos + 8, textColor, false);

        // 渲染中央区域标题
        graphics.drawString(this.font, Component.translatable("gui.machine_max.fabricator.preview").withStyle(ChatFormatting.BOLD),
                leftPos + 105, topPos + 8, textColor, false);

        // 显示空闲任务槽信息 TODO:能量状态？
        int idleSlots = menu.getFabricatorBlockEntity().getIdleTaskCount();
        int allSlots = menu.getFabricatorBlockEntity().getMaxTaskSize();
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.machine_max.fabricator.free_slots", idleSlots, allSlots),
                width / 2, topPos + 208,
                idleSlots > 0 ? 0x55FF55 : 0xFF5555);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        return; // 不渲染标题栏标签
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 使用纯色背景区分区域
        int bgColor = new Color(25, 25, 25, 128).getRGB();
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, bgColor);

        // 绘制区域分隔线
        int lineColor = new Color(25, 25, 25, 128).getRGB();
        graphics.fill(leftPos + 100, topPos + 5, leftPos + 102, topPos + imageHeight - 5, lineColor); // 左分隔
        graphics.fill(leftPos + imageWidth - 100, topPos + 5, leftPos + imageWidth - 102, topPos + imageHeight - 5, lineColor); // 右分隔

        // 绘制水平分隔线
        graphics.fill(leftPos + 100, topPos + 110, leftPos + imageWidth - 100, topPos + 112, lineColor); // 材料区域上方
        graphics.fill(leftPos + 100, topPos + 200, leftPos + imageWidth - 100, topPos + 202, lineColor); // 状态区域上方
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBlurredBackground(partialTick);
        this.renderBg(guiGraphics, partialTick, mouseX, mouseY);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        // 更新任务列表进度
        if (taskListWidget != null) {
            taskListWidget.tick();
        }
        // 更新材料需求显示
        if (materialWidget != null) {
            materialWidget.updateMaterialEntries();
        }
        // 更新生产按钮状态（玩家背包可能发生变化）
        updateProductionButtonState();
        updateTaskActionButtons();
    }

    public static String formatTime(int ticks) {
        int seconds = ticks / 20;
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int remainingSeconds = seconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds);
        } else if (minutes > 0) {
            return String.format("%02d:%02d", minutes, remainingSeconds);
        } else {
            return String.format("00:%02d", remainingSeconds);
        }
    }
}