package io.github.sweetzonzi.machine_max.client.render.gui.creative;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.registry.MMCreativeTabsKt;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.registry.MMTags;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import io.github.sweetzonzi.machine_max.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 两张零部件创造页共用的筛选条件、候选缓存与侧栏控件。
 *
 * <p>条件只有三个：单选系列、单选类别、主搜索字符串，三者取交集；任一条件为空表示它不参与筛选。
 * 条件在一次世界连接期间由静态字段记忆，{@link #clearSession()} 在退出世界时清空。静态字段只保存
 * 系列 ID、类别项与搜索文本，不持有屏幕、世界或玩家引用。</p>
 *
 * <p>本类不直接写屏幕私有字段：过滤结果写入菜单的 {@code items}，原版标签提示集合由构造时传入的
 * {@link Set} 就地清空与填充，滚动位置的写回留在 Mixin。刷新统一经构造时传入的 {@link Runnable}
 * 回到 {@code CreativeModeInventoryScreen#refreshSearchResults}，因此控件点击、主搜索输入、弹层选择
 * 与重置只有一个入口。</p>
 *
 * <p>线程：主线程（20tps）。</p>
 */
public final class CreativePartBrowser {

    /** 主搜索与弹层搜索共同的输入上限。 */
    public static final int SEARCH_MAX_LENGTH = 256;

    private static final ResourceLocation PART_TAB_ID =
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, MMCreativeTabsKt.PART_TAB_NAME);
    private static final ResourceLocation FABRICATING_BLUEPRINT_TAB_ID =
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, MMCreativeTabsKt.FABRICATING_BLUEPRINT_TAB_NAME);

    // ==================== 类别词表 ====================

    /**
     * 侧栏类别过滤项，声明顺序即侧栏顺序：全部、五个共用类别、未分类。
     *
     * <p>{@link #ALL} 与 {@link #UNCLASSIFIED} 没有对应标签：前者不参与类别判定，后者表示没有命中任何
     * 共用类别（包括只带未知 category 标签的情况）。枚举不持有物品图标，因此不依赖物品注册表。</p>
     */
    public enum Category {
        ALL(null, MMLanguageRegistry.CREATIVE_CATEGORY_ALL),
        STRUCTURAL(MMTags.PART_CATEGORY_STRUCTURAL, MMLanguageRegistry.CATEGORY_STRUCTURAL),
        MOBILITY(MMTags.PART_CATEGORY_MOBILITY, MMLanguageRegistry.CATEGORY_MOBILITY),
        WEAPON(MMTags.PART_CATEGORY_WEAPON, MMLanguageRegistry.CATEGORY_WEAPON),
        DECORATION(MMTags.PART_CATEGORY_DECORATION, MMLanguageRegistry.CATEGORY_DECORATION),
        MISC(MMTags.PART_CATEGORY_MISC, MMLanguageRegistry.CATEGORY_MISC),
        UNCLASSIFIED(null, MMLanguageRegistry.CREATIVE_CATEGORY_UNCLASSIFIED);

        /** 该类别对应的标签完整 ID；{@link #ALL} 与 {@link #UNCLASSIFIED} 为 {@code null}。 */
        public final @Nullable ResourceLocation tag;
        private final MMLanguageRegistry label;

        Category(@Nullable ResourceLocation tag, MMLanguageRegistry label) {
            this.tag = tag;
            this.label = label;
        }

        /** 侧栏按钮显示的译名。 */
        public Component displayName() {
            return this.label.translated();
        }
    }

    /** 参与类别判定的五个共用类别，顺序与侧栏一致。 */
    public static final List<Category> CATEGORY_VOCABULARY =
            List.of(Category.STRUCTURAL, Category.MOBILITY, Category.WEAPON, Category.DECORATION, Category.MISC);

    /** 侧栏按钮顺序。 */
    private static final Category[] SIDEBAR = Category.values();

    // ==================== 一次连接内记忆的条件 ====================

    private static @Nullable ResourceLocation selectedSeries;
    private static Category selectedCategory = Category.ALL;
    private static String rememberedQuery = "";

    /** 部件定义或配方索引变化后置位，由 {@code CreativeModeTabsMixin} 消费。 */
    private static boolean contentsDirty;
    /** 目录重建轮次；屏幕据它判断是否需要重建一次可见页列表。 */
    private static long contentsGeneration;

    /** 客户端配方索引重建后标记目录待重建。 */
    public static synchronized void markContentsDirty() {
        contentsDirty = true;
    }

    /**
     * 消费目录待重建标记。
     *
     * @return 自上次消费以来目录数据是否变化过
     */
    public static synchronized boolean consumeContentsDirty() {
        boolean dirty = contentsDirty;
        contentsDirty = false;
        return dirty;
    }

    /** 目录内容被重建，可见页列表可能变化。 */
    public static synchronized void bumpContentsGeneration() {
        contentsGeneration++;
    }

    /** 当前目录重建轮次。 */
    public static synchronized long contentsGeneration() {
        return contentsGeneration;
    }

    /** 退出世界时清空条件与缓存。 */
    public static synchronized void clearSession() {
        selectedSeries = null;
        selectedCategory = Category.ALL;
        rememberedQuery = "";
        contentsDirty = false;
        contentsGeneration++;
    }

    // ==================== 纯函数：标签解析与查询词匹配 ====================

    /** 标签是否为系列标签（路径以 {@code series/} 开头且后缀非空）。 */
    public static boolean isSeriesTag(ResourceLocation tag) {
        String path = tag.getPath();
        String prefix = MMTags.PART_SERIES_PREFIX;
        return path.startsWith(prefix) && path.length() > prefix.length();
    }

    /** 从标签集合中挑出系列标签，按完整 ID 去重排序。 */
    public static List<ResourceLocation> seriesTags(Collection<ResourceLocation> tags) {
        return tags.stream()
                .filter(CreativePartBrowser::isSeriesTag)
                .distinct()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    /**
     * 组合一次候选匹配使用的搜索字段。
     *
     * <p>普通模式取物品显示名、型号完整 ID 与部件自定义标签的完整 ID／译名；标签模式（{@code #} 开头）
     * 只取部件自定义标签与候选物品自带的原版物品标签，因此文字只命中标签字段。字段之间各自独立匹配。</p>
     *
     * @param nameAndId    物品显示名与型号完整 ID（普通模式才使用）
     * @param partTagTexts 部件自定义标签的完整 ID 与译名
     * @param itemTagIds   候选物品自带的原版物品标签完整 ID（标签模式才使用）
     * @param tagMode      是否 {@code #} 标签模式
     * @return 参与子串匹配的字段
     */
    public static List<String> searchFields(List<String> nameAndId, List<String> partTagTexts,
                                            List<String> itemTagIds, boolean tagMode) {
        List<String> fields = new ArrayList<>();
        if (!tagMode) fields.addAll(nameAndId);
        fields.addAll(partTagTexts);
        if (tagMode) fields.addAll(itemTagIds);
        return fields;
    }

    /** 返回标签集合命中的第一个共用类别；没有命中任何共用类别时返回 {@code null}。 */
    public static @Nullable ResourceLocation knownCategory(Collection<ResourceLocation> tags) {
        for (Category category : CATEGORY_VOCABULARY) {
            if (tags.contains(category.tag)) return category.tag;
        }
        return null;
    }

    /** 标签集合是否满足给定类别条件。 */
    public static boolean matchesCategory(Collection<ResourceLocation> tags, Category category) {
        if (category == Category.ALL) return true;
        if (category == Category.UNCLASSIFIED) return knownCategory(tags) == null;
        return tags.contains(category.tag);
    }

    /** 搜索串是否以 {@code #} 开头（标签模式）。 */
    public static boolean isTagQuery(String query) {
        return query != null && query.startsWith("#");
    }

    /** 归一化并按连续空白分词；两侧空白丢弃。 */
    public static List<String> tokenize(String text) {
        if (text == null) return List.of();
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return List.of();
        List<String> words = new ArrayList<>();
        for (String word : trimmed.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!word.isEmpty()) words.add(word);
        }
        return words;
    }

    /** 每个词都必须命中至少一个字段；字段各自独立判定，不做跨字段拼接。 */
    public static boolean matchesAllWords(Collection<String> fields, List<String> words) {
        for (String word : words) {
            boolean hit = false;
            for (String field : fields) {
                if (field.contains(word)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) return false;
        }
        return true;
    }

    /** 给定创造页是否为本期筛选目标页。 */
    public static boolean isFilterTab(@Nullable CreativeModeTab tab) {
        if (tab == null) return false;
        ResourceKey<CreativeModeTab> key = BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(tab).orElse(null);
        if (key == null) return false;
        ResourceLocation id = key.location();
        return id.equals(PART_TAB_ID) || id.equals(FABRICATING_BLUEPRINT_TAB_ID);
    }

    // ==================== 实例状态 ====================

    private final CreativeModeInventoryScreen screen;
    private final Set<TagKey<Item>> visibleTags;
    private final Runnable refreshRequest;
    private final SeriesPickerWidget picker;

    private EditBox mainSearch;
    private FilterButton seriesButton;
    private FilterButton resetButton;
    private final List<FilterButton> categoryButtons = new ArrayList<>();
    private final List<AbstractWidget> widgets = new ArrayList<>();

    private boolean active;
    private boolean mainSearchFocused = true;
    private boolean consumeRelease;

    /** 当前页完整候选。 */
    private List<ItemStack> candidates = List.of();
    private Collection<ItemStack> cachedDisplayItems;
    private Language cachedLanguage;
    /** 型号级缓存：标签集合与搜索文本，键为 PART_TYPE。 */
    private final Map<ResourceLocation, List<ResourceLocation>> tagCache = new HashMap<>();
    private final Map<ResourceLocation, List<String>> textCache = new HashMap<>();
    private final List<ResourceLocation> seriesOptions = new ArrayList<>();
    private final Set<ResourceLocation> seriesOptionSet = new LinkedHashSet<>();
    private final Map<Category, Integer> categoryCounts = new EnumMap<>(Category.class);
    private int matchCount;
    private final long builtGeneration;

    /**
     * 建立一屏的筛选器实例。
     *
     * @param screen         目标创造页
     * @param visibleTags    原版标签提示集合；刷新时就地清空与填充
     * @param mainSearch     原版主搜索框
     * @param refreshRequest 统一刷新入口，指向 {@code refreshSearchResults}
     */
    public CreativePartBrowser(CreativeModeInventoryScreen screen, Set<TagKey<Item>> visibleTags,
                               EditBox mainSearch, Runnable refreshRequest) {
        this.screen = screen;
        this.visibleTags = visibleTags;
        this.mainSearch = mainSearch;
        this.refreshRequest = refreshRequest;
        this.picker = new SeriesPickerWidget(this, screen);
        this.builtGeneration = contentsGeneration();
        this.cachedLanguage = Language.getInstance();
        this.buildWidgets();
    }

    // ==================== 控件 ====================

    private void buildWidgets() {
        int left = this.screen.getGuiLeft();
        int top = this.screen.getGuiTop();

        this.seriesButton = new FilterButton(left + 8, top + 4, 60, 12, null, this::toggleSeriesPicker);
        this.resetButton = new FilterButton(left + 70, top + 4, 10, 12, null, this::reset);
        this.resetButton.setMessage(Component.literal("×"));
        this.resetButton.setTooltipText(MMLanguageRegistry.CREATIVE_RESET.translated());
        this.widgets.add(this.seriesButton);
        this.widgets.add(this.resetButton);

        for (int i = 0; i < SIDEBAR.length; i++) {
            Category category = SIDEBAR[i];
            FilterButton button = new FilterButton(left - 27, top + 4 + i * 18, 26, 16,
                    categoryFallbackIcon(category), () -> this.applyCategory(category));
            button.setMessage(category.displayName());
            this.categoryButtons.add(button);
            this.widgets.add(button);
        }
        this.updateControls();
    }

    /**
     * 侧栏类别的回退图标：该类别没有任何候选时显示，避免空按钮看不出含义。
     *
     * <p>有候选时使用该类别内容物中的一个堆，见 {@link #rebuildOptions()}。</p>
     */
    private static ItemStack categoryFallbackIcon(Category category) {
        return switch (category) {
            case ALL -> new ItemStack(Items.COMPASS);
            case STRUCTURAL -> new ItemStack(Items.BRICKS);
            case MOBILITY -> new ItemStack(Items.MINECART);
            case WEAPON -> new ItemStack(Items.IRON_SWORD);
            case DECORATION -> new ItemStack(Items.PAINTING);
            case MISC -> new ItemStack(Items.BUNDLE);
            case UNCLASSIFIED -> new ItemStack(Items.CHEST);
        };
    }

    /** 挂到屏幕上的控件，按控件系统顺序排列。 */
    public List<AbstractWidget> widgets() {
        return List.copyOf(this.widgets);
    }

    // ==================== 条件 ====================

    /** 当前记忆的主搜索串。 */
    public String query() {
        return rememberedQuery;
    }

    /** 离开目标页时保存主搜索串。 */
    public void captureQuery() {
        if (this.mainSearch != null) rememberedQuery = this.mainSearch.getValue();
    }

    /** 主搜索框当前是否有焦点。 */
    public boolean isMainSearchFocused() {
        return this.mainSearch != null && this.mainSearch.isFocused();
    }

    void applySeries(@Nullable ResourceLocation series) {
        selectedSeries = series;
        this.picker.close();
        this.refreshRequest.run();
    }

    private void applyCategory(Category category) {
        selectedCategory = category;
        this.refreshRequest.run();
    }

    private void reset() {
        selectedSeries = null;
        selectedCategory = Category.ALL;
        rememberedQuery = "";
        if (this.mainSearch != null) this.mainSearch.setValue("");
        this.picker.close();
        this.refreshRequest.run();
    }

    /** 主搜索框内容变化后刷新。 */
    public void onQueryEdited() {
        if (this.mainSearch == null) return;
        rememberedQuery = this.mainSearch.getValue();
        this.refreshRequest.run();
    }

    private void toggleSeriesPicker() {
        if (this.picker.isOpen()) {
            this.picker.close();
            return;
        }
        this.picker.open(this.seriesOptions, selectedSeries);
    }

    // ==================== 候选与缓存 ====================

    /** 目标页当前是否处于激活状态（控件可见、输入归本类处理）。 */
    public boolean isActive() {
        return this.active;
    }

    /**
     * 用目标页的完整候选刷新本地缓存。
     *
     * @param displayItems 当前页 {@code getDisplayItems()} 的同一引用
     * @return 候选引用是否变化
     */
    private boolean updateContents(Collection<ItemStack> displayItems) {
        if (displayItems == this.cachedDisplayItems) return false;
        this.cachedDisplayItems = displayItems;
        this.candidates = List.copyOf(displayItems);
        this.tagCache.clear();
        this.textCache.clear();
        this.rebuildOptions();
        if (selectedSeries != null && !this.seriesOptionSet.contains(selectedSeries) && !this.candidates.isEmpty()) {
            selectedSeries = null;
        }
        return true;
    }

    /**
     * 容器 tick：目录或语言变化时重建缓存并重新过滤。
     *
     * <p>只在引用变化的那一次刷新，普通绘制帧不做事。</p>
     *
     * @param displayItems 当前页完整候选
     */
    public void containerTick(Collection<ItemStack> displayItems) {
        if (!this.active) return;
        Language language = Language.getInstance();
        boolean changed = this.updateContents(displayItems);
        if (language != this.cachedLanguage) {
            this.cachedLanguage = language;
            this.tagCache.clear();
            this.textCache.clear();
            changed = true;
        }
        if (changed) this.refreshRequest.run();
    }

    /** 构造本实例时的目录重建轮次。 */
    public long builtGeneration() {
        return this.builtGeneration;
    }

    /**
     * 从当前候选重建系列选项、类别计数与侧栏图标。
     *
     * <p>侧栏图标取该类别的一个实际成员，因此显示的是内容物本身；成员集合为空时回退到固定原版物品。
     * 选取只在候选集合变化时发生，输入查询词不会让图标跳动。</p>
     */
    private void rebuildOptions() {
        this.seriesOptions.clear();
        this.seriesOptionSet.clear();
        this.categoryCounts.clear();
        Map<Category, List<ItemStack>> members = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            this.categoryCounts.put(category, 0);
            members.put(category, new ArrayList<>());
        }
        LinkedHashSet<ResourceLocation> series = new LinkedHashSet<>();
        for (ItemStack stack : this.candidates) {
            List<ResourceLocation> tags = this.tagsOf(stack);
            series.addAll(seriesTags(tags));
            for (Category category : Category.values()) {
                if (matchesCategory(tags, category)) {
                    this.categoryCounts.merge(category, 1, Integer::sum);
                    members.get(category).add(stack);
                }
            }
        }
        List<ResourceLocation> sorted = new ArrayList<>(series);
        sorted.sort(Comparator.comparing(ResourceLocation::toString));
        this.seriesOptions.addAll(sorted);
        this.seriesOptionSet.addAll(sorted);

        for (int i = 0; i < this.categoryButtons.size(); i++) {
            Category category = SIDEBAR[i];
            List<ItemStack> candidatesForCategory = members.get(category);
            this.categoryButtons.get(i).setIcon(candidatesForCategory.isEmpty()
                    ? categoryFallbackIcon(category)
                    : candidatesForCategory.get(ThreadLocalRandom.current().nextInt(candidatesForCategory.size())));
        }
    }

    @Nullable
    private ResourceLocation partIdOf(ItemStack stack) {
        return stack.get(MMDataComponents.getPART_TYPE());
    }

    /** 候选堆对应型号的全部变体标签（并集、去重、按完整 ID 排序），走型号级缓存。 */
    private List<ResourceLocation> tagsOf(ItemStack stack) {
        ResourceLocation partId = this.partIdOf(stack);
        if (partId == null) return List.of();
        this.ensurePartCache(partId);
        return this.tagCache.getOrDefault(partId, List.of());
    }

    private List<String> tagSearchTexts(@Nullable ResourceLocation partId) {
        if (partId == null) return List.of();
        this.ensurePartCache(partId);
        return this.textCache.getOrDefault(partId, List.of());
    }

    /**
     * 型号级缓存：一次展开变体流得到标签集合与搜索文本，直到候选或语言变化才重建。
     *
     * <p>型号在当前发布快照里不存在时缓存空结果，避免每个候选堆重复查表。</p>
     */
    private void ensurePartCache(ResourceLocation partId) {
        if (this.tagCache.containsKey(partId)) return;
        RegularPartType type = PartDefinitions.regular(true).get(partId);
        if (type == null) {
            this.tagCache.put(partId, List.of());
            this.textCache.put(partId, List.of());
            return;
        }
        this.tagCache.put(partId, TextUtil.getDistinctSortedTags(type));
        this.textCache.put(partId, List.copyOf(TextUtil.collectSearchTexts(type)));
    }

    // ==================== 过滤 ====================

    /**
     * 按三个条件过滤当前完整候选，并把结果写回菜单。
     *
     * <p>每次刷新都从完整候选开始，不修改候选自身的组件与顺序。</p>
     */
    public void refresh() {
        if (this.mainSearch != null) rememberedQuery = this.mainSearch.getValue();
        boolean tagMode = isTagQuery(rememberedQuery);
        List<String> words = tokenize(tagMode ? rememberedQuery.substring(1) : rememberedQuery);

        List<ItemStack> filtered = new ArrayList<>(this.candidates.size());
        for (ItemStack stack : this.candidates) {
            List<ResourceLocation> tags = this.tagsOf(stack);
            if (selectedSeries != null && !tags.contains(selectedSeries)) continue;
            if (!matchesCategory(tags, selectedCategory)) continue;
            if (words.isEmpty()) {
                if (tagMode && !hasAnyTag(stack, tags)) continue;
            } else if (!matchesSearch(stack, tags, words, tagMode)) {
                continue;
            }
            filtered.add(stack);
        }
        this.matchCount = filtered.size();

        this.screen.getMenu().items.clear();
        this.screen.getMenu().items.addAll(filtered);

        this.visibleTags.clear();
        if (tagMode) this.updateVisibleTags(words);

        this.updateControls();
    }

    private boolean matchesSearch(ItemStack stack, List<ResourceLocation> tags, List<String> words, boolean tagMode) {
        ResourceLocation partId = this.partIdOf(stack);
        List<String> nameAndId = new ArrayList<>(2);
        if (!tagMode) {
            nameAndId.add(TextUtil.normalize(stack.getHoverName().getString()));
            if (partId != null) nameAndId.add(TextUtil.normalize(partId.toString()));
        }
        List<String> itemTagIds = new ArrayList<>();
        if (tagMode) {
            for (TagKey<Item> key : stack.getTags().toList()) {
                itemTagIds.add(TextUtil.normalize(key.location().toString()));
            }
        }
        return matchesAllWords(
                searchFields(nameAndId, this.tagSearchTexts(partId), itemTagIds, tagMode), words);
    }

    private boolean hasAnyTag(ItemStack stack, List<ResourceLocation> tags) {
        return !tags.isEmpty() || stack.getTags().findAny().isPresent();
    }

    private void updateVisibleTags(List<String> words) {
        if (words.isEmpty()) return;
        for (TagKey<Item> key : BuiltInRegistries.ITEM.getTagNames().toList()) {
            if (matchesAllWords(List.of(TextUtil.normalize(key.location().toString())), words)) {
                this.visibleTags.add(key);
            }
        }
    }

    private void updateControls() {
        if (this.seriesButton == null) return;
        Component seriesName = selectedSeries == null
                ? MMLanguageRegistry.CREATIVE_ALL_SERIES.translated()
                : Component.literal(TextUtil.getTranslatedOrRawTag(selectedSeries));
        this.seriesButton.setMessage(seriesName);
        this.seriesButton.setTooltipText(Component.empty()
                .append(seriesName)
                .append("\n")
                .append(countText(this.matchCount, this.candidates.size())));
        this.seriesButton.setSelected(selectedSeries != null);
        for (int i = 0; i < this.categoryButtons.size(); i++) {
            Category category = SIDEBAR[i];
            FilterButton button = this.categoryButtons.get(i);
            int count = this.categoryCounts.getOrDefault(category, 0);
            button.setSelected(category == selectedCategory);
            button.setActive(category == Category.ALL || count > 0);
            button.setTooltipText(categoryHint(category));
        }
        this.resetButton.setActive(
                selectedSeries != null || selectedCategory != Category.ALL || !rememberedQuery.isEmpty());
        this.picker.onOptionsChanged(this.seriesOptions);
    }

    private static Component countText(int matched, int total) {
        return Component.translatable(MMLanguageRegistry.CREATIVE_MATCH_COUNT.tag, matched, total);
    }

    /**
     * 类别按钮的悬停提示：类别译名，五个共用类别再加一行完整标签 ID。
     *
     * <p>侧栏用图标表示类别，提示里给出标签 ID 才能让内容包作者直接对照 {@code variants[].tags}。
     * 「全部」与「未分类」没有对应标签，只显示译名。</p>
     */
    private static Component categoryHint(Category category) {
        Component name = category.displayName();
        if (category.tag == null) return name;
        return Component.empty()
                .append(name)
                .append("\n")
                .append(Component.literal(category.tag.toString()));
    }

    // ==================== 输入 ====================

    /** 弹层是否打开。 */
    public boolean isPopupOpen() {
        return this.picker.isOpen();
    }

    /** 弹层是否覆盖给定屏幕坐标。 */
    public boolean isPopupCovering(double mouseX, double mouseY) {
        return this.picker.isOpen() && this.picker.isMouseOver(mouseX, mouseY);
    }

    /**
     * 鼠标按下入口。
     *
     * @return 本类是否消费该次按下
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active) return false;
        this.consumeRelease = false;
        if (this.picker.isOpen()) {
            boolean inside = this.picker.mouseClicked(mouseX, mouseY, button);
            if (!inside) {
                this.picker.close();
                this.restoreMainSearchFocus();
            }
            this.consumeRelease = true;
            return true;
        }
        if (this.mainSearch != null && over(this.mainSearch, mouseX, mouseY)) {
            this.mainSearch.setFocused(true);
            if (button == 1) {
                this.mainSearch.setValue("");
                this.onQueryEdited();
            } else {
                this.mainSearch.mouseClicked(mouseX, mouseY, button);
            }
            this.consumeRelease = true;
            return true;
        }
        for (AbstractWidget widget : this.widgets) {
            if (mouseX >= widget.getX() && mouseX < widget.getX() + widget.getWidth()
                    && mouseY >= widget.getY() && mouseY < widget.getY() + widget.getHeight()) {
                widget.mouseClicked(mouseX, mouseY, button);
                this.consumeRelease = true;
                return true;
            }
        }
        if (this.mainSearchFocused) {
            this.mainSearchFocused = false;
            if (this.mainSearch != null) this.mainSearch.setFocused(false);
        }
        return false;
    }

    /** 鼠标释放入口；消费掉与本次按下对应的释放，避免释放事件触发原版 tab 切换。 */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!this.active) return false;
        if (this.consumeRelease) {
            this.consumeRelease = false;
            return true;
        }
        return this.picker.isOpen();
    }

    /** 滚轮入口：弹层打开时滚动弹层。 */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!this.active) return false;
        if (this.picker.isOpen()) {
            this.picker.mouseScrolled(mouseX, mouseY, scrollY);
            return true;
        }
        return false;
    }

    /** 拖拽入口：弹层打开或本次拖拽起始于本类控件时消费。 */
    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        return this.active && (this.picker.isOpen() || this.consumeRelease);
    }

    /**
     * 键盘按下入口。
     *
     * @return 本类是否消费该次按下
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.active) return false;
        if (this.picker.isOpen()) {
            this.picker.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (!isMainSearchFocused()) {
            // EditBox 不检查自身焦点就会处理编辑键；失焦时把改动还原，字符输入由 charTyped 另行拦截。
            String before = this.mainSearch.getValue();
            boolean handled = this.mainSearch.keyPressed(keyCode, scanCode, modifiers);
            if (!before.equals(this.mainSearch.getValue())) this.mainSearch.setValue(before);
            return handled;
        }
        if (keyCode == 256) return false;
        String before = this.mainSearch.getValue();
        this.mainSearch.keyPressed(keyCode, scanCode, modifiers);
        if (!before.equals(this.mainSearch.getValue())) this.onQueryEdited();
        return true;
    }

    /**
     * 字符输入入口；弹层打开时字符只进入弹层。
     *
     * @return 字符是否已被本类消费
     */
    public boolean charTyped(char codePoint, int modifiers) {
        if (!this.active) return false;
        if (this.picker.isOpen()) {
            this.picker.charTyped(codePoint, modifiers);
            return true;
        }
        return false;
    }

    /** 主搜索失焦且弹层未打开时需要拦截字符输入。 */
    public boolean shouldBlockUntargetedCharTyped() {
        return this.active && !this.picker.isOpen() && !isMainSearchFocused();
    }

    // ==================== 页切换与焦点 ====================

    /**
     * 当前页切换后同步控件状态。
     *
     * @param target       是否为本期目标页
     * @param displayItems 当前页完整候选
     */
    public void onTabChanged(boolean target, Collection<ItemStack> displayItems) {
        this.active = target;
        if (!target) {
            this.picker.close();
            for (AbstractWidget widget : this.widgets) widget.visible = false;
            return;
        }
        for (AbstractWidget widget : this.widgets) widget.visible = true;
        this.updateContents(displayItems);
        if (this.mainSearch != null) {
            this.mainSearch.setCanLoseFocus(true);
            this.mainSearch.setMaxLength(SEARCH_MAX_LENGTH);
            if (!this.mainSearch.getValue().equals(rememberedQuery)) {
                this.mainSearch.setValue(rememberedQuery);
            }
            this.mainSearch.setFocused(this.mainSearchFocused);
        }
        this.refreshRequest.run();
    }

    /** 弹层关闭后恢复展开前的焦点。 */
    void restoreMainSearchFocus() {
        if (this.mainSearch != null) this.mainSearch.setFocused(this.mainSearchFocused);
    }

    /** 展开弹层前记下主搜索焦点并让出焦点。 */
    void suspendMainSearchFocus() {
        this.mainSearchFocused = isMainSearchFocused();
        if (this.mainSearch != null) this.mainSearch.setFocused(false);
    }

    // ==================== 绘制 ====================

    /** 绘制弹层与零结果提示；在屏幕 {@code render} 的末尾调用。 */
    public void renderOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!this.active) return;
        if (this.picker.isOpen()) {
            this.picker.render(guiGraphics, mouseX, mouseY);
            return;
        }
        if (this.matchCount == 0 && !this.candidates.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            Component hint = MMLanguageRegistry.CREATIVE_NO_RESULT.translated();
            guiGraphics.drawString(font, hint,
                    this.screen.getGuiLeft() + 90 - font.width(hint) / 2,
                    this.screen.getGuiTop() + 54, 0xFFA0A0A0, false);
        }
    }

    /** 目标页完整候选的当前匹配数。 */
    public int matchCount() {
        return this.matchCount;
    }

    private static boolean over(AbstractWidget widget, double mouseX, double mouseY) {
        return mouseX >= widget.getX() && mouseX < widget.getX() + widget.getWidth()
                && mouseY >= widget.getY() && mouseY < widget.getY() + widget.getHeight();
    }

    // ==================== 内部类型 ====================

    /**
     * 侧栏与顶部筛选按钮。
     *
     * <p>选中态用底色与边框区分，禁用态降低不透明度；旁白读按钮译名，悬停提示读数量反馈。
     * 图标可空，为空时绘制译名并按宽度截断。</p>
     */
    private static final class FilterButton extends AbstractWidget {
        private @Nullable ItemStack icon;
        private final Runnable action;
        private boolean selected;
        private @Nullable Component announcedHint;

        FilterButton(int x, int y, int width, int height, @Nullable ItemStack icon, Runnable action) {
            super(x, y, width, height, Component.empty());
            this.icon = icon;
            this.action = action;
        }

        /** 换图标；侧栏在候选集合变化时用它换成该类别的内容物。 */
        void setIcon(@Nullable ItemStack icon) {
            this.icon = icon;
        }

        void setSelected(boolean selected) {
            this.selected = selected;
        }

        void setActive(boolean active) {
            this.active = active;
        }

        void setTooltipText(@Nullable Component text) {
            this.announcedHint = text;
            this.setTooltip(text == null ? null : Tooltip.create(text));
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int x = this.getX();
            int y = this.getY();
            int right = x + this.getWidth();
            int bottom = y + this.getHeight();
            int fill;
            int border;
            if (!this.active) {
                fill = 0x50000000;
                border = 0x50808080;
            } else if (this.selected) {
                fill = 0xE02E6FBF;
                border = 0xFFFFFFFF;
            } else if (this.isHoveredOrFocused()) {
                fill = 0xE0555555;
                border = 0xFFE0E0E0;
            } else {
                fill = 0xC8202020;
                border = 0xFF909090;
            }
            guiGraphics.fill(x, y, right, bottom, fill);
            guiGraphics.fill(x, y, right, y + 1, border);
            guiGraphics.fill(x, bottom - 1, right, bottom, border);
            guiGraphics.fill(x, y, x + 1, bottom, border);
            guiGraphics.fill(right - 1, y, right, bottom, border);
            if (this.icon != null) {
                guiGraphics.renderItem(this.icon, x + (this.getWidth() - 16) / 2, y + (this.getHeight() - 16) / 2);
            } else {
                Font font = Minecraft.getInstance().font;
                String text = font.plainSubstrByWidth(this.getMessage().getString(), this.getWidth() - 4);
                int color = this.active ? 0xFFFFFFFF : 0xFF909090;
                guiGraphics.drawString(font, text,
                        x + 2 + Math.max(0, (this.getWidth() - 4 - font.width(text)) / 2),
                        y + (this.getHeight() - 8) / 2, color, false);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, this.getMessage());
            if (this.announcedHint != null) output.add(NarratedElementType.HINT, this.announcedHint);
        }

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            this.action.run();
        }
    }
}
