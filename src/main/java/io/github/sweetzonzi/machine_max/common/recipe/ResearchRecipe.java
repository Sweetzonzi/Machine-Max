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
import java.util.Set;

/**
 * 研发项目基类：持有研发点成本、研发材料、前置项目、归属组、图标与描述。
 *
 * <p>实现原版 {@link Recipe} 以接入配方数据加载与客户端同步，但研发项目本身不产出物品：
 * 制造蓝图由抄录动作按 {@code unlock_recipe} 现场产出，见 {@code BlueprintResearchRecipe}。</p>
 */
@Getter
public class ResearchRecipe implements Recipe<FabricatingInput> {
    private final int researchCost;
    private final List<IngredientCountPair> researchIngredientPairs;
    private final List<Ingredient> researchIngredientList = new ArrayList<>();
    private final List<ResourceLocation> prerequisites;
    private final List<ResourceLocation> groups;
    private final ResourceLocation icon;
    private final String tooltip;

    public static final MapCodec<ResearchRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.fieldOf("research_cost").forGetter(ResearchRecipe::getResearchCost),
                    IngredientCountPair.CODEC.listOf().optionalFieldOf("research_ingredients", List.of()).forGetter(ResearchRecipe::getResearchIngredientPairs),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("prerequisites", List.of()).forGetter(ResearchRecipe::getPrerequisites),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("groups", List.of()).forGetter(ResearchRecipe::getGroups),
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

            List<ResourceLocation> prerequisites = readIdList(buffer);
            List<ResourceLocation> groups = readIdList(buffer);

            ResourceLocation icon = ResourceLocation.STREAM_CODEC.decode(buffer);
            String tooltip = buffer.readUtf();
            return new ResearchRecipe(researchPointCost, ingredients, prerequisites, groups, icon, tooltip);
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

            writeIdList(buffer, recipe.prerequisites);
            writeIdList(buffer, recipe.groups);

            ResourceLocation.STREAM_CODEC.encode(buffer, recipe.icon);
            buffer.writeUtf(recipe.tooltip);
        }
    };

    /** 读写资源路径列表，供本类与子类的流编解码共用 */
    protected static List<ResourceLocation> readIdList(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<ResourceLocation> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(ResourceLocation.STREAM_CODEC.decode(buffer));
        }
        return ids;
    }

    /** 写资源路径列表，供本类与子类的流编解码共用 */
    protected static void writeIdList(RegistryFriendlyByteBuf buffer, List<ResourceLocation> ids) {
        buffer.writeVarInt(ids.size());
        for (ResourceLocation id : ids) {
            ResourceLocation.STREAM_CODEC.encode(buffer, id);
        }
    }

    public ResearchRecipe(int researchCost,
                          List<IngredientCountPair> researchIngredientPairs,
                          List<ResourceLocation> prerequisites,
                          List<ResourceLocation> groups,
                          ResourceLocation icon,
                          String tooltip) {
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
        this.prerequisites = prerequisites;
        this.groups = groups;
        this.icon = icon;
        this.tooltip = tooltip;
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

    /** 全部前置项目都已完成时返回 true；无前置时恒为 true */
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
