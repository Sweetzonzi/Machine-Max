package io.github.sweetzonzi.machinemax.common.crafting;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Getter
public class FabricatingRecipe implements Recipe<FabricatingInput> {
    private final List<Pair<Ingredient, Integer>> materials;
    private final ItemStack result;

    public static final Codec<Pair<Ingredient, Integer>> MATERIAL_CODEC = Codec.pair(
            Ingredient.CODEC.fieldOf("item").codec(),
            Codec.INT.fieldOf("count").codec()
    );

    public static final Codec<FabricatingRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MATERIAL_CODEC.listOf().fieldOf("materials").forGetter(FabricatingRecipe::getMaterials),
            ItemStack.CODEC.fieldOf("result").forGetter(FabricatingRecipe::getResult)
    ).apply(instance, FabricatingRecipe::new));

    public static final StreamCodec<FriendlyByteBuf, FabricatingRecipe> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull FabricatingRecipe decode(FriendlyByteBuf buffer) {
            return buffer.readJsonWithCodec(CODEC);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull FabricatingRecipe value) {
            buffer.writeJsonWithCodec(CODEC, value);
        }
    };

    public FabricatingRecipe(List<Pair<Ingredient, Integer>> materials, ItemStack result) {
        this.materials = materials;
        this.result = result;
    }

    /**
     * 检查输入的物品列表是否满足单一种材料的需求
     * @param inputs 输入的物品列表
     * @param requiredItem 需要的材料
     * @return 符合要求的材料数量
     */
    public int checkMaterials(List<ItemStack> inputs, Ingredient requiredItem) {
        int count = 0;
        for (ItemStack stack : inputs) {
            if (requiredItem.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    public boolean matches(FabricatingInput input, Level level) {
        boolean result = true;
        for (Pair<Ingredient, Integer> pair : materials) {
            //遍历需要的材料列表
            Ingredient requiredItem = pair.getFirst();
            int requiredCount = pair.getSecond();
            int count = checkMaterials(input.inputs(), requiredItem);
            if (count < requiredCount) {
                //任意材料需求不满足则返回false
                result = false;
                break;
            }
        }
        return result;
    }

    @Override
    public ItemStack assemble(FabricatingInput input, HolderLookup.Provider registries) {
        return getResultItem(registries);
    }

    /**
     * Used to determine if this recipe can fit in a grid of the given width/height
     *
     * @param width
     * @param height
     */
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public @NotNull ItemStack getResultItem(HolderLookup.@NotNull Provider registries) {
        return result.copy();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return null;
    }

    @Override
    public RecipeType<?> getType() {
        return null;
    }
}
