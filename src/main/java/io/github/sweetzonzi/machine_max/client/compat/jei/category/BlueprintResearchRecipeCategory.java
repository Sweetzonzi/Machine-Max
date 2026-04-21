package io.github.sweetzonzi.machine_max.client.compat.jei.category;

import io.github.sweetzonzi.machine_max.client.compat.jei.MMJeiRecipeTypes;
import io.github.sweetzonzi.machine_max.common.recipe.BlueprintResearchRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.IngredientCountPair;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.registry.MMItems;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    private final IDrawable background;
    private final IDrawable icon;

    public BlueprintResearchRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
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
    public @NotNull IDrawable getBackground() {
        return background;
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

        ItemStack blueprint = buildFabricatingBlueprint(recipe.getUnlockRecipe());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_BLUEPRINT_X, OUTPUT_BLUEPRINT_Y)
                .setOutputSlotBackground()
                .addItemStack(blueprint);

        Optional<ItemStack> unlockedResult = resolveUnlockedResult(recipe.getUnlockRecipe());
        if (unlockedResult.isPresent()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_UNLOCK_X, OUTPUT_UNLOCK_Y)
                    .setOutputSlotBackground()
                    .addItemStack(unlockedResult.get());
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

    private static ItemStack buildFabricatingBlueprint(ResourceLocation unlockRecipe) {
        ItemStack blueprint = new ItemStack(MMItems.getFABRICATING_BLUEPRINT().get());
        blueprint.set(MMDataComponents.getRECIPE_TYPE(), unlockRecipe);
        return blueprint;
    }

    private static Optional<ItemStack> resolveUnlockedResult(ResourceLocation unlockRecipe) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return Optional.empty();
        }
        return level.getRecipeManager()
                .byKey(unlockRecipe)
                .filter(holder -> holder.value() instanceof FabricatingRecipe)
                .map(holder -> ((FabricatingRecipe) holder.value()).getResult().copy());
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
