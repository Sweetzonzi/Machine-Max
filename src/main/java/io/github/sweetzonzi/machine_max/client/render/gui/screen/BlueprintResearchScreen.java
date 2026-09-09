package io.github.sweetzonzi.machine_max.client.render.gui.screen;

import io.github.sweetzonzi.machine_max.client.blueprint.BlueprintLibraryClient;
import io.github.sweetzonzi.machine_max.client.render.gui.renderable.BlueprintLibraryListWidget;
import io.github.sweetzonzi.machine_max.client.render.gui.renderable.BlueprintMaterialWidget;
import io.github.sweetzonzi.machine_max.client.render.gui.renderable.ItemModelWidget;
import io.github.sweetzonzi.machine_max.client.render.gui.renderable.TabbedMaterialWidget;
import io.github.sweetzonzi.machine_max.client.render.gui.renderable.ResearchRecipeListWidget;
import io.github.sweetzonzi.machine_max.common.attachment.BlueprintAttachment;
import io.github.sweetzonzi.machine_max.common.item.prop.VehicleBlueprintItem;
import io.github.sweetzonzi.machine_max.common.menu.BlueprintResearchMenu;
import io.github.sweetzonzi.machine_max.common.recipe.BlueprintResearchRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.ResearchRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.registry.MMItems;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.network.payload.library.BlueprintExtractLocalPayload;
import io.github.sweetzonzi.machine_max.network.payload.library.BlueprintExtractPackPayload;
import io.github.sweetzonzi.machine_max.network.payload.library.BlueprintStoreRequestPayload;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchClaimPayload;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchCompleteRequestPayload;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchReclaimPayload;
import io.github.sweetzonzi.machine_max.util.TextUtil;
import lombok.Getter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
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

    /** 标签页：研发 / 蓝图库（纯客户端 UI 状态，不影响 Menu） */
    private enum Tab { RESEARCH, LIBRARY }

    private Tab activeTab = Tab.RESEARCH;

    private BlueprintLibraryListWidget libraryList;
    private BlueprintMaterialWidget libraryMaterialWidget;
    private EditBox renameBox;
    private Button renameConfirmButton;
    private Button storeButton;
    private Button researchTabButton;
    private Button libraryTabButton;

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

        // 标签页按钮（位于 GUI 上方）
        int tabY = topPos - 18;
        this.researchTabButton = Button.builder(
                        Component.translatable("gui.machine_max.research.tab.research"),
                        button -> switchTab(Tab.RESEARCH))
                .bounds(leftPos, tabY, 76, 16).build();
        this.libraryTabButton = Button.builder(
                        Component.translatable("gui.machine_max.research.tab.library"),
                        button -> switchTab(Tab.LIBRARY))
                .bounds(leftPos + 80, tabY, 76, 16).build();
        this.addRenderableWidget(researchTabButton);
        this.addRenderableWidget(libraryTabButton);

        // 蓝图库标签页控件（与研发标签页共用预览区）
        this.libraryList = new BlueprintLibraryListWidget(minecraft, leftPos + 8, topPos + 18, 160, 195);
        this.libraryList.setCallbacks(new BlueprintLibraryListWidget.Callbacks() {
            @Override
            public void onSelect(BlueprintLibraryListWidget.Row row) {
                libraryMaterialWidget.setVehicleData(row.payload());
                modelWidget.setItemStack(buildPreviewStack(row));
                modelWidget.setEmptyText(Component.translatable("gui.machine_max.research.no_preview"));
                if (row.fileName() != null) renameBox.setValue(row.label().getString());
            }

            @Override
            public void onExtract(BlueprintLibraryListWidget.Row row) {
                if (row.type() == BlueprintLibraryListWidget.RowType.LOCAL && row.payload() != null) {
                    PacketDistributor.sendToServer(new BlueprintExtractLocalPayload(
                            row.payload(), row.payload().getMeta()));
                } else if (row.type() == BlueprintLibraryListWidget.RowType.PACK && row.packId() != null) {
                    PacketDistributor.sendToServer(new BlueprintExtractPackPayload(row.packId()));
                }
            }

            @Override
            public void onDelete(BlueprintLibraryListWidget.Row row) {
                if (row.fileName() != null) {
                    BlueprintLibraryClient.delete(row.fileName());
                    refreshLibraryList();
                }
            }

            @Override
            public void onRename(BlueprintLibraryListWidget.Row row) {
                if (row.fileName() != null) {
                    renameBox.setValue(row.label().getString());
                    renameBox.setFocused(true);
                }
            }
        });
        this.addRenderableWidget(libraryList);

        this.libraryMaterialWidget = new BlueprintMaterialWidget(leftPos + 175, topPos + 139, 220, 74);
        this.addRenderableWidget(libraryMaterialWidget);

        // 存入库：把背包中第一个可入库的蓝图（带 VEHICLE_DATA 组件）交给服务端回传
        this.storeButton = Button.builder(
                        Component.translatable("gui.machine_max.blueprint_library.store"),
                        button -> storeFirstStorableBlueprint())
                .bounds(leftPos + 175, topPos + 6, 96, 16).build();
        this.addRenderableWidget(storeButton);

        this.renameBox = new EditBox(font, leftPos + 175, topPos + 26, 60, 16, Component.empty());
        this.renameBox.setMaxLength(50);
        this.addRenderableWidget(renameBox);

        this.renameConfirmButton = Button.builder(
                        Component.translatable("gui.machine_max.blueprint_library.rename"),
                        button -> renameSelectedBlueprint())
                .bounds(leftPos + 237, topPos + 26, 34, 16).build();
        this.addRenderableWidget(renameConfirmButton);

        restoreSelection();
        applyTab();
    }

    /** 切换标签页；进入蓝图库时现场扫描本地目录 */
    private void switchTab(Tab tab) {
        if (activeTab == tab) return;
        activeTab = tab;
        if (tab == Tab.LIBRARY) {
            BlueprintLibraryClient.rescan(minecraft != null ? minecraft.level : null);
        } else {
            BlueprintLibraryClient.stop();
        }
        applyTab();
    }

    /** 应用标签页可见性 */
    private void applyTab() {
        boolean research = activeTab == Tab.RESEARCH;
        if (searchBox != null) {
            searchBox.visible = research;
            searchBox.active = research;
            // 切走时释放焦点，避免隐藏的搜索框继续吞掉按键
            if (!research) searchBox.setFocused(false);
        }
        if (recipeList != null) recipeList.visible = research;
        if (materialWidget != null) materialWidget.visible = research;
        if (libraryList != null) libraryList.visible = !research;
        if (libraryMaterialWidget != null) libraryMaterialWidget.visible = !research;
        if (storeButton != null) storeButton.visible = !research;
        if (renameBox != null) {
            renameBox.visible = !research;
            if (research) renameBox.setFocused(false);
        }
        if (renameConfirmButton != null) renameConfirmButton.visible = !research;
        // 两个标签按钮必须始终可点击：active=false 会禁用点击，选中态改用文字前缀标记
        if (researchTabButton != null) {
            researchTabButton.active = true;
            researchTabButton.setMessage(tabLabel("gui.machine_max.research.tab.research", research));
        }
        if (libraryTabButton != null) {
            libraryTabButton.active = true;
            libraryTabButton.setMessage(tabLabel("gui.machine_max.research.tab.library", !research));
        }
        if (!research) {
            refreshLibraryList();
        }
    }

    /** 标签文字：当前页加「▶」前缀作为选中标记 */
    private static Component tabLabel(String translationKey, boolean selected) {
        Component label = Component.translatable(translationKey);
        return selected ? Component.literal("▶ ").append(label) : label;
    }

    /** 重建蓝图库列表数据 */
    private void refreshLibraryList() {
        if (libraryList == null) return;
        libraryList.setData(BlueprintLibraryClient.getValid(), BlueprintLibraryClient.getBroken(),
                minecraft != null ? minecraft.level : null);
    }

    /** 构造用于预览的蓝图物品：PACK 写路径，LOCAL 写内联数据 */
    private ItemStack buildPreviewStack(BlueprintLibraryListWidget.Row row) {
        if (row.payload() == null && row.packId() == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(MMItems.getVEHICLE_BLUEPRINT().get());
        if (row.type() == BlueprintLibraryListWidget.RowType.PACK) {
            stack.set(MMDataComponents.getVEHICLE_BLUEPRINT_PATH(), row.packId());
        } else if (row.payload() != null) {
            stack.set(MMDataComponents.getVEHICLE_DATA(), row.payload());
        }
        return stack;
    }

    /** 把背包中第一个可入库的蓝图交给服务端 */
    private void storeFirstStorableBlueprint() {
        Player player = minecraft != null ? minecraft.player : null;
        if (player == null) return;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof VehicleBlueprintItem
                    && stack.has(MMDataComponents.getVEHICLE_DATA())) {
                PacketDistributor.sendToServer(new BlueprintStoreRequestPayload(i));
                return;
            }
        }
        player.displayClientMessage(
                Component.translatable("message.machine_max.blueprint_library.no_storable"), true);
    }

    /** 重命名选中的本地蓝图 */
    private void renameSelectedBlueprint() {
        BlueprintLibraryListWidget.Row row = libraryList != null ? libraryList.getSelected() : null;
        if (row == null || row.fileName() == null) return;
        String name = renameBox.getValue().trim();
        if (name.isEmpty()) return;
        if (BlueprintLibraryClient.rename(row.fileName(), name)) {
            refreshLibraryList();
        }
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
        String filter = TextUtil.normalize(searchBox != null ? searchBox.getValue() : "");

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
        if (TextUtil.normalize(holder.id().toString()).contains(filter)) return true;

        RecipeHolder<BlueprintResearchRecipe> blueprintHolder = MMDynamicRes.BLUEPRINT_RESEARCH_RECIPES.get(holder.id());
        if (blueprintHolder == null) return false;

        ResourceLocation unlockRecipeId = blueprintHolder.value().getUnlockRecipe();
        if (TextUtil.normalize(unlockRecipeId.toString()).contains(filter)) return true;

        RecipeHolder<FabricatingRecipe> unlockedHolder = MMDynamicRes.ALL_FABRICATING_RECIPES.get(unlockRecipeId);
        if (unlockedHolder == null) return false;

        var partType = TextUtil.resolvePartTypeForTooltip(unlockedHolder.value().getResult());
        for (String text : TextUtil.collectSearchTexts(partType)) {
            if (text.contains(filter)) return true;
        }
        return false;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (activeTab == Tab.RESEARCH) {
            rebuildEntries();
            recipeList.setStates(this.states);
            restoreSelection();
        } else if (BlueprintLibraryClient.isScanning()) {
            // 主线程限流：每 tick 至多解析 1 个文件
            BlueprintLibraryClient.tick(minecraft != null ? minecraft.level : null);
            refreshLibraryList();
        }
    }

    @Override
    public void onClose() {
        BlueprintLibraryClient.stop();
        super.onClose();
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
            if (infoX < mouseX && mouseX < infoX + 18 && infoY < mouseY && mouseY < infoY + 18){
                graphics.renderTooltip(font, result, mouseX, mouseY);
            }

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
