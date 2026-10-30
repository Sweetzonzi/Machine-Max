package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module.FeedModuleAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module.MagazineModuleAttr;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 再生装弹机子系统静态属性。<br>
 * 用于能量武器（激光、爆能束）和街机风格再生武器（导弹冷却再生）。<br>
 * 统一了从 EnergyGrid 消耗电能生成弹药和纯冷却时间驱动的免费再生两种模式。<br>
 * 能量与再生的区别仅在于 {@code energy_cost_per_round} 是否为 0。
 */
@Getter
public class RegenLoaderSubsystemStaticAttr extends BasicSubsystemStaticAttr {

    /**
     * 再生装弹机音效属性 — 包含基础音效和进度分段装填音效。
     * <p>
     * 仿 {@code LauncherSubsystemStaticAttr.LauncherSoundAttr} 的组织模式。
     * </p>
     *
     * @param basicSounds    基础音效（onDestroyed / onActivated / onDeactivated）
     * @param progressSounds 装填进度分段音效映射（key=进度浮点字符串如"0.0","0.25","0.5","0.75","1.0"，value=音效）
     */
    public record RegenLoaderSoundAttr(
        BasicSoundAttr basicSounds,
        Map<String, SoundEvent> progressSounds
    ) {
        public static final RegenLoaderSoundAttr DEFAULT = new RegenLoaderSoundAttr(
            BasicSoundAttr.DEFAULT,
            Map.of()
        );

        public static final Codec<RegenLoaderSoundAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BasicSoundAttr.basicSounds(RegenLoaderSoundAttr::basicSounds),
            Codec.unboundedMap(Codec.STRING, SoundEvent.DIRECT_CODEC)
                .optionalFieldOf("progress_sounds", Map.of())
                .forGetter(RegenLoaderSoundAttr::progressSounds)
        ).apply(instance, RegenLoaderSoundAttr::new));
    }

    /** 弹仓容量 */
    private final int magazineCapacity;

    /**
     * 每分钟再生数量。<br>
     * {@code regenRoundByRound=true} 时为弹药恢复速率（发/分钟）；<br>
     * {@code regenRoundByRound=false} 时为完整装填次数（次/分钟）。<br>
     * 0 = 无自动再生（静态弹仓）。
     */
    private final float regenPerMinute;

    /**
     * 再生模式。<br>
     * {@code true}=随打随产（每消耗一发即开始再生下一发）；<br>
     * {@code false}=打空后批量回满（弹仓全空后才触发再生）。
     */
    private final boolean regenRoundByRound;

    /** 每发消耗的能量值（0 = 免费再生） */
    private final float energyCostPerRound;

    /** 供给一发弹药的耗时（秒）。再生模式下为弹药诞生到可用的延迟 */
    private final float reloadTime;

    /** 是否允许多消费者并行 */
    private final boolean canSupplyMultiple;

    /**
     * 再生启动延迟（秒）。<br>
     * 上次输送弹药给消费者后，经过此延迟才开始下一发弹药再生。<br>
     * 0 = 边产边供，无延迟。
     */
    private final float regenDelay;

    /** 再生装弹机专属音效属性 */
    private final RegenLoaderSoundAttr regenLoaderSoundAttr;

    // ====== 模块字段 ======

    /** 输弹机构模块（必填，有默认值），非关键 */
    private final FeedModuleAttr feed;
    /** 弹仓模块（必填，有默认值），唯一关键模块 */
    private final MagazineModuleAttr magazine;

    public static final MapCodec<RegenLoaderSubsystemStaticAttr> CODEC =
            RecordCodecBuilder.<RegenLoaderSubsystemStaticAttr>mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.INT.fieldOf("magazine_capacity")
                .forGetter(RegenLoaderSubsystemStaticAttr::getMagazineCapacity),
            Codec.FLOAT.optionalFieldOf("regen_per_minute", 0f)
                .forGetter(RegenLoaderSubsystemStaticAttr::getRegenPerMinute),
            Codec.BOOL.optionalFieldOf("regen_round_by_round", true)
                .forGetter(RegenLoaderSubsystemStaticAttr::isRegenRoundByRound),
            Codec.FLOAT.optionalFieldOf("energy_cost_per_round", 0f)
                .forGetter(RegenLoaderSubsystemStaticAttr::getEnergyCostPerRound),
            Codec.FLOAT.fieldOf("reload_time")
                .forGetter(RegenLoaderSubsystemStaticAttr::getReloadTime),
            Codec.BOOL.optionalFieldOf("can_supply_multiple", false)
                .forGetter(RegenLoaderSubsystemStaticAttr::isCanSupplyMultiple),
            Codec.FLOAT.optionalFieldOf("regen_delay", 0f)
                .forGetter(RegenLoaderSubsystemStaticAttr::getRegenDelay),
            RegenLoaderSoundAttr.CODEC.optionalFieldOf("sounds", RegenLoaderSoundAttr.DEFAULT)
                .forGetter(RegenLoaderSubsystemStaticAttr::getRegenLoaderSounds),
            FeedModuleAttr.CODEC.codec().optionalFieldOf("feed", FeedModuleAttr.DEFAULT)
                .forGetter(RegenLoaderSubsystemStaticAttr::getFeed),
            MagazineModuleAttr.CODEC.codec().optionalFieldOf("magazine", MagazineModuleAttr.DEFAULT)
                .forGetter(RegenLoaderSubsystemStaticAttr::getMagazine)
    ).apply(instance, RegenLoaderSubsystemStaticAttr::new))
            .validate(RegenLoaderSubsystemStaticAttr::validateAttributes);

    public RegenLoaderSubsystemStaticAttr(
            BasicAttr basicAttr,
            int magazineCapacity,
            float regenPerMinute,
            boolean regenRoundByRound,
            float energyCostPerRound,
            float reloadTime,
            boolean canSupplyMultiple,
            float regenDelay,
            RegenLoaderSoundAttr sounds,
            FeedModuleAttr feed,
            MagazineModuleAttr magazine) {
        super(basicAttr, sounds.basicSounds());
        this.regenLoaderSoundAttr = sounds;
        this.magazineCapacity = magazineCapacity;
        this.regenPerMinute = regenPerMinute;
        this.regenRoundByRound = regenRoundByRound;
        this.energyCostPerRound = energyCostPerRound;
        this.reloadTime = reloadTime;
        this.canSupplyMultiple = canSupplyMultiple;
        this.regenDelay = regenDelay;
        this.feed = feed;
        this.magazine = magazine;
    }

    /**
     * 装弹机边界上的数值校验：{@code basic_durability} 与两个模块权重共同决定模块最大耐久
     * （{@code basic_durability × 权重 / 权重合计}），非有限值或非正值会让分配失去物理含义。
     * <p>
     * 校验结果以 {@link DataResult} 错误返回，由内容包加载器按文件隔离成一条带字段名的 ERROR；
     * 直接构造本属性的代码不受这条约束限制，模块分配仍会被 {@code SubModule} 钳位。
     * {@code BasicAttr} 的共享编解码器不在这里改动。
     * </p>
     */
    private static DataResult<RegenLoaderSubsystemStaticAttr> validateAttributes(
            RegenLoaderSubsystemStaticAttr attr) {
        float durability = attr.basicAttr.basicDurability();
        if (!Float.isFinite(durability) || durability <= 0f) {
            return DataResult.error(() -> "basic_durability 必须为有限正数，实际为 " + durability);
        }
        String feedError = weightError("feed", attr.feed.durabilityWeight());
        if (feedError != null) return DataResult.error(() -> feedError);
        String magazineError = weightError("magazine", attr.magazine.durabilityWeight());
        if (magazineError != null) return DataResult.error(() -> magazineError);
        // 两个权重各自为正仍可能把合计推到正无穷，那会让模块最大耐久全部塌成 0
        float totalWeight = attr.feed.durabilityWeight() + attr.magazine.durabilityWeight();
        if (!Float.isFinite(totalWeight)) {
            return DataResult.error(
                    () -> "feed 与 magazine 的 durability_weight 合计必须为有限数，实际为 " + totalWeight);
        }
        return DataResult.success(attr);
    }

    /**
     * 单个模块权重的错误说明。
     *
     * @return 合法时返回 {@code null}，否则返回带模块名与字段名的说明
     */
    @Nullable
    private static String weightError(String module, float weight) {
        if (!Float.isFinite(weight))
            return module + ".durability_weight 必须是有限数，实际为 " + weight;
        if (weight <= 0f)
            return module + ".durability_weight 必须为正数，实际为 " + weight;
        return null;
    }

    /** 获取再生装弹机完整音效属性 */
    public RegenLoaderSoundAttr getRegenLoaderSounds() {
        return regenLoaderSoundAttr;
    }

    /** 获取装填进度分段音效映射（key=进度浮点字符串如"0.0"~"1.0"） */
    public Map<String, SoundEvent> getProgressSounds() {
        return regenLoaderSoundAttr.progressSounds();
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.REGEN_LOADER;
    }
}
