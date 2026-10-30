package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractModuleAttr;

/**
 * 弹仓模块属性——承载「存多少、怎么长回来」。
 * <p>
 * 关键模块（{@code critical=true} 硬编码）：耐久归零即整机摧毁，停止存入、再生与供给；
 * 子系统摧毁边沿按库存贡献决定是否殉爆。恢复沿关键模块规则，需要回到 30% 以上并完成恢复锁存解除。
 * </p>
 * <p>
 * 字段含义见 {@code capacity} / {@code regenPerMinute} / {@code regenRoundByRound} /
 * {@code energyCostPerRound} / {@code regenDelay}：容量上限、产出速率、回充粒度、每发取电与取走后的暂停。
 * 输弹耗时与独占与否属于输弹机构模块；弹链属于部件实例配置。
 * </p>
 */
public class MagazineModuleAttr extends AbstractModuleAttr {

    public static final MapCodec<MagazineModuleAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("durability_weight", 1.0f).forGetter(AbstractModuleAttr::durabilityWeight),
            Codec.INT.fieldOf("capacity").forGetter(MagazineModuleAttr::capacity),
            Codec.FLOAT.optionalFieldOf("regen_per_minute", 0f).forGetter(MagazineModuleAttr::regenPerMinute),
            Codec.BOOL.optionalFieldOf("regen_round_by_round", true).forGetter(MagazineModuleAttr::regenRoundByRound),
            Codec.FLOAT.optionalFieldOf("energy_cost_per_round", 0f).forGetter(MagazineModuleAttr::energyCostPerRound),
            Codec.FLOAT.optionalFieldOf("regen_delay", 0f).forGetter(MagazineModuleAttr::regenDelay)
    ).apply(instance, MagazineModuleAttr::new));

    /** 存量上限（发） */
    private final int capacity;

    /**
     * 每分钟产出数量。<br>
     * {@code regenRoundByRound=true} 时为逐发回充速率（发/分钟）；
     * {@code false} 时为整批回充速率（整批时长 = 容量 ÷ 速率）。
     * 0 = 无自动回充（静态弹仓）。
     */
    private final float regenPerMinute;

    /** {@code true}=随打随产；{@code false}=打空后整批回满 */
    private final boolean regenRoundByRound;

    /** 每产出一发消耗的能量（0 = 免费回充）。整批回充在批次开始时按满容量一次性扣除 */
    private final float energyCostPerRound;

    /** 取走一发后暂停产出的时间（秒）。0 = 边产边供 */
    private final float regenDelay;

    public MagazineModuleAttr(float durabilityWeight, int capacity, float regenPerMinute,
                              boolean regenRoundByRound, float energyCostPerRound, float regenDelay) {
        super(durabilityWeight, true); // 弹仓关键，硬编码
        this.capacity = capacity;
        this.regenPerMinute = regenPerMinute;
        this.regenRoundByRound = regenRoundByRound;
        this.energyCostPerRound = energyCostPerRound;
        this.regenDelay = regenDelay;
    }

    public int capacity() {
        return capacity;
    }

    public float regenPerMinute() {
        return regenPerMinute;
    }

    public boolean regenRoundByRound() {
        return regenRoundByRound;
    }

    public float energyCostPerRound() {
        return energyCostPerRound;
    }

    public float regenDelay() {
        return regenDelay;
    }
}
