package io.github.sweetzonzi.machine_max.common.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record IngredientCountPair(Ingredient ingredient, int count) {
    public static final Codec<IngredientCountPair> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Ingredient.CODEC.fieldOf("ingredient").forGetter(IngredientCountPair::ingredient),
                    Codec.INT.fieldOf("count").forGetter(IngredientCountPair::count)
            ).apply(instance, IngredientCountPair::new)
    );
    @Override
    public String toString() {
        return count + "x " + ingredient;
    }

    /**
     * 检查指定物品容器是否包含配方所需的所有原料（考虑数量）
     */
    public static boolean hasRequiredIngredients(Container container, List<IngredientCountPair> ingredientPairs) {
        Map<Ingredient, Integer> requiredCounts = new HashMap<>();

        // 统计每个 Ingredient 需要的总数量
        for (IngredientCountPair pair : ingredientPairs) {
            requiredCounts.merge(pair.ingredient(), pair.count(), Integer::sum);
        }

        // 检查容器中的物品
        Map<Ingredient, Integer> availableCounts = new HashMap<>();

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                for (Ingredient ingredient : requiredCounts.keySet()) {
                    if (ingredient.test(stack)) {
                        availableCounts.merge(ingredient, stack.getCount(), Integer::sum);
                        break; // 一个物品只能匹配一个 Ingredient
                    }
                }
            }
        }

        // 验证每个原料的数量是否足够
        for (Map.Entry<Ingredient, Integer> entry : requiredCounts.entrySet()) {
            int required = entry.getValue();
            int available = availableCounts.getOrDefault(entry.getKey(), 0);
            if (available < required) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查指定物品列表是否包含配方所需的所有原料（考虑数量）
     */
    public static boolean hasRequiredIngredients(List<ItemStack> itemStacks, List<IngredientCountPair> ingredientPairs) {
        Map<Ingredient, Integer> requiredCounts = new HashMap<>();

        // 统计每个 Ingredient 需要的总数量
        for (IngredientCountPair pair : ingredientPairs) {
            requiredCounts.merge(pair.ingredient(), pair.count(), Integer::sum);
        }

        // 检查物品列表
        Map<Ingredient, Integer> availableCounts = new HashMap<>();

        for (ItemStack stack : itemStacks) {
            if (!stack.isEmpty()) {
                for (Ingredient ingredient : requiredCounts.keySet()) {
                    if (ingredient.test(stack)) {
                        availableCounts.merge(ingredient, stack.getCount(), Integer::sum);
                        break; // 一个物品只能匹配一个 Ingredient
                    }
                }
            }
        }

        // 验证每个原料的数量是否足够
        for (Map.Entry<Ingredient, Integer> entry : requiredCounts.entrySet()) {
            int required = entry.getValue();
            int available = availableCounts.getOrDefault(entry.getKey(), 0);
            if (available < required) {
                return false;
            }
        }

        return true;
    }

    /**
     * 从指定容器中消耗配方所需的原料
     */
    public static void consumeIngredients(Container container, List<IngredientCountPair> ingredientPairs) {
        Map<Ingredient, Integer> toConsume = new HashMap<>();

        // 统计需要消耗的数量
        for (IngredientCountPair pair : ingredientPairs) {
            toConsume.merge(pair.ingredient(), pair.count(), Integer::sum);
        }

        // 遍历容器并消耗物品
        for (Map.Entry<Ingredient, Integer> entry : toConsume.entrySet()) {
            Ingredient ingredient = entry.getKey();
            int remaining = entry.getValue();

            for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    int consumeAmount = Math.min(remaining, stack.getCount());
                    stack.shrink(consumeAmount);
                    remaining -= consumeAmount;

                    if (stack.isEmpty()) {
                        container.setItem(i, ItemStack.EMPTY);
                    }
                }
            }
        }
    }

    /**
     * 从指定物品列表中消耗配方所需的原料（返回消耗后的新列表）
     */
    public static List<ItemStack> consumeIngredients(List<ItemStack> itemStacks, List<IngredientCountPair> ingredientPairs) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : itemStacks) {
            if (!stack.isEmpty()) {
                result.add(stack.copy());
            }
        }

        Map<Ingredient, Integer> toConsume = new HashMap<>();

        // 统计需要消耗的数量
        for (IngredientCountPair pair : ingredientPairs) {
            toConsume.merge(pair.ingredient(), pair.count(), Integer::sum);
        }

        // 消耗物品
        for (Map.Entry<Ingredient, Integer> entry : toConsume.entrySet()) {
            Ingredient ingredient = entry.getKey();
            int remaining = entry.getValue();

            for (int i = 0; i < result.size() && remaining > 0; i++) {
                ItemStack stack = result.get(i);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    int consumeAmount = Math.min(remaining, stack.getCount());
                    stack.shrink(consumeAmount);
                    remaining -= consumeAmount;

                    if (stack.isEmpty()) {
                        result.set(i, ItemStack.EMPTY);
                    }
                }
            }
        }

        return result;
    }

    /**
     * 获取配方所需的所有可能物品显示（用于JEI/REI显示）
     */
    public static List<ItemStack> getAllPossibleInputs(List<IngredientCountPair> ingredientPairs) {
        List<ItemStack> allInputs = new ArrayList<>();
        for (IngredientCountPair pair : ingredientPairs) {
            // 对于每个 Ingredient，获取所有可能的匹配物品
            ItemStack[] matchingStacks = pair.ingredient().getItems();
            for (ItemStack stack : matchingStacks) {
                // 创建一个带有正确数量的副本用于显示
                ItemStack displayStack = stack.copy();
                displayStack.setCount(pair.count());
                allInputs.add(displayStack);
            }
        }
        return allInputs;
    }
}
