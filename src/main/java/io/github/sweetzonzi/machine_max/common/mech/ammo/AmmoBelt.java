package io.github.sweetzonzi.machine_max.common.mech.ammo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 一份有限、有序的弹药内容。
 * <p>
 * 模式列表 {@link #pattern()} 不可变、可以在多个实例间共享；{@link #remaining()} 是每个实例自己的
 * 消耗游标，{@link #recharge()} 的回充运行态也属于实例，由持有者原地推进。因此本类不是 record：
 * 身份（{@link #spec()} 之外的内容）与消耗进度必须分开，复制一份满额模板用 {@link #freshCopy()}，
 * 复制当前状态用 {@link #copy()}。
 * <p>
 * 弹序按 {@code (length - remaining) % patternTotal} 在模式展开序列上取：消耗一发只把
 * {@code remaining} 减一，剩余为零即耗尽，不因模式可循环而继续发射。
 * <p>
 * 编解码边界见 {@link #CODEC} 与 {@link #STREAM_CODEC}：模式计数必须为正、{@code 0 <= remaining <= length}、
 * 模式总发数不得溢出、空模式只允许无回充且余量为零的实例。NBT 额外保存回充运行态（延迟与零碎进度），
 * JSON 与网络格式只表达内容与配置。
 */
public final class AmmoBelt {

    /** 一条模式项：连续 {@code count} 发同种弹药。 */
    public record PatternEntry(ResourceLocation ammo, int count) {

        public static final Codec<PatternEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("ammo").forGetter(PatternEntry::ammo),
                Codec.INT.fieldOf("count").forGetter(PatternEntry::count)
        ).apply(instance, PatternEntry::new));
    }

    /**
     * 自身回充配置，属于已装实例，不指向源的同名状态。
     *
     * @param regenPerMinute    回充速率（发／分钟）
     * @param regenDelay        成功射击后暂停回充的秒数
     * @param energyCostPerRound 每发回充的能源费用，由持有者提供实际能源条件
     */
    public record Recharge(float regenPerMinute, float regenDelay, float energyCostPerRound) {

        public static final Codec<Recharge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("regen_per_minute").forGetter(Recharge::regenPerMinute),
                Codec.FLOAT.optionalFieldOf("regen_delay", 0f).forGetter(Recharge::regenDelay),
                Codec.FLOAT.optionalFieldOf("energy_cost_per_round", 0f).forGetter(Recharge::energyCostPerRound)
        ).apply(instance, Recharge::new));
    }

    /** 模式条目数上界：内容包按弹序段书写，超过这个量级说明写成了逐发列表。 */
    public static final int MAX_PATTERN_ENTRIES = 256;

    public static final Codec<AmmoBelt> CODEC = RecordCodecBuilder.<AmmoBelt>create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("container_id").forGetter(b -> Optional.ofNullable(b.containerId)),
            Codec.STRING.optionalFieldOf("label", "").forGetter(AmmoBelt::label),
            PatternEntry.CODEC.listOf().fieldOf("pattern").forGetter(AmmoBelt::pattern),
            Codec.INT.fieldOf("length").forGetter(AmmoBelt::length),
            Codec.INT.optionalFieldOf("remaining").forGetter(b -> Optional.of(b.remaining)),
            Recharge.CODEC.optionalFieldOf("recharge").forGetter(b -> Optional.ofNullable(b.recharge))
    ).apply(instance, (containerId, label, pattern, length, remaining, recharge) -> new AmmoBelt(
            containerId.orElse(null), label, pattern, length, remaining.orElse(length), recharge.orElse(null))))
            .validate(AmmoBelt::validate);

    public static final StreamCodec<ByteBuf, AmmoBelt> STREAM_CODEC =
            StreamCodec.of(AmmoBelt::write, AmmoBelt::read);

    @Nullable
    private final ResourceLocation containerId;
    private final String label;
    private final List<PatternEntry> pattern;
    private final int length;
    private int remaining;
    @Nullable
    private final Recharge recharge;

    /** 回充延迟剩余 tick；每次成功射击重置为 {@code recharge.regenDelay} 秒。 */
    private int rechargeDelayTicks;
    /** 回充零碎进度：每 tick 累加速率折算量，满 1 补入一发。 */
    private float rechargeProgress;

    public AmmoBelt(@Nullable ResourceLocation containerId, String label, List<PatternEntry> pattern,
                    int length, int remaining, @Nullable Recharge recharge) {
        this.containerId = containerId;
        this.label = label;
        this.pattern = List.copyOf(pattern);
        this.length = length;
        this.remaining = remaining;
        this.recharge = recharge;
    }

    // ==================== 内容与游标 ====================

    @Nullable
    public ResourceLocation containerId() {
        return containerId;
    }

    public String label() {
        return label;
    }

    public List<PatternEntry> pattern() {
        return pattern;
    }

    /** 这份 Belt 的容量（发），定值，不随消耗变化。 */
    public int length() {
        return length;
    }

    /** 剩余发数，唯一随消耗变化的计数。 */
    public int remaining() {
        return remaining;
    }

    @Nullable
    public Recharge recharge() {
        return recharge;
    }

    public boolean hasRecharge() {
        return recharge != null;
    }

    public int rechargeDelayTicks() {
        return rechargeDelayTicks;
    }

    public float rechargeProgress() {
        return rechargeProgress;
    }

    /** 模式展开后的总发数；空模式为零。 */
    public int patternTotal() {
        return patternTotal(pattern);
    }

    /** 模式展开后的总发数，超出 int 时钳到 {@link Integer#MAX_VALUE}（校验期已拒绝溢出）。 */
    public static int patternTotal(List<PatternEntry> pattern) {
        long total = 0L;
        for (PatternEntry entry : pattern) {
            total += entry.count();
            if (total >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return (int) total;
    }

    /** 下一发的弹种；余量为零或模式为空时返回 null。 */
    @Nullable
    public ResourceLocation nextRoundId() {
        if (remaining <= 0) return null;
        int total = patternTotal();
        if (total <= 0) return null;
        return typeAt(pattern, Math.floorMod(length - remaining, total));
    }

    /** 消耗一发并返回其弹种；余量为零时返回 null 且不改动游标。 */
    @Nullable
    public ResourceLocation consumeNext() {
        ResourceLocation id = nextRoundId();
        if (id != null) remaining--;
        return id;
    }

    /** 模式内出现过的弹种（去重，按首次出现顺序）。 */
    public List<ResourceLocation> ammoTypes() {
        List<ResourceLocation> types = new ArrayList<>();
        for (PatternEntry entry : pattern) {
            if (!types.contains(entry.ammo())) types.add(entry.ammo());
        }
        return types;
    }

    /**
     * 按弹种统计剩余发数。
     * <p>
     * 完整循环按模式计数直接乘算，只对不足一轮的尾段逐发取型；不为汇总生成逐发对象。
     */
    public Map<ResourceLocation, Integer> countRemaining() {
        return countRounds(pattern, length - remaining, remaining);
    }

    /**
     * 从 {@code offset} 起统计 {@code rounds} 发里各弹种的数量。
     * <p>
     * {@code offset} 是模式展开序列上的起点（{@code length - remaining}），用于让统计结果与实际余量对齐。
     */
    public static Map<ResourceLocation, Integer> countRounds(List<PatternEntry> pattern, int offset, int rounds) {
        int total = patternTotal(pattern);
        if (total <= 0 || rounds <= 0) return Map.of();
        int fullCycles = rounds / total;
        int tail = rounds % total;
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        if (fullCycles > 0) {
            for (PatternEntry entry : pattern) {
                counts.merge(entry.ammo(), fullCycles * entry.count(), Integer::sum);
            }
        }
        int cursor = Math.floorMod(offset, total);
        for (int i = 0; i < tail; i++) {
            counts.merge(typeAt(pattern, (cursor + i) % total), 1, Integer::sum);
        }
        return counts;
    }

    /** 模式展开序列上第 {@code index} 发的弹种。 */
    private static ResourceLocation typeAt(List<PatternEntry> pattern, int index) {
        int cursor = 0;
        for (PatternEntry entry : pattern) {
            cursor += entry.count();
            if (index < cursor) return entry.ammo();
        }
        // 索引由 patternTotal 取模得到，走到这里说明模式在遍历中被改动
        throw new IllegalStateException("弹药模式索引越界: " + index);
    }

    // ==================== 回充 ====================

    /**
     * 推进一 tick 自身回充。
     * <p>
     * 延迟计时优先；延迟结束后按 {@code regen_per_minute} 累积进度，每满一发先向持有者取电，
     * 取不到就停在这一格（进度保留，下一 tick 重试）。补到 {@code length} 即停止。
     *
     * @param payEnergy 取电回调：参数为这一发的能源费用，返回是否已付清
     */
    public void tickRecharge(Predicate<Float> payEnergy) {
        if (recharge == null) return;
        if (remaining >= length) {
            rechargeProgress = 0f;
            return;
        }
        if (rechargeDelayTicks > 0) {
            rechargeDelayTicks--;
            return;
        }
        float perMinute = recharge.regenPerMinute();
        if (perMinute <= 0f) return;
        rechargeProgress += perMinute / 1200f;
        while (rechargeProgress >= 1f && remaining < length) {
            float cost = recharge.energyCostPerRound();
            if (cost > 0f && !payEnergy.test(cost)) break;
            remaining++;
            rechargeProgress -= 1f;
        }
        if (remaining >= length) rechargeProgress = 0f;
    }

    /** 成功消费本 Belt 额度后重置回充延迟；未消费额度时不调用。 */
    public void onShot() {
        if (recharge == null) return;
        rechargeDelayTicks = (int) Math.ceil(recharge.regenDelay() * 20f);
    }

    // ==================== 复制 ====================

    /** 满额模板副本：余量等于长度、回充运行态归零。 */
    public AmmoBelt freshCopy() {
        return freshCopy(length);
    }

    /**
     * 按指定余量取模板副本：内容与配置同本实例，回充运行态归零。
     * <p>
     * 源持有不足一份库存时用它交付。交付实例的 {@link #length} 仍是模板长度——规格不变，
     * 消费者才不会把一次缩水的交付判成换装；变化只在 {@link #remaining} 上，按"这条弹链已经打掉
     * {@code length - remaining} 发"解释，取弹位置随之从模式中途开始。
     *
     * @param remaining 交付后的余量，落在 {@code [0, length]} 内
     */
    public AmmoBelt freshCopy(int remaining) {
        return new AmmoBelt(containerId, label, pattern, length, remaining, recharge);
    }

    /** 当前状态副本，含回充运行态。 */
    public AmmoBelt copy() {
        AmmoBelt copy = new AmmoBelt(containerId, label, pattern, length, remaining, recharge);
        copy.rechargeDelayTicks = rechargeDelayTicks;
        copy.rechargeProgress = rechargeProgress;
        return copy;
    }

    /**
     * 同步快照：回充零碎进度按 1/20 取整，避免进度每 tick 小幅变化都触发一次同步广播。
     */
    public AmmoBelt syncCopy() {
        AmmoBelt copy = copy();
        copy.rechargeProgress = Math.round(rechargeProgress * 20f) / 20f;
        return copy;
    }

    /**
     * 交付规格的全序：先比模式条目（弹种、数量），再比长度、标签与是否回充。<br>
     * 只读内容与配置，不含余量与回充运行态，因此同一份规格在满额模板上恒等。
     */
    public static int compareSpec(AmmoBelt left, AmmoBelt right) {
        int shared = Math.min(left.pattern.size(), right.pattern.size());
        for (int i = 0; i < shared; i++) {
            PatternEntry a = left.pattern.get(i);
            PatternEntry b = right.pattern.get(i);
            int order = a.ammo().toString().compareTo(b.ammo().toString());
            if (order != 0) return order;
            order = Integer.compare(a.count(), b.count());
            if (order != 0) return order;
        }
        int order = Integer.compare(left.pattern.size(), right.pattern.size());
        if (order != 0) return order;
        order = Integer.compare(left.length, right.length);
        if (order != 0) return order;
        order = left.label.compareTo(right.label);
        if (order != 0) return order;
        return Boolean.compare(left.recharge != null, right.recharge != null);
    }

    /**
     * 弹链显示名：{@link #label()} 非空时按字面返回，否则取首发弹种的语言键文本。
     * <p>
     * <b>仅在客户端调用</b>：内容包的翻译键只在客户端语言表里。
     */
    public String displayLabel() {
        if (!label.isEmpty()) return label;
        if (pattern.isEmpty()) return "---";
        return Component.translatable(pattern.getFirst().ammo().toLanguageKey()).getString();
    }

    // ==================== 编解码边界 ====================

    /**
     * 结构校验：字段位置写进错误说明，调用方（内容包加载、网络解码、存档恢复）据此定位。
     *
     * @return 合法时返回成功；否则返回带字段名的错误
     */
    public static DataResult<AmmoBelt> validate(AmmoBelt belt) {
        String error = check(belt);
        return error == null ? DataResult.success(belt) : DataResult.error(() -> error);
    }

    @Nullable
    public static String check(AmmoBelt belt) {
        if (belt.length < 0) return "length 必须非负，实际为 " + belt.length;
        if (belt.remaining < 0 || belt.remaining > belt.length) {
            return "remaining 必须落在 [0, " + belt.length + "] 内，实际为 " + belt.remaining;
        }
        if (belt.pattern.size() > MAX_PATTERN_ENTRIES) {
            return "pattern 条目数不得超过 " + MAX_PATTERN_ENTRIES + "，实际为 " + belt.pattern.size();
        }
        long total = 0L;
        for (int i = 0; i < belt.pattern.size(); i++) {
            PatternEntry entry = belt.pattern.get(i);
            if (entry.count() <= 0) {
                return "pattern[" + i + "].count 必须为正，实际为 " + entry.count();
            }
            total += entry.count();
            if (total > Integer.MAX_VALUE) return "pattern 总发数溢出 int，模式项数 " + belt.pattern.size();
        }
        if (belt.pattern.isEmpty()) {
            if (belt.remaining != 0) return "空 pattern 只允许 remaining 为 0 的实例，实际为 " + belt.remaining;
            if (belt.recharge != null) return "可回充实例必须带有效 pattern";
        }
        if (belt.recharge != null) {
            if (!Float.isFinite(belt.recharge.regenPerMinute()) || belt.recharge.regenPerMinute() < 0f) {
                return "recharge.regen_per_minute 必须为非负有限数，实际为 " + belt.recharge.regenPerMinute();
            }
            if (!Float.isFinite(belt.recharge.regenDelay()) || belt.recharge.regenDelay() < 0f) {
                return "recharge.regen_delay 必须为非负有限数，实际为 " + belt.recharge.regenDelay();
            }
            if (!Float.isFinite(belt.recharge.energyCostPerRound()) || belt.recharge.energyCostPerRound() < 0f) {
                return "recharge.energy_cost_per_round 必须为非负有限数，实际为 " + belt.recharge.energyCostPerRound();
            }
        }
        return null;
    }

    private static void write(ByteBuf buf, AmmoBelt belt) {
        buf.writeBoolean(belt.containerId != null);
        if (belt.containerId != null) ResourceLocation.STREAM_CODEC.encode(buf, belt.containerId);
        ByteBufCodecs.STRING_UTF8.encode(buf, belt.label);
        ByteBufCodecs.VAR_INT.encode(buf, belt.pattern.size());
        for (PatternEntry entry : belt.pattern) {
            ResourceLocation.STREAM_CODEC.encode(buf, entry.ammo());
            ByteBufCodecs.VAR_INT.encode(buf, entry.count());
        }
        ByteBufCodecs.VAR_INT.encode(buf, belt.length);
        ByteBufCodecs.VAR_INT.encode(buf, belt.remaining);
        buf.writeBoolean(belt.recharge != null);
        if (belt.recharge != null) {
            buf.writeFloat(belt.recharge.regenPerMinute());
            buf.writeFloat(belt.recharge.regenDelay());
            buf.writeFloat(belt.recharge.energyCostPerRound());
        }
        ByteBufCodecs.VAR_INT.encode(buf, belt.rechargeDelayTicks);
        buf.writeFloat(belt.rechargeProgress);
    }

    private static AmmoBelt read(ByteBuf buf) {
        ResourceLocation containerId = buf.readBoolean() ? ResourceLocation.STREAM_CODEC.decode(buf) : null;
        String label = ByteBufCodecs.STRING_UTF8.decode(buf);
        int size = ByteBufCodecs.VAR_INT.decode(buf);
        if (size < 0 || size > MAX_PATTERN_ENTRIES) {
            throw new DecoderException("AmmoBelt.pattern 条目数超出边界: " + size);
        }
        List<PatternEntry> pattern = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            pattern.add(new PatternEntry(ResourceLocation.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));
        }
        int length = ByteBufCodecs.VAR_INT.decode(buf);
        int remaining = ByteBufCodecs.VAR_INT.decode(buf);
        Recharge recharge = null;
        if (buf.readBoolean()) {
            recharge = new Recharge(buf.readFloat(), buf.readFloat(), buf.readFloat());
        }
        AmmoBelt belt = new AmmoBelt(containerId, label, pattern, length, remaining, recharge);
        belt.rechargeDelayTicks = ByteBufCodecs.VAR_INT.decode(buf);
        belt.rechargeProgress = buf.readFloat();
        String error = check(belt);
        if (error != null) throw new DecoderException("AmmoBelt 解码失败: " + error);
        return belt;
    }

    // ==================== NBT ====================

    /** 存档形状：JSON 编解码的内容，外加只有存档需要的回充运行态。 */
    public CompoundTag save() {
        CompoundTag tag = (CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
        tag.putInt("recharge_delay_ticks", rechargeDelayTicks);
        tag.putFloat("recharge_progress", rechargeProgress);
        return tag;
    }

    /**
     * 从存档形状恢复。
     *
     * @return 恢复的 Belt；数据不合法或内容无法解析时返回 null，调用方按"没有已装数据"处理
     */
    @Nullable
    public static AmmoBelt load(CompoundTag tag) {
        return CODEC.parse(NbtOps.INSTANCE, tag)
                .resultOrPartial(error -> MachineMax.LOGGER.error("AmmoBelt 存档解码失败: {}", error))
                .map(belt -> {
                    belt.rechargeDelayTicks = tag.getInt("recharge_delay_ticks");
                    belt.rechargeProgress = tag.getFloat("recharge_progress");
                    return belt;
                })
                .orElse(null);
    }

    /**
     * 解析弹种引用。交付校验与发射共用的唯一解析入口。
     *
     * @return 对应投射物类型；尚未注册时返回 null
     */
    @Nullable
    public static ProjectileType resolve(net.minecraft.world.level.Level level, @Nullable ResourceLocation id) {
        return id == null ? null : ProjectileType.get(level, id);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AmmoBelt belt)) return false;
        return length == belt.length
                && remaining == belt.remaining
                && rechargeDelayTicks == belt.rechargeDelayTicks
                && Float.compare(rechargeProgress, belt.rechargeProgress) == 0
                && java.util.Objects.equals(containerId, belt.containerId)
                && label.equals(belt.label)
                && pattern.equals(belt.pattern)
                && java.util.Objects.equals(recharge, belt.recharge);
    }

    @Override
    public int hashCode() {
        int result = java.util.Objects.hash(containerId, label, pattern, length, remaining, recharge);
        result = 31 * result + rechargeDelayTicks;
        result = 31 * result + Float.hashCode(rechargeProgress);
        return result;
    }

    @Override
    public String toString() {
        return "AmmoBelt[" + (containerId == null ? "virtual" : containerId) + " '" + label + "' "
                + remaining + "/" + length + " " + pattern + (recharge == null ? "" : " recharge") + "]";
    }
}
