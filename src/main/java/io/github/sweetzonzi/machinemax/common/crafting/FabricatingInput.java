package io.github.sweetzonzi.machinemax.common.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.List;

public record FabricatingInput(List<ItemStack> inputs, float commonUnit, float rareUnit, float researchPoint) implements RecipeInput {

    @Override
    public ItemStack getItem(int index) {
        return inputs.get(index);
    }

    @Override
    public int size() {
        return inputs.size();
    }
}
