package io.github.sweetzonzi.machine_max.common.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.registry.MMResources;
import lombok.Getter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Getter
public class BlueprintResearchRecipe extends ResearchRecipe {
    private final ResourceLocation unlockRecipe;

    public static final MapCodec<BlueprintResearchRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.fieldOf("research_cost").forGetter(BlueprintResearchRecipe::getResearchCost),
                    IngredientCountPair.CODEC.listOf().optionalFieldOf("research_ingredients", List.of()).forGetter(BlueprintResearchRecipe::getResearchIngredientPairs),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("prerequisites", List.of()).forGetter(BlueprintResearchRecipe::getPrerequisites),
                    ResourceLocation.CODEC.optionalFieldOf("icon", ResourceLocation.withDefaultNamespace("textures/missingno.png")).forGetter(BlueprintResearchRecipe::getIcon),
                    Codec.STRING.optionalFieldOf("description", "").forGetter(BlueprintResearchRecipe::getTooltip),
                    ResourceLocation.CODEC.fieldOf("unlock_recipe").forGetter(BlueprintResearchRecipe::getUnlockRecipe)
            ).apply(instance, BlueprintResearchRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintResearchRecipe> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull BlueprintResearchRecipe decode(@NotNull RegistryFriendlyByteBuf buffer) {
            int researchPointCost = buffer.readVarInt();

            int ingredientCount = buffer.readVarInt();
            List<IngredientCountPair> ingredients = new ArrayList<>(ingredientCount);
            for (int i = 0; i < ingredientCount; i++) {
                ingredients.add(new IngredientCountPair(
                        net.minecraft.world.item.crafting.Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),
                        buffer.readVarInt()
                ));
            }

            int prerequisiteCount = buffer.readVarInt();
            List<ResourceLocation> prerequisites = new ArrayList<>(prerequisiteCount);
            for (int i = 0; i < prerequisiteCount; i++) {
                prerequisites.add(ResourceLocation.STREAM_CODEC.decode(buffer));
            }

            ResourceLocation icon = ResourceLocation.STREAM_CODEC.decode(buffer);
            String tooltip = buffer.readUtf();
            ResourceLocation unlockRecipe = ResourceLocation.STREAM_CODEC.decode(buffer);
            return new BlueprintResearchRecipe(researchPointCost, ingredients, prerequisites, icon, tooltip, unlockRecipe);
        }

        @Override
        public void encode(@NotNull RegistryFriendlyByteBuf buffer, BlueprintResearchRecipe recipe) {
            buffer.writeVarInt(recipe.getResearchCost());

            List<IngredientCountPair> ingredients = recipe.getResearchIngredientPairs();
            buffer.writeVarInt(ingredients.size());
            for (IngredientCountPair pair : ingredients) {
                net.minecraft.world.item.crafting.Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, pair.ingredient());
                buffer.writeVarInt(pair.count());
            }

            buffer.writeVarInt(recipe.getPrerequisites().size());
            for (ResourceLocation prerequisite : recipe.getPrerequisites()) {
                ResourceLocation.STREAM_CODEC.encode(buffer, prerequisite);
            }

            ResourceLocation.STREAM_CODEC.encode(buffer, recipe.getIcon());
            buffer.writeUtf(recipe.getTooltip());
            ResourceLocation.STREAM_CODEC.encode(buffer, recipe.unlockRecipe);
        }
    };

    public BlueprintResearchRecipe(int researchCost,
                                   List<IngredientCountPair> researchIngredientPairs,
                                   List<ResourceLocation> prerequisites,
                                   ResourceLocation icon,
                                   String tooltip,
                                   ResourceLocation unlockRecipe) {
        super(researchCost, researchIngredientPairs, prerequisites, icon, tooltip);
        this.unlockRecipe = unlockRecipe;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return MMResources.getBLUEPRINT_RESEARCH_RECIPE_SERIALIZER().get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return MMResources.getBLUEPRINT_RESEARCH_RECIPE_TYPE().get();
    }

    public static class Serializer implements RecipeSerializer<BlueprintResearchRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public @NotNull MapCodec<BlueprintResearchRecipe> codec() {
            return CODEC;
        }

        @Override
        public @NotNull StreamCodec<RegistryFriendlyByteBuf, BlueprintResearchRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
