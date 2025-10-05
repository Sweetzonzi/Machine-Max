package io.github.sweetzonzi.machine_max.common.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.registry.MMResources;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
//TODO: 材料需求检查与消耗仍然可能存在一个物品对应多个需求的问题
@Getter
public class FabricatingRecipe implements Recipe<FabricatingInput> {
    private final List<IngredientCountPair> ingredientPairs;
    private final ItemStack result;
    private final int processingTime;

    // 内部类：包装 Ingredient 和数量
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
    }

    public static final MapCodec<FabricatingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    FabricatingRecipe.IngredientCountPair.CODEC.listOf().fieldOf("ingredients").forGetter(FabricatingRecipe::getIngredientPairs),
                    ItemStack.CODEC.fieldOf("result").forGetter(FabricatingRecipe::getResult),
                    Codec.INT.optionalFieldOf("time", 100).forGetter(FabricatingRecipe::getProcessingTime)
            ).apply(instance, FabricatingRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, FabricatingRecipe> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull FabricatingRecipe decode(@NotNull RegistryFriendlyByteBuf buffer) {
            // 读取原料列表
            int ingredientCount = buffer.readVarInt();
            List<FabricatingRecipe.IngredientCountPair> ingredients = new ArrayList<>(ingredientCount);
            for (int i = 0; i < ingredientCount; i++) {
                Ingredient ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
                int count = buffer.readVarInt();
                ingredients.add(new FabricatingRecipe.IngredientCountPair(ingredient, count));
            }
            // 读取输出结果
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            // 读取处理时间
            int processingTime = buffer.readVarInt();
            return new FabricatingRecipe(ingredients, result, processingTime);
        }

        @Override
        public void encode(@NotNull RegistryFriendlyByteBuf buffer, FabricatingRecipe recipe) {
            // 写入原料列表
            List<FabricatingRecipe.IngredientCountPair> ingredients = recipe.getIngredientPairs();
            buffer.writeVarInt(ingredients.size());
            for (FabricatingRecipe.IngredientCountPair pair : ingredients) {
                // 使用 Ingredient.CONTENTS_STREAM_CODEC 写入 Ingredient
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, pair.ingredient());
                buffer.writeVarInt(pair.count());
            }
            // 写入输出结果
            ItemStack.STREAM_CODEC.encode(buffer, recipe.getResult());
            // 写入处理时间
            buffer.writeVarInt(recipe.getProcessingTime());
        }
    };

    public FabricatingRecipe(List<IngredientCountPair> ingredientPairs, ItemStack result, int processingTime) {
        this.ingredientPairs = ingredientPairs;
        this.result = result;
        this.processingTime = processingTime;

        // 验证输出数量在合理范围内
        if (result.getCount() <= 0 || result.getCount() > 99) {
            throw new IllegalArgumentException("Output count must be between 1 and 99, got: " + result.getCount());
        }
    }

    /**
     * 检查指定物品容器是否包含配方所需的所有原料（考虑数量）
     */
    public boolean hasRequiredIngredients(Container container) {
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
    public boolean hasRequiredIngredients(List<ItemStack> itemStacks) {
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
    public void consumeIngredients(Container container) {
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
    public List<ItemStack> consumeIngredients(List<ItemStack> itemStacks) {
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

    // 保留原有的 Player 方法作为便捷方法
    public boolean hasRequiredIngredients(Player player) {
        return hasRequiredIngredients(player.getInventory());
    }

    public void consumeIngredients(Player player) {
        consumeIngredients(player.getInventory());
    }

    /**
     * 获取配方所需的所有可能物品显示（用于JEI/REI显示）
     */
    public List<ItemStack> getAllPossibleInputs() {
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

    @Override
    public boolean matches(FabricatingInput input, Level level) {
        // 将输入转换为物品列表进行检查
        List<ItemStack> inputStacks = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            inputStacks.add(input.getItem(i));
        }
        return hasRequiredIngredients(inputStacks);
    }

    @Override
    public ItemStack assemble(FabricatingInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true; // 我们的制造机不依赖网格尺寸
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return MMResources.getFABRICATE_RECIPE_TYPE().getSerializer().get();
    }

    @Override
    public RecipeType<?> getType() {
        return MMResources.getFABRICATE_RECIPE_TYPE().getType().get();
    }

    public static class Serializer implements RecipeSerializer<FabricatingRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public @NotNull MapCodec<FabricatingRecipe> codec() {
            return FabricatingRecipe.CODEC;
        }

        @Override
        public @NotNull StreamCodec<RegistryFriendlyByteBuf, FabricatingRecipe> streamCodec() {
            return FabricatingRecipe.STREAM_CODEC;
        }
    }
}