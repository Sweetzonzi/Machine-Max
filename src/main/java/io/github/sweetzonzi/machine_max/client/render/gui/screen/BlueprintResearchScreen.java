package io.github.sweetzonzi.machine_max.client.render.gui.screen;

import io.github.sweetzonzi.machine_max.client.render.gui.renderable.ItemModelWidget;
import io.github.sweetzonzi.machine_max.client.render.gui.renderable.TabbedMaterialWidget;
import io.github.sweetzonzi.machine_max.client.render.gui.renderable.ResearchRecipeListWidget;
import io.github.sweetzonzi.machine_max.common.attachment.BlueprintAttachment;
import io.github.sweetzonzi.machine_max.common.menu.BlueprintResearchMenu;
import io.github.sweetzonzi.machine_max.common.recipe.BlueprintResearchRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.ResearchRecipe;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchClaimPayload;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchCompleteRequestPayload;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchReclaimPayload;
import io.github.sweetzonzi.machine_max.util.PartTagTextUtil;
import lombok.Getter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

public class BlueprintResearchScreen extends AbstractContainerScreen<BlueprintResearchMenu> {
    private static ResourceLocation LAST_SELECTED_RESEARCH_ID;

    private EditBox searchBox;
    private ItemModelWidget modelWidget;
    private ResearchRecipeListWidget recipeList;
    @Getter
    private TabbedMaterialWidget materialWidget;

    private static final int THEME = new Color(255, 100, 0, 128).getRGB();

    private ResearchState selected;
    private List<ResearchState> states;

    public BlueprintResearchScreen(BlueprintResearchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 400;
        this.imageHeight = 220;
    }

    @Override
    protected void init() {
        super.init();

        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2 + 5;

        searchBox = new EditBox(font, leftPos + 8, topPos + 6, 160, 12, Component.empty());
        searchBox.setMaxLength(64);
        searchBox.setBordered(true);
        searchBox.setResponder(s -> rebuildEntries());
        addRenderableWidget(searchBox);

        recipeList = new ResearchRecipeListWidget(minecraft, leftPos + 8, topPos + 18, 160, 195);
        recipeList.setCallbacks(new ResearchRecipeListWidget.Callbacks() {
            @Override
            public void onSelect(ResearchState state) {
                applySelection(state);
            }

            @Override
            public void onComplete(ResearchState state) {
                PacketDistributor.sendToServer(new ResearchCompleteRequestPayload(state.recipe().id()));
            }

            @Override
            public void onClaim(ResearchState state) {
                if (state.blueprintResearch()) {
                    PacketDistributor.sendToServer(new ResearchClaimPayload(state.recipe().id()));
                }
            }

            @Override
            public void onReclaim(ResearchState state) {
                if (state.blueprintResearch()) {
                    PacketDistributor.sendToServer(new ResearchReclaimPayload(state.recipe().id()));
                }
            }
        });
        rebuildEntries();
        addRenderableWidget(recipeList);

        this.materialWidget = new TabbedMaterialWidget(leftPos + 175, topPos + 135, 220, 78);
        this.addRenderableWidget(materialWidget);

        this.modelWidget = new ItemModelWidget(leftPos + this.imageWidth - 125, topPos + 5, 120, 120);
        this.modelWidget.setScale(20.0f);
        this.modelWidget.setEmptyText(Component.translatable("gui.machine_max.research.no_preview"));
        this.addRenderableWidget(modelWidget);

        restoreSelection();
    }

