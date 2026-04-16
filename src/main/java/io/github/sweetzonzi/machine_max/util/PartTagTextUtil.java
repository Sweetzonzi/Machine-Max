package io.github.sweetzonzi.machine_max.util;

import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PartTagTextUtil {

    private PartTagTextUtil() {
    }

    public static List<ResourceLocation> getDistinctSortedTags(@Nullable PartType partType) {
        if (partType == null) return List.of();
        return partType.getVariants().values().stream()
                .flatMap(variant -> variant.getTags().stream())
                .distinct()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    public static String getTranslatedOrRawTag(ResourceLocation tag) {
        String raw = tag.toLanguageKey();
        String translated = Component.translatable(raw).getString();
        return translated.equals(raw) ? raw : translated;
    }

    public static Set<String> collectSearchTexts(@Nullable PartType partType) {
        Set<String> result = new LinkedHashSet<>();
        for (ResourceLocation tag : getDistinctSortedTags(partType)) {
            String raw = tag.toLanguageKey();
            String translated = getTranslatedOrRawTag(tag);
            result.add(normalize(raw));
            result.add(normalize(translated));
        }
        return result;
    }

    public static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static PartType resolvePartTypeForTooltip(ItemStack stack) {
        ResourceLocation partTypeId = stack.get(MMDataComponents.getPART_TYPE());
        if (partTypeId != null) {
            return MMDynamicRes.PART_TYPES.get(partTypeId);
        }

        ResourceLocation recipeId = stack.get(MMDataComponents.getRECIPE_TYPE());
        if (recipeId == null) return null;
        RecipeHolder<FabricatingRecipe> recipeHolder = MMDynamicRes.ALL_FABRICATING_RECIPES.get(recipeId);
        if (recipeHolder == null) return null;

        ItemStack result = recipeHolder.value().getResult();
        ResourceLocation resultPartTypeId = result.get(MMDataComponents.getPART_TYPE());
        if (resultPartTypeId == null) return null;
        return MMDynamicRes.PART_TYPES.get(resultPartTypeId);
    }
}
