package io.github.sweetzonzi.machine_max.client.compat.jei.category;

import io.github.sweetzonzi.machine_max.client.compat.jei.MMJeiRecipeTypes;
import io.github.sweetzonzi.machine_max.client.render.gui.screen.FabricatingScreen;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.IngredientCountPair;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FabricatingRecipeCategory implements IRecipeCategory<RecipeHolder<FabricatingRecipe>> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 84;
    private static final int INPUT_X = 6;
    private static final int INPUT_Y = 6;
    private static final int INPUT_COLUMNS = 4;
    private static final int SLOT_GAP = 18;
    private static final int OUTPUT_X = 148;
    private static final int OUTPUT_Y = 24;
    private static final int TIME_X = 80;
    private static final int TIME_Y = 58;

    private final IDrawable background;
    private final IDrawable icon;

    public FabricatingRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        // Fabricator block currently has no guaranteed BlockItem, use a stable item icon.
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(MMItems.getFABRICATING_BLUEPRINT().get()));
    }

    @Override
    public @NotNull RecipeType<RecipeHolder<FabricatingRecipe>> getRecipeType() {
        return MMJeiRecipeTypes.FABRICATING;
    }

    @Override
    public @NotNull Component getTitle() {
        return Component.translatable("block.machine_max.fabricator");
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
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<FabricatingRecipe> recipeHolder, IFocusGroup focuses) {
        FabricatingRecipe recipe = recipeHolder.value();

        List<IngredientCountPair> ingredientPairs = recipe.getIngredientPairs();
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

        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .setOutputSlotBackground()
                .addItemStack(recipe.getResult().copy());
    }

    @Override
    public void draw(RecipeHolder<FabricatingRecipe> recipeHolder, mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        String time = FabricatingScreen.formatTime(recipeHolder.value().getProcessingTime());
        Component timeText = Component.translatable("gui.machine_max.fabricator.actual_time", time);
        graphics.drawString(Minecraft.getInstance().font, timeText, TIME_X, TIME_Y, 0x909090, false);
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
