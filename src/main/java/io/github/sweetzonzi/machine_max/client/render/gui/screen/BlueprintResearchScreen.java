package io.github.sweetzonzi.machine_max.client.render.gui.screen;

import io.github.sweetzonzi.machine_max.client.render.gui.renderable.ItemModelWidget;
import io.github.sweetzonzi.machine_max.client.render.gui.renderable.MaterialRequirementsWidget;
import io.github.sweetzonzi.machine_max.client.render.gui.renderable.ResearchRecipeListWidget;
import io.github.sweetzonzi.machine_max.common.attachment.BlueprintAttachment;
import io.github.sweetzonzi.machine_max.common.menu.BlueprintResearchMenu;
import io.github.sweetzonzi.machine_max.network.payload.research.*;
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
    private ItemModelWidget modelWidget;
    private ResearchRecipeListWidget recipeList;
    private MaterialRequirementsWidget materialWidget;

    private static final int THEME = new Color(255, 100, 0, 128).getRGB();

    /**
     * 当前选中的科研条目（用于右侧详情）
     */
    private ResearchState selected;
    private List<ResearchState> states;

    public BlueprintResearchScreen(BlueprintResearchMenu menu,
                                   Inventory inventory,
                                   Component title) {
        super(menu, inventory, title);
        this.imageWidth = 400;
        this.imageHeight = 220;
    }

    @Override
    protected void init() {
        super.init();

        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2 + 5;

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
                195
        );
        recipeList.setCallbacks(new ResearchRecipeListWidget.Callbacks() {
            @Override
            public void onSelect(ResearchState state) {
                selected = state;
                if (materialWidget != null) {
                    materialWidget.setRecipe(state.recipe().value());
                }
                if (modelWidget != null) {
                    modelWidget.setItemStack(state.recipe().value().getResultItem(minecraft.level.registryAccess()));
                }
            }

            @Override
            public void onStart(ResearchState state) {
                PacketDistributor.sendToServer(new ResearchSetPayload(state.recipe().id()));
            }

            @Override
            public void onApplyFreeRp(ResearchState state) {
                PacketDistributor.sendToServer(new ResearchApplyFreeRpPayload(state.recipe().id()));
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
                PacketDistributor.sendToServer(new ResearchReclaimPayload(state.recipe().id()));
            }
        });
        rebuildEntries();
        addRenderableWidget(recipeList);

        // 材料需求显示
        this.materialWidget = new MaterialRequirementsWidget(
                leftPos + 175,
                topPos + 133,
                220,
                80,
                true
        );
        this.addRenderableWidget(materialWidget);

        // 3D模型预览
        this.modelWidget = new ItemModelWidget(
                leftPos + this.imageWidth - 120 - 5,
                topPos + 5,
                120,
                120
        );
        this.modelWidget.setScale(20.0f);
        this.addRenderableWidget(modelWidget);

    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.getFocused() != null && (button == 0 || button == 1) && this.getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void rebuildEntries() {
        BlueprintAttachment research = menu.getResearch();
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
                    boolean researching = holder.id().equals(research.getResearchingRecipe());
                    boolean canResearch = research.canStartResearching(minecraft.player, holder.id());
                    boolean unlocked = level >= 1;
                    boolean canReclaim = research.canReclaim(holder.id());

                    return new ResearchState(
                            holder,
                            research.hasStartedResearching(holder.id()),
                            level,
                            progress,
                            (int) (progress * research.getRpCost(holder.id())),
                            research.getFreeResearchPoint(),
                            research.getRpCost(holder.id()),
                            hasProduct,
                            researching,
                            canResearch,
                            unlocked,
                            canReclaim,
                            research.getReclaimRpCost(holder.id())
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
        // 物品详情
        if (selected != null) {
            guiGraphics.drawCenteredString(font, "///WIP///", leftPos + 220, topPos + 58, 0xAAAAAA);
        } else {
            guiGraphics.drawCenteredString(font, "///WIP///", leftPos + 220, topPos + 58, 0xAAAAAA);
        }
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 使用纯色背景区分区域
        int bgColor1 = new Color(25, 25, 25, 64).getRGB();
        int bgColor2 = new Color(25, 25, 25, 128).getRGB();
        graphics.fillGradient(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, bgColor1, bgColor2);
        // 绘制区域分隔线
        int lineColor = new Color(25, 25, 25, 128).getRGB();
        graphics.fill(leftPos + 170, topPos + 5, leftPos + 172, topPos + imageHeight - 5, lineColor); // 左分隔
        graphics.fill(leftPos + imageWidth - 130, topPos + 5, leftPos + imageWidth - 132, topPos + 128, lineColor); // 右分隔
        // 绘制水平分隔线
        graphics.fill(leftPos + 172, topPos + 128, leftPos + imageWidth - 5, topPos + 130, lineColor); // 材料区域上方
        graphics.fill(leftPos, topPos, leftPos + 5, topPos + imageHeight, THEME);
        // 显示自由研发点
        Component freeRpText = Component.translatable("gui.machine_max.research.free_rp")
                .append(Component.literal(String.valueOf(getMenu().getResearch().getFreeResearchPoint())));
        graphics.fill(0, 0, font.width(freeRpText.getString()) + 2, 10, bgColor1);
        graphics.drawString(font, freeRpText, 1, 1, Color.WHITE.getRGB());

    }
}
