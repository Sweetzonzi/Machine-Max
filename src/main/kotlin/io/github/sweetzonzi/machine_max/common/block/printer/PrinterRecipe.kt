package cn.solarmoon.spark_core.printer

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.core.RegistryAccess
import net.minecraft.data.PackOutput
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.RecipeProvider
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeInput
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level
import java.util.concurrent.CompletableFuture

class PrinterRecipe(
    private val ingredients: List<Ingredient>, // 9个输入
    val printTime: Int,                       // 打印时间
    val result: ItemStack,                    // 输出
): Recipe<CraftingInput> {

    override fun getIngredients(): NonNullList<Ingredient> {
        return NonNullList.copyOf(ingredients)
    }

    override fun matches(
        input: CraftingInput,
        level: Level
    ): Boolean {
        ingredients.forEachIndexed { index, ingredient ->
            if (!ingredient.test(input.getItem(index))) {
                return false
            }
        }
        return true
    }

    override fun assemble(
        input: CraftingInput,
        registries: HolderLookup.Provider
    ): ItemStack {
        return result.copy()
    }

    override fun canCraftInDimensions(width: Int, height: Int): Boolean {
        return true
    }

    override fun getResultItem(registries: HolderLookup.Provider): ItemStack {
        return result
    }

    override fun getSerializer(): RecipeSerializer<*> {
        return PrinterRegister.PRINTER_RECIPE_TYPE.serializer.get()
    }

    override fun getType(): RecipeType<*> {
        return PrinterRegister.PRINTER_RECIPE_TYPE.type.get()
    }

    class Serializer: RecipeSerializer<PrinterRecipe> {
        companion object {
            val CODEC = RecordCodecBuilder.mapCodec<PrinterRecipe> {
                it.group(
                    Ingredient.LIST_CODEC_NONEMPTY.fieldOf("ingredients").forGetter { it.ingredients },
                    Codec.INT.fieldOf("printTime").forGetter { it.printTime },
                    ItemStack.CODEC.fieldOf("result").forGetter { it.result }
                ).apply(it, ::PrinterRecipe)
            }

            val STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.collection { NonNullList.create() }), PrinterRecipe::ingredients,
                ByteBufCodecs.INT, PrinterRecipe::printTime,
                ItemStack.STREAM_CODEC, PrinterRecipe::result,
                ::PrinterRecipe
            )
        }

        override fun codec(): MapCodec<PrinterRecipe> {
            return CODEC
        }

        override fun streamCodec(): StreamCodec<RegistryFriendlyByteBuf, PrinterRecipe> {
            return STREAM_CODEC
        }
    }

    class DataProvider(output: PackOutput, registries: CompletableFuture<HolderLookup.Provider>): RecipeProvider(output, registries) {
        override fun buildRecipes(recipeOutput: RecipeOutput) {
            recipeOutput.accept(PrinterRegister.PRINTER_RECIPE_TYPE.type.id, PrinterRecipe(
                listOf(Ingredient.of(Items.IRON_INGOT)),
                10,
                ItemStack(Items.TNT)
            ), null)
        }
    }

}