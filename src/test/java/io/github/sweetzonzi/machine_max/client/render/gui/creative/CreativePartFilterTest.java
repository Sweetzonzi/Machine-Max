package io.github.sweetzonzi.machine_max.client.render.gui.creative;

import io.github.sweetzonzi.machine_max.client.render.gui.creative.CreativePartBrowser.Category;
import io.github.sweetzonzi.machine_max.common.registry.MMTags;
import io.github.sweetzonzi.machine_max.util.TextUtil;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 创造栏零部件筛选的三条件判定：类别、系列与查询词。
 *
 * <p>只测试不依赖物品注册表的方法：类别词表与标签集合的判定、系列标签的识别与去重排序、
 * 查询词分词与跨字段匹配、标签语言键的转换与缺译回退。物品堆身份、蓝图候选与组件保持需要活注册表，
 * 不属于本类的自动化范围。</p>
 */
class CreativePartFilterTest {

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static ResourceLocation mm(String path) {
        return id("machine_max", path);
    }

    // ==================== T01 类别 ====================

    @Test
    void knownCategoryMatchesTheSharedVocabulary() {
        List<ResourceLocation> tags = List.of(MMTags.PART_CATEGORY_STRUCTURAL, mm("type/land"));
        assertTrue(CreativePartBrowser.matchesCategory(tags, Category.STRUCTURAL));
        assertFalse(CreativePartBrowser.matchesCategory(tags, Category.MOBILITY));
        assertTrue(CreativePartBrowser.matchesCategory(tags, Category.ALL));
        assertFalse(CreativePartBrowser.matchesCategory(tags, Category.UNCLASSIFIED));
        assertEquals(MMTags.PART_CATEGORY_STRUCTURAL, CreativePartBrowser.knownCategory(tags));
    }

    @Test
    void unknownCategoryTagFallsIntoUnclassified() {
        List<ResourceLocation> tags = List.of(id("other_pack", "category/weapon"));
        assertNull(CreativePartBrowser.knownCategory(tags));
        for (Category category : CreativePartBrowser.CATEGORY_VOCABULARY) {
            assertFalse(CreativePartBrowser.matchesCategory(tags, category), category.name());
        }
        assertTrue(CreativePartBrowser.matchesCategory(tags, Category.UNCLASSIFIED));
        assertTrue(CreativePartBrowser.matchesCategory(tags, Category.ALL));
    }

    @Test
    void emptyTagSetIsUnclassified() {
        assertTrue(CreativePartBrowser.matchesCategory(List.of(), Category.UNCLASSIFIED));
        assertTrue(CreativePartBrowser.matchesCategory(List.of(), Category.ALL));
        assertFalse(CreativePartBrowser.matchesCategory(List.of(), Category.STRUCTURAL));
    }

    @Test
    void aModelMayMatchSeveralCategories() {
        List<ResourceLocation> tags = List.of(MMTags.PART_CATEGORY_WEAPON, MMTags.PART_CATEGORY_STRUCTURAL);
        assertTrue(CreativePartBrowser.matchesCategory(tags, Category.STRUCTURAL));
        assertTrue(CreativePartBrowser.matchesCategory(tags, Category.WEAPON));
        assertFalse(CreativePartBrowser.matchesCategory(tags, Category.UNCLASSIFIED));
        // 侧栏顺序决定 knownCategory 的返回值
        assertEquals(MMTags.PART_CATEGORY_STRUCTURAL, CreativePartBrowser.knownCategory(tags));
    }

    // ==================== T02 系列 ====================

    @Test
    void seriesTagNeedsThePrefixAndANonEmptySuffix() {
        assertTrue(CreativePartBrowser.isSeriesTag(mm("series/k17")));
        assertTrue(CreativePartBrowser.isSeriesTag(id("my_pack", "series/example")));
        assertFalse(CreativePartBrowser.isSeriesTag(mm("series/")));
        assertFalse(CreativePartBrowser.isSeriesTag(mm("category/series")));
        assertFalse(CreativePartBrowser.isSeriesTag(mm("ae86")));
    }

    @Test
    void seriesKeepTheFullIdIncludingNamespace() {
        ResourceLocation first = id("pack_a", "series/example");
        ResourceLocation second = id("pack_b", "series/example");
        assertTrue(CreativePartBrowser.isSeriesTag(first));
        assertTrue(CreativePartBrowser.isSeriesTag(second));
        assertFalse(first.equals(second));
        assertEquals(List.of(first, second).size(),
                List.of(first, second).stream().distinct().count());
    }

    @Test
    void seriesTagsAreTheUnionOfVariantsDedupedAndSorted() {
        List<ResourceLocation> variantTags = List.of(
                mm("type/land"),
                mm("series/wine_fox"),
                mm("series/ae86"),
                mm("series/wine_fox"),
                id("pack_b", "series/ae86"));
        assertEquals(List.of(mm("series/ae86"), mm("series/wine_fox"), id("pack_b", "series/ae86")),
                CreativePartBrowser.seriesTags(variantTags));
        assertEquals(List.of(), CreativePartBrowser.seriesTags(List.of(mm("type/land"))));
    }

    // ==================== T03 查询词与交集 ====================

