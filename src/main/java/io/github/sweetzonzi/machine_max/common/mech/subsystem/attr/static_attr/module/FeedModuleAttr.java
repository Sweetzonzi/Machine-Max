package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractModuleAttr;

/**
 * 输弹机构模块属性——承载「怎么把弹药送进膛」，不拥有库存。
 * <p>
 * 机构非关键（{@code critical=false} 硬编码）：耐久归零只中断后续装填，
 * 弹仓与已存弹药保留，子系统本身不因此进入摧毁状态。
 * 模块自身的摧毁与恢复仍走通用规则——归零即摧毁，修到自身最大耐久的 30% 才脱离摧毁，
 * 与是否关键无关；关键与否只决定"归零是否判整机摧毁"。
 * </p>
 * <p>
 * 字段含义见 {@code reloadTime} / {@code reloadRoundByRound} / {@code canSupplyMultiple}：
 * 三者共同描述一次输弹操作付多少时间、移动多少发、允许几根管同时接。
 * 弹仓容量、产弹速率与弹链都不在这里——容量与速率属于弹仓模块，弹链属于部件实例配置。
 * </p>
 */
public class FeedModuleAttr extends AbstractModuleAttr {

    public static final MapCodec<FeedModuleAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("durability_weight", 1.0f).forGetter(AbstractModuleAttr::durabilityWeight),
            Codec.FLOAT.fieldOf("reload_time").forGetter(FeedModuleAttr::reloadTime),
            Codec.BOOL.optionalFieldOf("reload_round_by_round", true).forGetter(FeedModuleAttr::reloadRoundByRound),
            Codec.BOOL.optionalFieldOf("can_supply_multiple", false).forGetter(FeedModuleAttr::canSupplyMultiple)
    ).apply(instance, FeedModuleAttr::new));

    /** 把一个装填单位送进膛的耗时（秒）。逐发模式每发付一次，整匣模式每次对接付一次 */
    private final float reloadTime;

    /** {@code true}=每发都付 {@code reloadTime}；{@code false}=只在对接时付一次，之后瞬时送达 */
    private final boolean reloadRoundByRound;

    /** {@code false}=独占绑定（同一时刻最多一根管）；{@code true}=允许多根管各自计时 */
    private final boolean canSupplyMultiple;

    public FeedModuleAttr(float durabilityWeight, float reloadTime,
                          boolean reloadRoundByRound, boolean canSupplyMultiple) {
        super(durabilityWeight, false); // 机构非关键，硬编码
        this.reloadTime = reloadTime;
        this.reloadRoundByRound = reloadRoundByRound;
        this.canSupplyMultiple = canSupplyMultiple;
    }

    public float reloadTime() {
        return reloadTime;
    }

    public boolean reloadRoundByRound() {
        return reloadRoundByRound;
    }

    public boolean canSupplyMultiple() {
        return canSupplyMultiple;
    }
}
