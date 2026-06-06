package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

/**
 * 再生装弹机子系统静态属性。<br>
 * 用于能量武器（激光、爆能束）和街机风格再生武器（导弹冷却再生）。<br>
 * 统一了从 EnergyGrid 消耗电能生成弹药和纯冷却时间驱动的免费再生两种模式。<br>
 * 能量与再生的区别仅在于 {@code energy_cost_per_round} 是否为 0。
 */
@Getter
public class RegenLoaderSubsystemStaticAttr extends BasicSubsystemStaticAttr {

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

    /** 生成的投射物类型 ID */
    private final ResourceLocation projectileType;

    /** 供给一发弹药的耗时（tick）。再生模式下为弹药诞生到可用的延迟 */
    private final int reloadTimeTicks;

    /** 是否允许多消费者并行 */
    private final boolean canSupplyMultiple;

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
            ResourceLocation.CODEC.fieldOf("projectile_type")
                .forGetter(RegenLoaderSubsystemStaticAttr::getProjectileType),
            Codec.INT.fieldOf("reload_time_ticks")
                .forGetter(RegenLoaderSubsystemStaticAttr::getReloadTimeTicks),
            Codec.BOOL.optionalFieldOf("can_supply_multiple", false)
                .forGetter(RegenLoaderSubsystemStaticAttr::isCanSupplyMultiple),
            BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT)
                .forGetter(BasicSubsystemStaticAttr::getSoundAttr)
    ).apply(instance, RegenLoaderSubsystemStaticAttr::new));

    public RegenLoaderSubsystemStaticAttr(
            BasicAttr basicAttr,
            int magazineCapacity,
            float regenPerMinute,
            boolean regenRoundByRound,
            float energyCostPerRound,
            ResourceLocation projectileType,
            int reloadTimeTicks,
            boolean canSupplyMultiple,
            BasicSoundAttr sounds) {
        super(basicAttr, sounds);
        this.magazineCapacity = magazineCapacity;
        this.regenPerMinute = regenPerMinute;
        this.regenRoundByRound = regenRoundByRound;
        this.energyCostPerRound = energyCostPerRound;
        this.projectileType = projectileType;
        this.reloadTimeTicks = reloadTimeTicks;
        this.canSupplyMultiple = canSupplyMultiple;
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
