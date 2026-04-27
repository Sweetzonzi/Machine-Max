package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import lombok.Getter;

@Getter
public class BatterySubsystemStaticAttr extends BasicSubsystemStaticAttr {
    /** 最大储能 (J) */
    public final float maxStoredEnergy;
    /** 最大充电功率 (W) */
    public final float maxChargeRate;
    /** 最大放电功率 (W) */
    public final float maxDischargeRate;

    public static final MapCodec<BatterySubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.FLOAT.optionalFieldOf("max_stored_energy", 50000f).forGetter(BatterySubsystemStaticAttr::getMaxStoredEnergy),
            Codec.FLOAT.optionalFieldOf("max_charge_rate", 1000f).forGetter(BatterySubsystemStaticAttr::getMaxChargeRate),
            Codec.FLOAT.optionalFieldOf("max_discharge_rate", 2000f).forGetter(BatterySubsystemStaticAttr::getMaxDischargeRate),
            BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT).forGetter(BasicSubsystemStaticAttr::getSoundAttr)
    ).apply(instance, BatterySubsystemStaticAttr::new));

    protected BatterySubsystemStaticAttr(
            BasicAttr basicAttr,
            float maxStoredEnergy,
            float maxChargeRate,
            float maxDischargeRate,
            BasicSoundAttr sounds) {
        super(basicAttr, sounds);
        this.maxStoredEnergy = maxStoredEnergy;
        this.maxChargeRate = maxChargeRate;
        this.maxDischargeRate = maxDischargeRate;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.BATTERY;
    }
}
