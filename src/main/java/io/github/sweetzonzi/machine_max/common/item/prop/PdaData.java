package io.github.sweetzonzi.machine_max.common.item.prop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * PDA 蓝图终端的全部物品状态，随 {@code machine_max:pda_data} 数据组件持久化并随网络同步。
 *
 * <p>四个字段：</p>
 * <ul>
 *   <li>{@code entries}：配方 id → 残留次数的<b>有序</b>映射，键即蓝图条目的唯一标识，迭代顺序恒为
 *       {@link #ENTRY_ORDER}（配方 id 字符串序）。条目只存配方 id 与次数，类别与零件 id 一律由配方 id
 *       经本侧索引反查；</li>
 *   <li>{@code shortcuts}：格位序号 0~8 → 该格位绑定的配方 id，稀疏映射，键不存在即该格位为空；</li>
 *   <li>{@code selected}：当前格位序号，域 0~8；</li>
 *   <li>{@code designMode}：设计模式开关。</li>
 * </ul>
 *
 * <p>产出 {@code PdaData} 的每条路径（编解码与每个改动方法）都以 {@link #sanitized()} 收口，
 * 否则 {@code entries} 的迭代顺序会静默退化为插入序。写入组件的唯一入口是
 * {@link PdaHelper#setData(net.minecraft.world.item.ItemStack, PdaData)}。</p>
 */
public record PdaData(SortedMap<ResourceLocation, Integer> entries,
                      Map<Integer, ResourceLocation> shortcuts,
                      int selected,
                      boolean designMode) {
    /** 残留次数的哨兵值：表示无限次。 */
    public static final int INFINITE_USES = -1;
    /** 设计模式快捷栏的格位数量。 */
    public static final int SHORTCUT_COUNT = 9;
    /**
     * {@code entries} 的定序器：配方 id 的字符串序。
     *
     * <p>显式比较器而非依赖 {@link ResourceLocation} 自身的比较规则，字符串序跨版本与跨侧都稳定，
     * 是界面签名比较成立的前提。</p>
     */
    public static final Comparator<ResourceLocation> ENTRY_ORDER = Comparator.comparing(ResourceLocation::toString);

    /** 空数据；所有字段取默认值，映射为不可变集合，避免被调用方就地修改而污染全局常量。 */
    public static final PdaData EMPTY = new PdaData(
            Collections.unmodifiableSortedMap(new TreeMap<>(ENTRY_ORDER)),
            Map.of(),
            0,
            false
    );

    /** JSON/NBT 的对象键只能是字符串，解码结果收口为 {@link #ENTRY_ORDER} 有序映射。 */
    private static final Codec<SortedMap<ResourceLocation, Integer>> ENTRIES_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).xmap(
                    map -> {
                        SortedMap<ResourceLocation, Integer> sorted = new TreeMap<>(ENTRY_ORDER);
                        sorted.putAll(map);
                        return sorted;
                    },
                    sorted -> new LinkedHashMap<>(sorted)
            );

    /** {@code shortcuts} 的编解码：格位序号的十进制字符串 ↔ 整数键；非法序号随键一起丢弃。 */
    private static final Codec<Map<Integer, ResourceLocation>> SHORTCUTS_CODEC =
            Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC).xmap(
                    map -> {
                        Map<Integer, ResourceLocation> parsed = new HashMap<>();
                        for (Map.Entry<String, ResourceLocation> entry : map.entrySet()) {
                            try {
                                parsed.put(Integer.parseInt(entry.getKey()), entry.getValue());
                            } catch (NumberFormatException ignored) {
                                // 非十进制格位序号视为无效键，与 sanitized() 的第 3 步同效
                            }
                        }
                        return parsed;
                    },
                    map -> {
                        Map<String, ResourceLocation> stringKeyed = new LinkedHashMap<>();
                        for (Map.Entry<Integer, ResourceLocation> entry : map.entrySet()) {
                            stringKeyed.put(Integer.toString(entry.getKey()), entry.getValue());
                        }
                        return stringKeyed;
                    }
            );

    /** 落盘与界面读取共用的编解码，四个字段全部参与；解码侧以 {@link #sanitized()} 收口。 */
    public static final Codec<PdaData> CODEC = RecordCodecBuilder.<PdaData>create(instance -> instance.group(
                    ENTRIES_CODEC.optionalFieldOf("entries",
                            new TreeMap<ResourceLocation, Integer>(ENTRY_ORDER)).forGetter(PdaData::entries),
                    SHORTCUTS_CODEC.optionalFieldOf("shortcuts",
                            Map.<Integer, ResourceLocation>of()).forGetter(PdaData::shortcuts),
                    Codec.INT.optionalFieldOf("selected", 0).forGetter(PdaData::selected),
                    Codec.BOOL.optionalFieldOf("design_mode", false).forGetter(PdaData::designMode)
            ).apply(instance, PdaData::new))
            .xmap(PdaData::sanitized, Function.identity());

    /** 网络同步编解码，由 {@link #CODEC} 派生（组件同时声明 persistent 与 networkSynchronized）。 */
    public static final StreamCodec<ByteBuf, PdaData> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    /** 残留次数是否表示无限次。 */
    public static boolean isInfinite(int uses) {
        return uses < 0;
    }

    /** 读某一格位绑定的配方 id；格位越界或未绑定时返回 {@code null}。 */
    @Nullable
    public ResourceLocation shortcutAt(int index) {
        return shortcuts.get(index);
    }

    /** 读某条目的残留次数；未收纳该配方时返回 {@code null}。 */
    @Nullable
    public Integer usesOf(ResourceLocation recipeId) {
        return entries.get(recipeId);
    }

    /**
     * 绑定或解绑一个格位。
     *
     * @param index    格位序号
     * @param recipeId 目标配方 id；为 {@code null} 表示解绑该格位
     */
    public PdaData withShortcut(int index, @Nullable ResourceLocation recipeId) {
        Map<Integer, ResourceLocation> next = new HashMap<>(shortcuts);
        if (recipeId == null) {
            next.remove(index);
        } else {
            next.put(index, recipeId);
        }
        return new PdaData(entries, next, selected, designMode).sanitized();
    }

    /**
     * 写入当前格位序号；序号按 {@link #SHORTCUT_COUNT} 取模归一，与服务端共用同一段逻辑。
     */
    public PdaData withSelected(int index) {
        return new PdaData(entries, shortcuts, normalizeIndex(index), designMode).sanitized();
    }

    /** 写入设计模式开关。 */
    public PdaData withDesignMode(boolean on) {
        return new PdaData(entries, shortcuts, selected, on).sanitized();
    }

    /**
     * 把一张刚解析出的蓝图并入数据（纯函数）。
     *
     * <p>合并规则：未收纳时新增；已有无限次时拒绝并入并原样返回 {@code this}；已有有限次时按传入次数
     * 累加，传入无限次则升级为无限。</p>
     *
     * @param recipeId     蓝图对应的配方 id
     * @param incomingUses 传入蓝图的残留次数（无限次为负）
     * @return 合并后的新实例；本次被拒绝时返回 {@code this}
     */
    public PdaData mergeEntry(ResourceLocation recipeId, int incomingUses) {
        Integer current = usesOf(recipeId);
        if (current != null && isInfinite(current)) return this;
        int merged;
        if (current == null) {
            merged = incomingUses;
        } else if (isInfinite(incomingUses)) {
            merged = INFINITE_USES;
        } else {
            merged = current + incomingUses;
        }
        SortedMap<ResourceLocation, Integer> nextEntries = new TreeMap<>(ENTRY_ORDER);
        nextEntries.putAll(entries);
        nextEntries.put(recipeId, merged);
        return new PdaData(nextEntries, shortcuts, selected, designMode).sanitized();
    }

    /**
     * 移除一个条目，并同步清除引用它的所有格位绑定。
     *
     * @param recipeId 待移除的配方 id
     */
    public PdaData withoutEntry(ResourceLocation recipeId) {
        SortedMap<ResourceLocation, Integer> nextEntries = new TreeMap<>(ENTRY_ORDER);
        nextEntries.putAll(entries);
        nextEntries.remove(recipeId);
        Map<Integer, ResourceLocation> nextShortcuts = new HashMap<>(shortcuts);
        nextShortcuts.values().removeIf(recipeId::equals);
        return new PdaData(nextEntries, nextShortcuts, selected, designMode).sanitized();
    }

    /**
     * 收口到满足全部不变量的新实例，用于所有反序列化入口与每个改动方法。
     *
     * <p>按顺序执行：</p>
     * <ol>
     *   <li>丢弃 {@code entries} 中键为空的项；</li>
     *   <li>值小于 {@link #INFINITE_USES} 时归一为 {@link #INFINITE_USES}，值为 0 的条目丢弃；</li>
     *   <li>丢弃 {@code shortcuts} 中键不在 0~8 的项；</li>
     *   <li>丢弃 {@code shortcuts} 中值在 {@code entries} 里找不到对应键的项；</li>
     *   <li>{@code selected} 按 {@link #SHORTCUT_COUNT} 取模归一；{@code designMode} 不做处理。</li>
     * </ol>
     *
     * <p>不校验配方 id 是否存在于本侧索引：本类拿不到 {@code Level}，且配方消失时条目应当保留。</p>
     */
    public PdaData sanitized() {
        SortedMap<ResourceLocation, Integer> cleanEntries = new TreeMap<>(ENTRY_ORDER);
        for (Map.Entry<ResourceLocation, Integer> entry : entries.entrySet()) {
            ResourceLocation recipeId = entry.getKey();
            Integer uses = entry.getValue();
            if (recipeId == null || uses == null) continue;
            if (uses < INFINITE_USES) uses = INFINITE_USES;
            if (uses == 0) continue;
            cleanEntries.put(recipeId, uses);
        }
        Map<Integer, ResourceLocation> cleanShortcuts = new HashMap<>();
        for (Map.Entry<Integer, ResourceLocation> entry : shortcuts.entrySet()) {
            Integer index = entry.getKey();
            ResourceLocation recipeId = entry.getValue();
            if (index == null || index < 0 || index >= SHORTCUT_COUNT) continue;
            if (recipeId == null || !cleanEntries.containsKey(recipeId)) continue;
            cleanShortcuts.put(index, recipeId);
        }
        return new PdaData(cleanEntries, cleanShortcuts, normalizeIndex(selected), designMode);
    }

    /** 把格位序号归一到 0~{@link #SHORTCUT_COUNT} 区间（越界环绕）。 */
    private static int normalizeIndex(int index) {
        return Math.floorMod(index, SHORTCUT_COUNT);
    }
}
