package io.github.sweetzonzi.machine_max.common.mech.vehicle.track;

import java.util.ArrayList;
import java.util.List;

/**
 * 履带的材料扇区快照：每个扇区还剩几成材料，以及它是否是被撕开的断口。
 *
 * <p>履带沿带面分成 {@code S} 个等长材料扇区，每区 {@code N / S} 节材料（可为小数）。
 * 权威状态只有本记录的两个等长列表：</p>
 *
 * <ul>
 *   <li>{@link #fill()} —— 扇区剩余材料比例 {@code [0, 1]}，{@code 0} 表示该段已被撕掉。
 *       与 {@code SubPartData.durability_ratio} 同一约定：内容包调整每节耐久或换材质时，
 *       已放置的履带保持「还剩几成」，而不是把绝对点数跟着上限缩放。</li>
 *   <li>{@link #torn()} —— 断口标记。被打断的扇区置真，焊满后清位。</li>
 * </ul>
 *
 * <p>{@code S} 取成员数，所以两个列表的长度就是已建造扇区数；空列表表示履带未建造
 * （只有设计、没有材料，见 {@link TrackLink#isBuilt()}）。本记录不可变：伤害结算与修复
 * 都换一个新实例发布，命中线程只读快照，不加锁。</p>
 *
 * <p>派生读数（{@link #links(int)}、{@link #refundLinks(int)}、{@link #isBroken()}）也在这里
 * 现算，不落第二个字段——记录里再存一份就是第三个同义对象。</p>
 */
public record TrackSectors(List<Float> fill, List<Boolean> torn) {
    /** 未建造：只有设计、没有材料。 */
    public static final TrackSectors UNBUILT = new TrackSectors(List.of(), List.of());

    /** 断口缺额的换算分母：每扇区材料的三分之一，向上夹在两处整数。 */
    private static final float TORN_LINK_DIVISOR = 3f;
    private static final int MIN_TORN_LINKS = 1;
    private static final int MAX_TORN_LINKS = 3;

    public TrackSectors {
        fill = List.copyOf(fill);
        torn = List.copyOf(torn);
        if (fill.size() != torn.size()) {
            throw new IllegalArgumentException("扇区材料比例与断口标记长度不一致: "
                    + fill.size() + " / " + torn.size());
        }
    }

