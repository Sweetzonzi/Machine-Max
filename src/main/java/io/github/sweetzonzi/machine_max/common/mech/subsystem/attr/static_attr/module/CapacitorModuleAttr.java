package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SubModule;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractModuleAttr;

/**
 * 电容模块属性（可选，用于磁轨炮等蓄力武器）。
 * <p>
 * 电容受损 → 蓄力速率下降、最大功率受限。摧毁后完全无法蓄力（非 critical 模块，
 * {@code critical=false} 硬编码）。
 * </p>
 */
public class CapacitorModuleAttr extends AbstractModuleAttr {
    /** 模块级衰减曲线指数 */
    private final float decayPower;
    /** 战损极限蓄力速率乘数 */
    private final float damagedChargeRate;
    /** 摧毁蓄力速率乘数 */
    private final float destroyedChargeRate;
    /** 战损极限最大功率乘数 */
    private final float damagedMaxPower;
    /** 摧毁最大功率乘数 */
    private final float destroyedMaxPower;

    public static final MapCodec<CapacitorModuleAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("durability_weight", 1.0f).forGetter(AbstractModuleAttr::durabilityWeight),
            Codec.FLOAT.optionalFieldOf("decay_power", 2.0f).forGetter(CapacitorModuleAttr::decayPower),
            Codec.FLOAT.optionalFieldOf("damaged_charge_rate", 0.5f).forGetter(CapacitorModuleAttr::damagedChargeRate),
            Codec.FLOAT.optionalFieldOf("destroyed_charge_rate", 0.0f).forGetter(CapacitorModuleAttr::destroyedChargeRate),
            Codec.FLOAT.optionalFieldOf("damaged_max_power", 0.7f).forGetter(CapacitorModuleAttr::damagedMaxPower),
            Codec.FLOAT.optionalFieldOf("destroyed_max_power", 0.0f).forGetter(CapacitorModuleAttr::destroyedMaxPower)
    ).apply(instance, CapacitorModuleAttr::new));

    public CapacitorModuleAttr(float durabilityWeight, float decayPower,
                               float damagedChargeRate, float destroyedChargeRate,
                               float damagedMaxPower, float destroyedMaxPower) {
        super(durabilityWeight, false); // 电容非 critical，硬编码
        this.decayPower = decayPower;
        this.damagedChargeRate = damagedChargeRate;
        this.destroyedChargeRate = destroyedChargeRate;
        this.damagedMaxPower = damagedMaxPower;
        this.destroyedMaxPower = destroyedMaxPower;
    }

    /** 根据耐久比例计算蓄力速率乘数 */
    public float getChargeRate(float durabilityRatio) {
        return SubModule.computeDecayMultiplier(durabilityRatio, damagedChargeRate, destroyedChargeRate, decayPower);
    }

    /** 根据耐久比例计算最大功率乘数 */
    public float getMaxPower(float durabilityRatio) {
        return SubModule.computeDecayMultiplier(durabilityRatio, damagedMaxPower, destroyedMaxPower, decayPower);
    }

    public float decayPower() { return decayPower; }
    public float damagedChargeRate() { return damagedChargeRate; }
    public float destroyedChargeRate() { return destroyedChargeRate; }
    public float damagedMaxPower() { return damagedMaxPower; }
    public float destroyedMaxPower() { return destroyedMaxPower; }
}
