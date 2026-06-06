package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import lombok.Getter;

/**
 * 装弹机子系统静态属性。<br>
 * 定义装填模式（逐发/整体换弹匣）、弹药容量、装填耗时、多消费者支持等硬件参数。<br>
 * 弹药请求与消耗通信已改为 {@code IAmmoSupplier/IAmmoConsumer} 直接接口调用，不再走信号频道。
 */
@Getter
public class AmmoLoaderSubsystemStaticAttr extends BasicSubsystemStaticAttr {

    /**
     * 装填是否为逐发模式。<br>
     * {@code true}=逐发压入（火炮、霰弹枪），此时 {@link #reloadTimeTicks} 表示装填一发耗时；<br>
     * {@code false}=整体换弹匣（机炮），此时 {@link #reloadTimeTicks} 表示换弹匣总耗时。
     */
    private final boolean roundByRound;

    /** 弹匣/弹仓容量 */
    private final int magazineCapacity;

    /**
     * 装填耗时（tick）。<br>
     * {@code roundByRound=false} 时表示整体换弹匣耗时；<br>
     * {@code roundByRound=true} 时表示装填一发耗时。
     */
    private final int reloadTimeTicks;

    /** 是否允许多个消费者同时等待装填 */
    private final boolean canSupplyMultiple;

    /** 弹仓未满时是否自动向上游供给者请求补充 */
    private final boolean autoRequestUpstream;

    public static final MapCodec<AmmoLoaderSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.BOOL.optionalFieldOf("round_by_round", false)
                .forGetter(AmmoLoaderSubsystemStaticAttr::isRoundByRound),
            Codec.INT.optionalFieldOf("magazine_capacity", 30)
                .forGetter(AmmoLoaderSubsystemStaticAttr::getMagazineCapacity),
            Codec.INT.optionalFieldOf("reload_time_ticks", 200)
                .forGetter(AmmoLoaderSubsystemStaticAttr::getReloadTimeTicks),
            Codec.BOOL.optionalFieldOf("can_supply_multiple", false)
                .forGetter(AmmoLoaderSubsystemStaticAttr::isCanSupplyMultiple),
            Codec.BOOL.optionalFieldOf("auto_request_upstream", true)
                .forGetter(AmmoLoaderSubsystemStaticAttr::isAutoRequestUpstream),
            BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT)
                .forGetter(BasicSubsystemStaticAttr::getSoundAttr)
    ).apply(instance, AmmoLoaderSubsystemStaticAttr::new));

    public AmmoLoaderSubsystemStaticAttr(
            BasicAttr basicAttr,
            boolean roundByRound,
            int magazineCapacity,
            int reloadTimeTicks,
            boolean canSupplyMultiple,
            boolean autoRequestUpstream,
            BasicSoundAttr sounds) {
        super(basicAttr, sounds);
        this.roundByRound = roundByRound;
        this.magazineCapacity = magazineCapacity;
        this.reloadTimeTicks = reloadTimeTicks;
        this.canSupplyMultiple = canSupplyMultiple;
        this.autoRequestUpstream = autoRequestUpstream;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.AMMO_LOADER;
    }
}
