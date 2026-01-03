package io.github.sweetzonzi.machine_max.common.item.prop;

import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.NoSuchElementException;

public interface PartAssemblyItem {
    @Nullable
    static PartType getPartType(ItemStack stack, Level level) {
        PartType partType = null;
        if (stack.has(MMDataComponents.getPART_TYPE())) {
            partType = PartType.get(level, stack.get(MMDataComponents.getPART_TYPE()));
        }
        if (partType == null && stack.has(MMDataComponents.getRECIPE_TYPE())) {
            RecipeHolder<?> recipe = getRecipeHolder(stack, level);
            if (recipe != null) {
                ItemStack resultItem = recipe.value().getResultItem(level.registryAccess());
                partType = getPartType(resultItem, level);
            }
        }
        return partType;
    }

    @Nullable
    static RecipeHolder<?> getRecipeHolder(ItemStack stack, Level level) {
        ResourceLocation type = null;
        if (stack.has(MMDataComponents.getRECIPE_TYPE())) {
            type = stack.get(MMDataComponents.getRECIPE_TYPE());
        }
        if (type != null) {
            try {
                return level.getRecipeManager().byKey(type).orElseThrow();
            } catch (NoSuchElementException e) {
                return null;
            }
        } else return null;
    }
}
