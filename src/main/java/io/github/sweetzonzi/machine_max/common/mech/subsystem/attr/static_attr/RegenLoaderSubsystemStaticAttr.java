package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

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

    public static final MapCodec<RegenLoaderSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
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
                .forGetter(RegenLoaderSubsystemStaticAttr::getRegenLoaderSounds)
    ).apply(instance, RegenLoaderSubsystemStaticAttr::new));

    public RegenLoaderSubsystemStaticAttr(
            BasicAttr basicAttr,
            int magazineCapacity,
            float regenPerMinute,
            boolean regenRoundByRound,
            float energyCostPerRound,
            float reloadTime,
            boolean canSupplyMultiple,
            float regenDelay,
            RegenLoaderSoundAttr sounds) {
        super(basicAttr, sounds.basicSounds());
        this.regenLoaderSoundAttr = sounds;
        this.magazineCapacity = magazineCapacity;
        this.regenPerMinute = regenPerMinute;
        this.regenRoundByRound = regenRoundByRound;
        this.energyCostPerRound = energyCostPerRound;
        this.reloadTime = reloadTime;
        this.canSupplyMultiple = canSupplyMultiple;
        this.regenDelay = regenDelay;
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
