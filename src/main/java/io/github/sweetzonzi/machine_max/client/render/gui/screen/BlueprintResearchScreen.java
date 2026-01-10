package io.github.sweetzonzi.machine_max.client.render.gui.screen;

import io.github.sweetzonzi.machine_max.client.render.gui.renderable.ResearchRecipeListWidget;
import io.github.sweetzonzi.machine_max.common.attachment.BluePrintAttachment;
import io.github.sweetzonzi.machine_max.common.menu.BlueprintResearchMenu;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchCancelPayload;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchClaimPayload;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchSetPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * 蓝图研发 Screen
 * UI 仅展示科研状态，不参与任何科研逻辑计算。
 */
public class BlueprintResearchScreen extends AbstractContainerScreen<BlueprintResearchMenu> {
    private EditBox searchBox;
    private ResearchRecipeListWidget recipeList;
    /**
     * 当前选中的科研条目（用于右侧详情）
     */
    private ResearchState selected;
    private List<ResearchState> states;

    public BlueprintResearchScreen(BlueprintResearchMenu menu,
                                   Inventory inventory,
                                   Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        // 搜索框：位于配方列表正上方
        searchBox = new EditBox(
                font,
                leftPos + 8,
                topPos + 6,
                160,
                12,
                Component.empty()
        );
        searchBox.setMaxLength(64);
        searchBox.setBordered(true);
        searchBox.setResponder(s -> rebuildEntries());

        addRenderableWidget(searchBox);

        // 配方列表
        recipeList = new ResearchRecipeListWidget(
                minecraft,
                leftPos + 8,
                topPos + 18,
                160,
                140
        );
        recipeList.setCallbacks(new ResearchRecipeListWidget.Callbacks() {
            @Override
            public void onSelect(ResearchState state) {
                selected = state;
            }

            @Override
            public void onStart(ResearchState state) {
                PacketDistributor.sendToServer(new ResearchSetPayload(state.recipe().id()));
            }

            @Override
            public void onCancel(ResearchState state) {
                PacketDistributor.sendToServer(new ResearchCancelPayload());
            }

            @Override
            public void onClaim(ResearchState state) {
                PacketDistributor.sendToServer(new ResearchClaimPayload(state.recipe().id()));
            }

            @Override
            public void onReclaim(ResearchState state) {
                PacketDistributor.sendToServer(new ResearchClaimPayload(state.recipe().id()));
            }
        });
        rebuildEntries();

        addRenderableWidget(recipeList);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.getFocused() != null && this.isDragging() && button == 0 && this.getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void rebuildEntries() {
        BluePrintAttachment research = menu.getResearch();
        String filter = searchBox != null
                ? searchBox.getValue().toLowerCase()
                : "";

        this.states = research.getAllResearchable().values().stream()
                // === 搜索过滤 ===
                .filter(holder -> {
                    if (filter.isEmpty()) return true;
                    ItemStack result = holder.value()
                            .getResultItem(minecraft.level.registryAccess());
                    return result.getHoverName()
                            .getString()
                            .toLowerCase()
                            .contains(filter);
                })
                // === 构建 ResearchState ===
                .map(holder -> {
                    int level = research.getResearchLevel(holder.id());
                    float totalProgress = research.getResearchedRecipes()
                            .getOrDefault(holder.id(), 0f);
                    float progress = totalProgress - level;
                    boolean hasProduct = research.getProducts().getOrDefault(holder.id(), ItemStack.EMPTY) != ItemStack.EMPTY;
                    boolean researching = holder.id() .equals(research.getResearchingRecipe());
                    boolean canResearch = research.canStartResearching(minecraft.player, holder.id());
                    boolean unlocked = level >= 1;
                    boolean canReclaim = research.canReclaim(holder.id());

                    return new ResearchState(
                            holder,
                            level,
                            progress,
                            (int) (progress * holder.value().getResearchCost()),
                            holder.value().getResearchCost(),
                            hasProduct,
                            researching,
                            canResearch,
                            unlocked,
                            canReclaim
                    );
                })
                .toList();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // 每 tick 重新计算 UI 状态
        rebuildEntries();
        recipeList.setStates(this.states);
        if (!states.contains(selected)) selected = null;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        return; // 不渲染标题栏标签
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBlurredBackground(partialTick);
        this.renderBg(guiGraphics, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 使用纯色背景区分区域
        int bgColor = new Color(25, 25, 25, 128).getRGB();
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, bgColor);
        // 显示自由研发点
        graphics.drawString(font, "Free Rp: " + menu.getResearch().getFreeResearchPoint(), leftPos + 8, topPos - 4, Color.WHITE.getRGB());
        // 绘制区域分隔线
        int lineColor = new Color(25, 25, 25, 128).getRGB();
        graphics.fill(leftPos + 100, topPos + 5, leftPos + 102, topPos + imageHeight - 5, lineColor); // 左分隔
        graphics.fill(leftPos + imageWidth - 100, topPos + 5, leftPos + imageWidth - 102, topPos + imageHeight - 5, lineColor); // 右分隔

        // 绘制水平分隔线
        graphics.fill(leftPos + 100, topPos + 110, leftPos + imageWidth - 100, topPos + 112, lineColor); // 材料区域上方
        graphics.fill(leftPos + 100, topPos + 200, leftPos + imageWidth - 100, topPos + 202, lineColor); // 状态区域上方
    }
}
