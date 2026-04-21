package io.github.sweetzonzi.machine_max.common.recipe;

import lombok.Getter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

@Getter
public abstract class AbstractResearchRecipe {
    private final int researchCost;
    private final List<IngredientCountPair> researchIngredientPairs;
    private final List<Ingredient> researchIngredientList = new ArrayList<>();

    protected AbstractResearchRecipe(int researchCost, List<IngredientCountPair> researchIngredientPairs) {
        if (researchCost < 0) {
            throw new IllegalArgumentException("Research point cost must be non-negative, got: " + researchCost);
        }
        this.researchCost = researchCost;
        this.researchIngredientPairs = researchIngredientPairs;
        for (IngredientCountPair pair : researchIngredientPairs) {
            for (int i = 0; i < pair.count(); i++) {
                researchIngredientList.add(pair.ingredient());
            }
        }
    }

    public boolean hasRequiredIngredients(Container container) {
        return IngredientCountPair.hasRequiredIngredients(container, researchIngredientPairs);
    }

    public boolean hasRequiredIngredients(List<ItemStack> itemStacks) {
        return IngredientCountPair.hasRequiredIngredients(itemStacks, researchIngredientPairs);
    }

    public boolean hasRequiredIngredients(Player player) {
        return hasRequiredIngredients(player.getInventory());
    }

    public void consumeIngredients(Container container) {
        IngredientCountPair.consumeIngredients(container, researchIngredientPairs);
    }

    public List<ItemStack> consumeIngredients(List<ItemStack> itemStacks) {
        return IngredientCountPair.consumeIngredients(itemStacks, researchIngredientPairs);
    }

    public void consumeIngredients(Player player) {
        consumeIngredients(player.getInventory());
    }

    public List<ItemStack> getAllPossibleResearchInputs() {
        return IngredientCountPair.getAllPossibleInputs(researchIngredientPairs);
    }
}
