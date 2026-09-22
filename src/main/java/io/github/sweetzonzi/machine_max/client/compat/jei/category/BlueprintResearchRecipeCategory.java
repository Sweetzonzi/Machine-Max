package io.github.sweetzonzi.machine_max.client.compat.jei.category;

import io.github.sweetzonzi.machine_max.client.compat.jei.MMJeiRecipeTypes;
import io.github.sweetzonzi.machine_max.common.recipe.BlueprintResearchRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.IngredientCountPair;
import io.github.sweetzonzi.machine_max.common.recipe.PartFabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.registry.MMItems;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BlueprintResearchRecipeCategory implements IRecipeCategory<RecipeHolder<BlueprintResearchRecipe>> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 84;
    private static final int INPUT_X = 6;
    private static final int INPUT_Y = 6;
    private static final int INPUT_COLUMNS = 4;
    private static final int SLOT_GAP = 18;
    private static final int OUTPUT_BLUEPRINT_X = 120;
    private static final int OUTPUT_BLUEPRINT_Y = 24;
    private static final int OUTPUT_UNLOCK_X = 148;
    private static final int OUTPUT_UNLOCK_Y = 24;
    private static final int RP_TEXT_X = 6;
    private static final int RP_TEXT_Y = 58;

    private final IDrawable icon;

    public BlueprintResearchRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(MMItems.getRESEARCH_TABLE_BLOCK_ITEM().get()));
    }

    @Override
    public @NotNull RecipeType<RecipeHolder<BlueprintResearchRecipe>> getRecipeType() {
        return MMJeiRecipeTypes.BLUEPRINT_RESEARCH;
    }

    @Override
    public @NotNull Component getTitle() {
        return Component.translatable("block.machine_max.research_table");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public @NotNull IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<BlueprintResearchRecipe> recipeHolder, IFocusGroup focuses) {
        BlueprintResearchRecipe recipe = recipeHolder.value();

        List<IngredientCountPair> ingredientPairs = recipe.getResearchIngredientPairs();
        int shownInputs = 0;
        for (IngredientCountPair pair : ingredientPairs) {
            if (pair.count() <= 0) {
                continue;
            }
            int x = INPUT_X + (shownInputs % INPUT_COLUMNS) * SLOT_GAP;
            int y = INPUT_Y + (shownInputs / INPUT_COLUMNS) * SLOT_GAP;
            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                    .setStandardSlotBackground()
                    .addItemStacks(toDisplayStacks(pair));
            shownInputs++;
        }

        RecipeHolder<FabricatingRecipe> unlocked = resolveUnlockedRecipe(recipe.getUnlockRecipe());
        ItemStack blueprint = buildResearchProduct(recipe.getUnlockRecipe(), unlocked);
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_BLUEPRINT_X, OUTPUT_BLUEPRINT_Y)
                .setOutputSlotBackground()
                .addItemStack(blueprint);

        if (unlocked != null) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_UNLOCK_X, OUTPUT_UNLOCK_Y)
                    .setOutputSlotBackground()
                    .addItemStack(unlocked.value().getResult().copy());
        }
    }

    @Override
    public void draw(RecipeHolder<BlueprintResearchRecipe> recipeHolder, mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        Component rpText = Component.translatable(
                "jei.machine_max.blueprint_research.rp_cost",
                recipeHolder.value().getResearchCost()
        );
        graphics.drawString(Minecraft.getInstance().font, rpText, RP_TEXT_X, RP_TEXT_Y, 0xFFFFFF, true);
    }

    /** 按配方 id 取本侧全配方索引中的制造配方；索引未就绪或该配方被装载期校验排除时返回 {@code null} */
    @Nullable
    private static RecipeHolder<FabricatingRecipe> resolveUnlockedRecipe(ResourceLocation unlockRecipe) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        return MMDynamicRes.getAllFabricating(level).get(unlockRecipe);
    }

    /**
     * 按解锁配方的类型选择奖励蓝图物品：零件配方产出零件制造蓝图（同时写入 {@code part_type}），
     * 通用制造配方产出通用制造蓝图。
     */
    private static ItemStack buildResearchProduct(ResourceLocation unlockRecipe,
                                                  @Nullable RecipeHolder<FabricatingRecipe> unlocked) {
        if (unlocked != null && unlocked.value() instanceof PartFabricatingRecipe partRecipe) {
            ItemStack blueprint = new ItemStack(MMItems.getPART_FABRICATING_BLUEPRINT().get());
            blueprint.set(MMDataComponents.getRECIPE_TYPE(), unlocked.id());
            if (partRecipe.getPartType() != null) {
                blueprint.set(MMDataComponents.getPART_TYPE(), partRecipe.getPartType());
            }
            return blueprint;
        }
        ItemStack blueprint = new ItemStack(MMItems.getFABRICATING_BLUEPRINT().get());
        blueprint.set(MMDataComponents.getRECIPE_TYPE(), unlockRecipe);
        return blueprint;
    }

    private static List<ItemStack> toDisplayStacks(IngredientCountPair pair) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack itemStack : pair.ingredient().getItems()) {
            ItemStack display = itemStack.copy();
            display.setCount(Math.max(1, pair.count()));
            stacks.add(display);
        }
        return stacks;
    }
}
