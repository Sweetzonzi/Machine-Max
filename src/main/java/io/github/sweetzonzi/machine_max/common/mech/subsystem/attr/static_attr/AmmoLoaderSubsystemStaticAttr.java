package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import lombok.Getter;

/**
 * 装弹机子系统静态属性。<br>
 * 定义装填模式（逐发/整体换弹匣）、弹药容量、装填耗时、与发射器及武器控制器的信号频道名称。<br>
 * TODO: 弹药tag过滤在 LauncherSubsystemStaticAttr 中定义，装弹机侧读取弹药 item 的 tag 进行匹配
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

    /** 向发射器请求弹药的信号频道名称 */
    private final String ammoRequestChannel;

    /** 发射器响应的弹药信号频道名称 */
    private final String ammoResponseChannel;

    /** 发射器告知已消耗的信号频道名称 */
    private final String ammoConsumedChannel;

    /** 玩家/武器控制器触发主动换弹的信号频道名称 */
    private final String reloadChannel;

    public static final MapCodec<AmmoLoaderSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.BOOL.optionalFieldOf("round_by_round", false)
                .forGetter(AmmoLoaderSubsystemStaticAttr::isRoundByRound),
            Codec.INT.optionalFieldOf("magazine_capacity", 30)
                .forGetter(AmmoLoaderSubsystemStaticAttr::getMagazineCapacity),
            Codec.INT.optionalFieldOf("reload_time_ticks", 200)
                .forGetter(AmmoLoaderSubsystemStaticAttr::getReloadTimeTicks),
            Codec.STRING.optionalFieldOf("ammo_request_channel", "ammo_request")
                .forGetter(AmmoLoaderSubsystemStaticAttr::getAmmoRequestChannel),
            Codec.STRING.optionalFieldOf("ammo_response_channel", "ammo_response")
                .forGetter(AmmoLoaderSubsystemStaticAttr::getAmmoResponseChannel),
            Codec.STRING.optionalFieldOf("ammo_consumed_channel", "ammo_consumed")
                .forGetter(AmmoLoaderSubsystemStaticAttr::getAmmoConsumedChannel),
            Codec.STRING.optionalFieldOf("reload_channel", "reload")
                .forGetter(AmmoLoaderSubsystemStaticAttr::getReloadChannel),
            BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT)
                .forGetter(BasicSubsystemStaticAttr::getSoundAttr)
    ).apply(instance, AmmoLoaderSubsystemStaticAttr::new));

    public AmmoLoaderSubsystemStaticAttr(
            BasicAttr basicAttr,
            boolean roundByRound,
            int magazineCapacity,
            int reloadTimeTicks,
            String ammoRequestChannel,
            String ammoResponseChannel,
            String ammoConsumedChannel,
            String reloadChannel,
            BasicSoundAttr sounds) {
        super(basicAttr, sounds);
        this.roundByRound = roundByRound;
        this.magazineCapacity = magazineCapacity;
        this.reloadTimeTicks = reloadTimeTicks;
        this.ammoRequestChannel = ammoRequestChannel;
        this.ammoResponseChannel = ammoResponseChannel;
        this.ammoConsumedChannel = ammoConsumedChannel;
        this.reloadChannel = reloadChannel;
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
