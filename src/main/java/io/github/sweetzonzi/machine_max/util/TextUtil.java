package io.github.sweetzonzi.machine_max.util;

import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
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

public final class TextUtil {

    private TextUtil() {
    }

    public static String getTranslation(String key) {
        return Component.translatable(key).getString();
    }

    public static String getTranslation(ResourceLocation key) {
        return Component.translatable(key.toLanguageKey()).getString();
    }

    /**
     * 标签的语言键：命名空间 + 点 + 路径，路径中的斜杠转成点。
     *
     * <p>内容包的语言文件按 {@code machine_max.series.k17}、{@code machine_max.category.structural}
     * 的形式登记，{@link ResourceLocation#toLanguageKey()} 保留路径里的斜杠，不能直接当语言键使用。</p>
     *
     * @param tag 标签完整 ID
     * @return 语言键；标签没有翻译时它同时就是查询失败的键
     */
    public static String getTagLanguageKey(ResourceLocation tag) {
        return tag.getNamespace() + "." + tag.getPath().replace('/', '.');
    }

    public static List<ResourceLocation> getDistinctSortedTags(@Nullable RegularPartType partType) {
        if (partType == null) return List.of();
        return partType.getVariants().values().stream()
                .flatMap(variant -> variant.getTags().stream())
                .distinct()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    /**
     * 取标签的译名，标签没有译名时返回完整原始标签 ID。
     *
     * <p>语言键按 {@link #getTagLanguageKey(ResourceLocation)} 生成；该键在语言表里查询不到时，
     * 组件渲染出的就是键本身，此时改用 {@link ResourceLocation#toString()}，界面因此始终能读到
     * 带命名空间的完整 ID，而不是一个无法对照内容包的键。</p>
     *
     * @param tag 标签完整 ID
     * @return 译名或完整原始标签 ID
     */
    public static String getTranslatedOrRawTag(ResourceLocation tag) {
        String key = getTagLanguageKey(tag);
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? tag.toString() : translated;
    }

    /**
     * 收集一个型号全部变体标签的搜索文本：完整标签 ID 与标签译名各一条。
     *
     * <p>返回的文本已按 {@link #normalize(String)} 归一化，可直接参与子串匹配；
     * 标签按完整 ID 去重排序，输出顺序稳定。</p>
     *
     * @param partType 型号定义；{@code null} 时返回空集合
     * @return 去重后的搜索文本集合
     */
    public static Set<String> collectSearchTexts(@Nullable RegularPartType partType) {
        Set<String> result = new LinkedHashSet<>();
        for (ResourceLocation tag : getDistinctSortedTags(partType)) {
            result.add(normalize(tag.toString()));
            result.add(normalize(getTranslatedOrRawTag(tag)));
        }
        return result;
    }

    public static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static RegularPartType resolvePartTypeForTooltip(ItemStack stack) {
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
