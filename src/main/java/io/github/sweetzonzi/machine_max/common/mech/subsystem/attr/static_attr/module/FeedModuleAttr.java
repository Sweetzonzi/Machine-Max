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
 * 字段含义见 {@code reloadTime} / {@code canSupplyMultiple}：前者是一次输弹付多少时间，
 * 后者是允许几根管同时接。一次输弹送进几发不由本模块决定——交付单位是一份
 * {@code AmmoBelt}，发数由目标发射器的 {@code single_round_loading} 与源的实际库存共同决定。
 * 弹仓容量、产弹速率与弹链都不在这里——容量与速率属于弹仓模块，弹链属于部件实例配置。
 * </p>
 */
public class FeedModuleAttr extends AbstractModuleAttr {

    public static final MapCodec<FeedModuleAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("durability_weight", 1.0f).forGetter(AbstractModuleAttr::durabilityWeight),
            Codec.FLOAT.fieldOf("reload_time").forGetter(FeedModuleAttr::reloadTime),
            Codec.BOOL.optionalFieldOf("can_supply_multiple", false).forGetter(FeedModuleAttr::canSupplyMultiple)
    ).apply(instance, FeedModuleAttr::new));

    /** 把一份交付送进膛的耗时（秒）。一份交付付一次，与这一份装几发无关 */
    private final float reloadTime;

    /** {@code false}=独占绑定（同一时刻最多一根管）；{@code true}=允许多根管各自计时 */
    private final boolean canSupplyMultiple;

    public FeedModuleAttr(float durabilityWeight, float reloadTime, boolean canSupplyMultiple) {
        super(durabilityWeight, false); // 机构非关键，硬编码
        this.reloadTime = reloadTime;
        this.canSupplyMultiple = canSupplyMultiple;
    }

    public float reloadTime() {
        return reloadTime;
    }

    public boolean canSupplyMultiple() {
        return canSupplyMultiple;
    }
}