    /** 满材料：{@code sectors} 个扇区各 {@code 1.0}，无断口。 */
    public static TrackSectors full(int sectors) {
        int count = Math.max(0, sectors);
        if (count == 0) return UNBUILT;
        List<Float> fill = new ArrayList<>(count);
        List<Boolean> torn = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            fill.add(1f);
            torn.add(false);
        }
        return new TrackSectors(fill, torn);
    }

    /**
     * 材料比例的夹取：伤害扣减与修复推进共用这一个函数，{@code [0, 1]} 之外一律按端点处理。
     *
     * <p>非有限值按 0 处理：那是存档 / 网络里的垃圾值，在解码处已被拦下，这里是最后一道。</p>
     */
    public static float clampRatio(float ratio) {
        return Float.isFinite(ratio) ? Math.clamp(ratio, 0f, 1f) : 0f;
    }

    /** 是否已建造：{@code section_fill} 非空。空列表即「只有设计、没有材料」。 */
    public boolean isBuilt() {
        return !fill.isEmpty();
    }

    public int size() {
        return fill.size();
    }

    public float fill(int sector) {
        return fill.get(sector);
    }

    public boolean isTorn(int sector) {
        return torn.get(sector);
    }

    /** 是否还有没焊满的断口。 */
    public boolean isBroken() {
        for (Boolean flag : torn) {
            if (flag) return true;
        }
        return false;
    }

    /** 整条带的剩余材料比例之和。拆除返还与 HUD 完好度读它。 */
    public float totalFill() {
        float total = 0f;
        for (Float ratio : fill) total += ratio;
        return total;
    }

    /**
     * 每个断口缺的节数 {@code R = clamp(round((N/S)/3), 1, 3)}。
     *
     * <p>公式保留为扇区数改变后的占位：{@code S = M} 时第一方三车都取到上界 3。</p>
     */
    public static int tornLinks(int linkCount, int sectors) {
        if (sectors <= 0 || linkCount <= 0) return MIN_TORN_LINKS;
        long rounded = Math.round((float) linkCount / sectors / TORN_LINK_DIVISOR);
        return (int) Math.clamp(rounded, MIN_TORN_LINKS, MAX_TORN_LINKS);
    }

    /**
     * 已上料的节数 {@code links = N − R × unpaid}，{@code unpaid} 是断口里材料仍为 0、
     * 还没上料的个数。
     *
     * <p>它与「还有没焊满的断口」（{@link #isBroken()}）是两本账：断口开焊时先扣 {@code R} 节，
     * 材料随即回到该扇区，{@code links} 立刻补回；{@code torn} 要等扇区焊满才清。
     * 因此会短暂出现 {@code links == N} 而仍有断口的状态，对应零件「材料齐了、焊缝没收口」。</p>
     */
    public int links(int linkCount) {
        if (!isBuilt() || linkCount <= 0) return 0;
        int unpaid = 0;
        for (int j = 0; j < fill.size(); j++) {
            if (torn.get(j) && fill.get(j) <= 0f) unpaid++;
        }
        return Math.clamp(linkCount - tornLinks(linkCount, fill.size()) * unpaid, 0, linkCount);
    }

    /**
     * 拆除返还的节数：{@code round(N × Σ fill / S)}，断口扇区按 0 计。
     *
     * <p>把 {@code sectorMax = durability_per_link × N / S} 代进点数式
     * {@code round(durability / durability_per_link)} 后剂量系数与 {@code S} 一起约掉，
     * 于是返还只与「还剩几成」有关，与型号的每节耐无关。</p>
     */
    public int refundLinks(int linkCount) {
        if (!isBuilt() || linkCount <= 0) return 0;
        return Math.clamp(Math.round(linkCount * totalFill() / fill.size()), 0, linkCount);
    }

    /** 换一个扇区的材料比例；比例先夹取。 */
    public TrackSectors withFill(int sector, float ratio) {
        List<Float> next = new ArrayList<>(fill);
        next.set(sector, clampRatio(ratio));
        return new TrackSectors(next, torn);
    }

    /** 换一个扇区的断口标记。 */
    public TrackSectors withTorn(int sector, boolean broken) {
        List<Boolean> next = new ArrayList<>(torn);
        next.set(sector, broken);
        return new TrackSectors(fill, next);
    }

    /**
     * 按材料比例重采样到 {@code sectors} 段，供成员变化使用。
     *
     * <p>新扇区 {@code j} 覆盖材料区间 {@code [j/S_new, (j+1)/S_new)}，取与旧扇区重叠部分的
     * 加权均值；断口随重叠区继承，材料比例为 0 的断口仍是断口。只保证「战损不凭空消失、
     * 也不凭空修满」，不追求逐节精确。</p>
     */
    public static TrackSectors resample(TrackSectors from, int sectors) {
        int target = Math.max(0, sectors);
        if (!from.isBuilt()) return UNBUILT;
        if (target == 0) return UNBUILT;
        if (from.size() == target) return from;
        int source = from.size();
        List<Float> fill = new ArrayList<>(target);
        List<Boolean> torn = new ArrayList<>(target);
        for (int j = 0; j < target; j++) {
            double start = (double) j / target;
            double end = (double) (j + 1) / target;
            double weighted = 0;
            double covered = 0;
            boolean anyTorn = false;
            for (int i = 0; i < source; i++) {
                double overlap = Math.min(end, (double) (i + 1) / source) - Math.max(start, (double) i / source);
                if (overlap <= 0) continue;
                weighted += from.fill(i) * overlap;
                covered += overlap;
                anyTorn |= from.isTorn(i);
            }
            fill.add(covered > 0 ? clampRatio((float) (weighted / covered)) : 0f);
            torn.add(anyTorn);
        }
        return new TrackSectors(fill, torn);
    }
}