    /**
     * 获取制造产物区域中指定鼠标位置的物品，用于 JEI 查询
     */
    public ItemStack getProductIngredientAt(double mouseX, double mouseY) {
        if (selected != null && selected.blueprintResearch() && selected.fabricatingRecipe() != null
                && minecraft != null && minecraft.level != null) {
            int infoX = leftPos + 175;
            int infoY = topPos + 8;
            if (mouseX >= infoX && mouseX < infoX + 18 && mouseY >= infoY && mouseY < infoY + 18) {
                return selected.fabricatingRecipe().value().getResultItem(minecraft.level.registryAccess());
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.getFocused() != null && (button == 0 || button == 1) && this.getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused() && minecraft != null
                && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            // 搜索框输入状态下，背包键不应关闭研发菜单
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void rebuildEntries() {
        BlueprintAttachment research = menu.getResearch();
        String filter = PartTagTextUtil.normalize(searchBox != null ? searchBox.getValue() : "");

        this.states = research.getAllResearchable().values().stream()
                .filter(holder -> matchesSearchFilter(holder, filter))
                .map(holder -> {
                    ResourceLocation researchId = holder.id();
                    boolean completed = research.isResearched(researchId);
                    boolean unlockable = research.hasSatisfiedPrerequisites(minecraft.player, researchId);
                    boolean canComplete = research.canCompleteResearch(minecraft.player, researchId);
                    int missing = research.getMissingPrerequisites(researchId).size();

                    ResourceLocation unlockedRecipe = null;
                    ItemStack previewItem = ItemStack.EMPTY;
                    boolean hasProduct = false;
                    boolean canReclaim = false;
                    boolean blueprintResearch = false;
                    RecipeHolder<FabricatingRecipe> fabHolder = null;

                    var blueprintHolder = MMDynamicRes.BLUEPRINT_RESEARCH_RECIPES.get(researchId);
                    if (blueprintHolder != null) {
                        blueprintResearch = true;
                        BlueprintResearchRecipe blueprintResearchRecipe = blueprintHolder.value();
                        unlockedRecipe = blueprintResearchRecipe.getUnlockRecipe();
                        fabHolder = MMDynamicRes.ALL_FABRICATING_RECIPES.get(unlockedRecipe);
                        if (fabHolder != null && minecraft != null && minecraft.level != null) {
                            previewItem = fabHolder.value().getResultItem(minecraft.level.registryAccess());
                        }
                        hasProduct = research.getProducts().getOrDefault(researchId, ItemStack.EMPTY) != ItemStack.EMPTY;
                        canReclaim = research.canReclaim(researchId);
                    }

                    return new ResearchState(
                            holder,
                            fabHolder,
                            completed,
                            unlockable,
                            canComplete,
                            research.getFreeResearchPoint(),
                            holder.value().getResearchCost(),
                            missing,
                            blueprintResearch,
                            unlockedRecipe,
                            previewItem,
                            hasProduct,
                            canReclaim
                    );
                })
                .toList();
    }

    private void applySelection(ResearchState state) {
        selected = state;
        LAST_SELECTED_RESEARCH_ID = state.recipe().id();
        if (materialWidget != null) {
            ResearchRecipe researchRecipe = state.recipe().value();
            FabricatingRecipe fabRecipe = state.fabricatingRecipe() != null
                    ? state.fabricatingRecipe().value() : null;
            materialWidget.setRecipes(researchRecipe, fabRecipe);
        }
        if (modelWidget != null) {
            modelWidget.setItemStack(state.previewItem());
            modelWidget.setEmptyText(state.blueprintResearch()
                    ? Component.translatable("gui.machine_max.research.no_blueprint_product")
                    : Component.translatable("gui.machine_max.research.no_preview"));
        }
    }

    private void restoreSelection() {
        if (recipeList == null || states == null || states.isEmpty()) {
            selected = null;
            return;
        }

        ResourceLocation targetId = LAST_SELECTED_RESEARCH_ID;
        if (targetId == null && selected != null) {
            targetId = selected.recipe().id();
        }
        if (targetId == null) {
            return;
        }

        ResearchState restored = recipeList.selectByResearchId(targetId);
        if (restored != null) {
            applySelection(restored);
        } else {
            selected = null;
        }
    }

    private boolean matchesSearchFilter(RecipeHolder<? extends ResearchRecipe> holder, String filter) {
        if (filter.isEmpty()) return true;
        if (PartTagTextUtil.normalize(holder.id().toString()).contains(filter)) return true;

        RecipeHolder<BlueprintResearchRecipe> blueprintHolder = MMDynamicRes.BLUEPRINT_RESEARCH_RECIPES.get(holder.id());
        if (blueprintHolder == null) return false;

        ResourceLocation unlockRecipeId = blueprintHolder.value().getUnlockRecipe();
        if (PartTagTextUtil.normalize(unlockRecipeId.toString()).contains(filter)) return true;

        RecipeHolder<FabricatingRecipe> unlockedHolder = MMDynamicRes.ALL_FABRICATING_RECIPES.get(unlockRecipeId);
        if (unlockedHolder == null) return false;

        var partType = PartTagTextUtil.resolvePartTypeForTooltip(unlockedHolder.value().getResult());
        for (String text : PartTagTextUtil.collectSearchTexts(partType)) {
            if (text.contains(filter)) return true;
        }
        return false;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        rebuildEntries();
        recipeList.setStates(this.states);
        restoreSelection();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        return;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBlurredBackground(partialTick);
        this.renderBg(guiGraphics, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int bgColor1 = new Color(25, 25, 25, 64).getRGB();
        int bgColor2 = new Color(25, 25, 25, 128).getRGB();
        graphics.fillGradient(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, bgColor1, bgColor2);

        int lineColor = new Color(25, 25, 25, 128).getRGB();
        graphics.fill(leftPos + 170, topPos + 5, leftPos + 172, topPos + imageHeight - 5, lineColor);
        graphics.fill(leftPos + imageWidth - 130, topPos + 5, leftPos + imageWidth - 132, topPos + 128, lineColor);
        graphics.fill(leftPos + 172, topPos + 130, leftPos + imageWidth - 5, topPos + 132, lineColor);
        graphics.fill(leftPos, topPos, leftPos + 5, topPos + imageHeight, THEME);

        /* 制造信息区：产物图标 + 制造时间 + 蓝图描述 */
        if (selected != null && selected.blueprintResearch() && selected.fabricatingRecipe() != null) {
            FabricatingRecipe recipe = selected.fabricatingRecipe().value();
            int infoX = leftPos + 175;
            int infoY = topPos + 8;

            /* 产物图标 + 数量角标 */
            ItemStack result = recipe.getResultItem(minecraft.level.registryAccess());
            graphics.renderItem(result, infoX, infoY);
            graphics.renderItemDecorations(font, result, infoX, infoY);

            /* 制造时间 */
            int seconds = recipe.getProcessingTime() / 20;
            Component timeText = Component.translatable("gui.machine_max.research.fabrication_time",
                    Component.literal(String.valueOf(seconds)));
            graphics.drawString(font, timeText, infoX + 22, infoY + 2, Color.WHITE.getRGB(), false);

            /* 蓝图描述（换行显示，最多4行） */
            String desc = recipe.getTooltip();
            if (desc != null && !desc.isEmpty()) {
                int descX = infoX;
                int descY = infoY + 20;
                var lines = font.split(Component.literal(desc), 90);
                int maxLines = Math.min(lines.size(), 4);
                for (int i = 0; i < maxLines; i++) {
                    graphics.drawString(font, lines.get(i), descX, descY + i * (font.lineHeight + 1),
                            new Color(180, 180, 180).getRGB(), false);
                }
            }
        }
    }
}
