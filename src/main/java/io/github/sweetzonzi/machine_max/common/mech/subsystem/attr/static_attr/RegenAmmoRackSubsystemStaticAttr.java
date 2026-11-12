package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * 再生弹药架子系统静态属性。<br>
 * 只描述库存本身：存量上限与自身再生参数。输弹机构、装填耗时与进度音效属于
 * {@code ammo_loader} 型号，不在这里。
 * <p>
 * 两种再生模式：{@code regen_round_by_round=true} 随打随产（每产出一发付
 * {@code regen_per_minute} 折算的时间），{@code false} 打空后整批回满。能量与冷却两种模式的差别只在
 * {@code energy_cost_per_round} 是否为零。
 * </p>
 */
@Getter
public class RegenAmmoRackSubsystemStaticAttr extends BasicSubsystemStaticAttr {

    /** 存量上限（必填） */
    private final int capacity;

    /** 每分钟产出数量，0 表示不自行再生 */
    private final float regenPerMinute;

    /** 逐发产出（true）还是打空后整批回满（false） */
    private final boolean regenRoundByRound;

    /** 每产出一发的取电，0 表示不取电 */
    private final float energyCostPerRound;

    /** 取走一发后暂停再生的秒数 */
    private final float regenDelay;

    public static final MapCodec<RegenAmmoRackSubsystemStaticAttr> CODEC =
            RecordCodecBuilder.<RegenAmmoRackSubsystemStaticAttr>mapCodec(instance -> instance.group(
                    BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
                    Codec.INT.fieldOf("capacity")
                            .forGetter(RegenAmmoRackSubsystemStaticAttr::getCapacity),
                    Codec.FLOAT.optionalFieldOf("regen_per_minute", 0f)
                            .forGetter(RegenAmmoRackSubsystemStaticAttr::getRegenPerMinute),
                    Codec.BOOL.optionalFieldOf("regen_round_by_round", true)
                            .forGetter(RegenAmmoRackSubsystemStaticAttr::isRegenRoundByRound),
                    Codec.FLOAT.optionalFieldOf("energy_cost_per_round", 0f)
                            .forGetter(RegenAmmoRackSubsystemStaticAttr::getEnergyCostPerRound),
                    Codec.FLOAT.optionalFieldOf("regen_delay", 0f)
                            .forGetter(RegenAmmoRackSubsystemStaticAttr::getRegenDelay),
                    BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT)
                            .forGetter(BasicSubsystemStaticAttr::getSoundAttr)
            ).apply(instance, RegenAmmoRackSubsystemStaticAttr::new))
                    .validate(RegenAmmoRackSubsystemStaticAttr::validateAttributes);

    public RegenAmmoRackSubsystemStaticAttr(
            BasicAttr basicAttr,
            int capacity,
            float regenPerMinute,
            boolean regenRoundByRound,
            float energyCostPerRound,
            float regenDelay,
            BasicSoundAttr sounds) {
        super(basicAttr, sounds);
        this.capacity = capacity;
        this.regenPerMinute = regenPerMinute;
        this.regenRoundByRound = regenRoundByRound;
        this.energyCostPerRound = energyCostPerRound;
        this.regenDelay = regenDelay;
    }

    /**
     * 边界校验：存量上限至少为 1（否则永远产不出可交付的一份），再生速率、取电与延迟为非负有限数
     * （负值会让计时器倒退或永远走不完）。错误带字段名，由内容包加载器按文件隔离成一条 ERROR。
     */
    private static DataResult<RegenAmmoRackSubsystemStaticAttr> validateAttributes(
            RegenAmmoRackSubsystemStaticAttr attr) {
        float durability = attr.basicAttr.basicDurability();
        if (!Float.isFinite(durability) || durability <= 0f) {
            return DataResult.error(() -> "basic_durability 必须为有限正数，实际为 " + durability);
        }
        if (attr.capacity < 1) {
            return DataResult.error(() -> "capacity 必须至少为 1，实际为 " + attr.capacity);
        }
        String rateError = nonNegativeError("regen_per_minute", attr.regenPerMinute);
        if (rateError != null) return DataResult.error(() -> rateError);
        String energyError = nonNegativeError("energy_cost_per_round", attr.energyCostPerRound);
        if (energyError != null) return DataResult.error(() -> energyError);
        String delayError = nonNegativeError("regen_delay", attr.regenDelay);
        if (delayError != null) return DataResult.error(() -> delayError);
        return DataResult.success(attr);
    }

    /**
     * 非负有限数的错误说明。
     *
     * @return 合法时返回 {@code null}，否则返回带字段名与实际值的说明
     */
    @Nullable
    private static String nonNegativeError(String field, float value) {
        if (!Float.isFinite(value)) return field + " 必须是有限数，实际为 " + value;
        if (value < 0f) return field + " 不能为负数，实际为 " + value;
        return null;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.REGEN_AMMO_RACK;
    }
}
