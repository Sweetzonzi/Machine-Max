package io.github.sweetzonzi.machine_max.common.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.registry.MMResources;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Getter
public class ResearchRecipe extends AbstractResearchRecipe implements Recipe<FabricatingInput> {
    private final List<ResourceLocation> prerequisites;
    private final ResourceLocation icon;
    private final String tooltip;

    public static final MapCodec<ResearchRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.fieldOf("research_cost").forGetter(ResearchRecipe::getResearchCost),
                    IngredientCountPair.CODEC.listOf().optionalFieldOf("research_ingredients", List.of()).forGetter(ResearchRecipe::getResearchIngredientPairs),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("prerequisites", List.of()).forGetter(ResearchRecipe::getPrerequisites),
                    ResourceLocation.CODEC.optionalFieldOf("icon", ResourceLocation.withDefaultNamespace("textures/missingno.png")).forGetter(ResearchRecipe::getIcon),
                    Codec.STRING.optionalFieldOf("description", "").forGetter(ResearchRecipe::getTooltip)
            ).apply(instance, ResearchRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchRecipe> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull ResearchRecipe decode(@NotNull RegistryFriendlyByteBuf buffer) {
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
            return new ResearchRecipe(researchPointCost, ingredients, prerequisites, icon, tooltip);
        }

        @Override
        public void encode(@NotNull RegistryFriendlyByteBuf buffer, ResearchRecipe recipe) {
            buffer.writeVarInt(recipe.getResearchCost());

            List<IngredientCountPair> ingredients = recipe.getResearchIngredientPairs();
            buffer.writeVarInt(ingredients.size());
            for (IngredientCountPair pair : ingredients) {
                net.minecraft.world.item.crafting.Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, pair.ingredient());
                buffer.writeVarInt(pair.count());
            }

            buffer.writeVarInt(recipe.prerequisites.size());
            for (ResourceLocation prerequisite : recipe.prerequisites) {
                ResourceLocation.STREAM_CODEC.encode(buffer, prerequisite);
            }

            ResourceLocation.STREAM_CODEC.encode(buffer, recipe.icon);
            buffer.writeUtf(recipe.tooltip);
        }
    };

    public ResearchRecipe(int researchCost,
                          List<IngredientCountPair> researchIngredientPairs,
                          List<ResourceLocation> prerequisites,
                          ResourceLocation icon,
                          String tooltip) {
        super(researchCost, researchIngredientPairs);
        this.prerequisites = prerequisites;
        this.icon = icon;
        this.tooltip = tooltip;
    }

    public boolean isCompletedBy(Set<ResourceLocation> completedResearches) {
        for (ResourceLocation prerequisite : prerequisites) {
            if (!completedResearches.contains(prerequisite)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean matches(FabricatingInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(FabricatingInput input, HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return MMResources.getRESEARCH_RECIPE_SERIALIZER().get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return MMResources.getRESEARCH_RECIPE_TYPE().get();
    }

    public static class Serializer implements RecipeSerializer<ResearchRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public @NotNull MapCodec<ResearchRecipe> codec() {
            return CODEC;
        }

        @Override
        public @NotNull StreamCodec<RegistryFriendlyByteBuf, ResearchRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