    @Test
    void tokenizeTrimsLowercasesAndSplitsOnWhitespace() {
        assertEquals(List.of(), CreativePartBrowser.tokenize("   "));
        assertEquals(List.of(), CreativePartBrowser.tokenize(null));
        assertEquals(List.of("wheel", "ae86"), CreativePartBrowser.tokenize("  Wheel   AE86 "));
    }

    @Test
    void everyWordMustHitAtLeastOneFieldButNotTheSameOne() {
        List<String> fields = List.of("wheel", "ae86 轮子", "machine_max:ae86_wheel");
        assertTrue(CreativePartBrowser.matchesAllWords(fields, List.of("wheel")));
        assertTrue(CreativePartBrowser.matchesAllWords(fields, List.of("wheel", "轮子")));
        assertFalse(CreativePartBrowser.matchesAllWords(fields, List.of("wheel", "jeep")));
        // 字段之间不拼接，避免跨字段边界产生命中
        assertFalse(CreativePartBrowser.matchesAllWords(List.of("ae8", "6wheel"), List.of("ae866wheel")));
    }

    @Test
    void hashPrefixSwitchesToTagOnlyFields() {
        List<String> nameAndId = List.of("part item", "machine_max:ae86_wheel");
        List<String> partTagTexts = List.of("machine_max:series/ae86", "ae86");
        List<String> itemTagIds = List.of("c:tools");

        List<String> normal = CreativePartBrowser.searchFields(nameAndId, partTagTexts, itemTagIds, false);
        assertTrue(normal.contains("part item"));
        assertTrue(normal.contains("ae86"));
        assertFalse(normal.contains("c:tools"));

        List<String> tagOnly = CreativePartBrowser.searchFields(nameAndId, partTagTexts, itemTagIds, true);
        assertFalse(tagOnly.contains("part item"));
        assertTrue(tagOnly.contains("c:tools"));
        assertTrue(tagOnly.contains("machine_max:series/ae86"));
        assertTrue(CreativePartBrowser.isTagQuery("#ae86"));
        assertFalse(CreativePartBrowser.isTagQuery("ae86"));
    }

    @Test
    void conditionsIntersectWithEmptyOnesPassingThrough() {
        List<ResourceLocation> tags = List.of(mm("category/mobility"), mm("series/ae86"));
        List<String> nameAndId = List.of("ae86 车轮", "machine_max:ae86_wheel");

        assertTrue(meetsAll(tags, nameAndId, mm("series/ae86"), Category.MOBILITY, List.of("wheel")));
        assertFalse(meetsAll(tags, nameAndId, mm("series/jeep"), Category.MOBILITY, List.of("wheel")));
        assertFalse(meetsAll(tags, nameAndId, mm("series/ae86"), Category.WEAPON, List.of("wheel")));
        assertFalse(meetsAll(tags, nameAndId, mm("series/ae86"), Category.MOBILITY, List.of("jeep")));
        assertTrue(meetsAll(tags, nameAndId, null, Category.ALL, List.of()));
        assertEquals(1, CreativePartBrowser.tokenize("wheel").size());
    }

    /** 与生产过滤同构的三条件交集：系列成员、类别判定、每词命中至少一个字段。 */
    private static boolean meetsAll(List<ResourceLocation> tags, List<String> nameAndId, ResourceLocation series,
                                    Category category, List<String> words) {
        if (series != null && !tags.contains(series)) return false;
        if (!CreativePartBrowser.matchesCategory(tags, category)) return false;
        List<String> fields = CreativePartBrowser.searchFields(
                nameAndId, tags.stream().map(ResourceLocation::toString).toList(), List.of(), false);
        return CreativePartBrowser.matchesAllWords(fields, words);
    }

    // ==================== T04 语言键与缺译回退 ====================

    @Test
    void tagLanguageKeyReplacesSlashesWithDots() {
        assertEquals("machine_max.series.k17", TextUtil.getTagLanguageKey(id("machine_max", "series/k17")));
        assertEquals("machine_max.category.structural",
                TextUtil.getTagLanguageKey(MMTags.PART_CATEGORY_STRUCTURAL));
        assertEquals("my_pack.series.example",
                TextUtil.getTagLanguageKey(id("my_pack", "series/example")));
    }

    @Test
    void untranslatedTagFallsBackToTheFullRawId() {
        ResourceLocation tag = id("machine_max", "series/not_translated_anywhere");
        assertEquals(tag.toString(), TextUtil.getTranslatedOrRawTag(tag));
        assertTrue(TextUtil.getTranslatedOrRawTag(tag).contains(":"));
    }

    @Test
    void translatedFieldsAreSearchableAlongsideRawIds() {
        List<String> fields = List.of(
                TextUtil.normalize("结构"),
                TextUtil.normalize(MMTags.PART_CATEGORY_STRUCTURAL.toString()));
        assertTrue(CreativePartBrowser.matchesAllWords(fields, CreativePartBrowser.tokenize("结构")));
        assertTrue(CreativePartBrowser.matchesAllWords(fields,
                CreativePartBrowser.tokenize(MMTags.PART_CATEGORY_STRUCTURAL.toString())));
        assertFalse(CreativePartBrowser.matchesAllWords(fields, CreativePartBrowser.tokenize("装饰")));
    }
}
