package io.github.sweetzonzi.machine_max.common.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.registry.MMResources;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
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
import java.util.List;

//TODO: 材料需求检查与消耗仍然可能存在一个物品对应多个需求的问题
@Getter
public class FabricatingRecipe implements Recipe<FabricatingInput> {
    public static final ResourceLocation EMPTY = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty");

    private final List<IngredientCountPair> ingredientPairs;
    private final List<Ingredient> ingredientList = new ArrayList<>(); // 列表形式的原料，方便分步推进合成
    private final ItemStack result;
    private final int processingTime;
    private final String tooltip;

    public static final MapCodec<FabricatingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    IngredientCountPair.CODEC.listOf().fieldOf("ingredients").forGetter(FabricatingRecipe::getIngredientPairs),
                    ItemStack.CODEC.fieldOf("result").forGetter(FabricatingRecipe::getResult),
                    Codec.INT.optionalFieldOf("time", 100).forGetter(FabricatingRecipe::getProcessingTime),
                    Codec.STRING.optionalFieldOf("description", "").forGetter(FabricatingRecipe::getTooltip)
            ).apply(instance, FabricatingRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, FabricatingRecipe> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull FabricatingRecipe decode(@NotNull RegistryFriendlyByteBuf buffer) {
            // 读取原料列表
            int ingredientCount = buffer.readVarInt();
            List<IngredientCountPair> ingredients = new ArrayList<>(ingredientCount);
            for (int i = 0; i < ingredientCount; i++) {
                Ingredient ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
                int count = buffer.readVarInt();
                ingredients.add(new IngredientCountPair(ingredient, count));
            }
            // 读取输出结果
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            // 读取处理时间
            int processingTime = buffer.readVarInt();
            String descriptionId = buffer.readUtf();
            return new FabricatingRecipe(ingredients, result, processingTime, descriptionId);
        }

        @Override
        public void encode(@NotNull RegistryFriendlyByteBuf buffer, FabricatingRecipe recipe) {
            // 写入原料列表
            List<IngredientCountPair> ingredients = recipe.getIngredientPairs();
            buffer.writeVarInt(ingredients.size());
            for (IngredientCountPair pair : ingredients) {
                // 使用 Ingredient.CONTENTS_STREAM_CODEC 写入 Ingredient
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, pair.ingredient());
                buffer.writeVarInt(pair.count());
            }
            // 写入输出结果
            ItemStack.STREAM_CODEC.encode(buffer, recipe.getResult());
            // 写入处理时间
            buffer.writeVarInt(recipe.getProcessingTime());
            // 写入描述id
            buffer.writeUtf(recipe.getTooltip());
        }
    };

    public FabricatingRecipe(List<IngredientCountPair> ingredientPairs, ItemStack result, int processingTime, String tooltip) {
        this.ingredientPairs = ingredientPairs;
        this.result = result;
        this.processingTime = processingTime;
        this.tooltip = tooltip;
        for (IngredientCountPair pair : ingredientPairs) {
            for (int i = 0; i < pair.count(); i++) {
                ingredientList.add(pair.ingredient());
            }
        }
        // 验证输出数量在合理范围内
        if (result.getCount() <= 0 || result.getCount() > 99) {
            throw new IllegalArgumentException("Output count must be between 1 and 99, got: " + result.getCount());
        }
    }

    /**
     * 检查指定物品容器是否包含配方所需的所有原料（考虑数量）
     */
    public boolean hasRequiredIngredients(Container container) {
        return IngredientCountPair.hasRequiredIngredients(container, ingredientPairs);
    }

    /**
     * 检查指定物品列表是否包含配方所需的所有原料（考虑数量）
     */
    public boolean hasRequiredIngredients(List<ItemStack> itemStacks) {
        return IngredientCountPair.hasRequiredIngredients(itemStacks, ingredientPairs);
    }

    /**
     * 从指定容器中消耗配方所需的原料
     */
    public void consumeIngredients(Container container) {
        IngredientCountPair.consumeIngredients(container, ingredientPairs);
    }

    /**
     * 从指定物品列表中消耗配方所需的原料（返回消耗后的新列表）
     */
    public List<ItemStack> consumeIngredients(List<ItemStack> itemStacks) {
        return IngredientCountPair.consumeIngredients(itemStacks, ingredientPairs);
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
        return IngredientCountPair.getAllPossibleInputs(ingredientPairs);
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
    public @NotNull RecipeSerializer<?> getSerializer() {
        return MMResources.getFABRICATION_RECIPE_SERIALIZER().get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return MMResources.getFABRICATION_RECIPE_TYPE().get();
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