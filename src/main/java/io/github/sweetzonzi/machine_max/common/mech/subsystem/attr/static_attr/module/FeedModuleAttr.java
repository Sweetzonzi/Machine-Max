package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractModuleAttr;

/**
 * 输弹机构模块属性——承载输送与交付能力，不拥有库存。
 * <p>
 * 机构非关键（{@code critical=false} 硬编码）：耐久归零只中断后续装填，
 * 弹仓与已存弹药保留，子系统本身不因此进入摧毁状态。
 * 模块自身的摧毁与恢复仍走通用规则——归零即摧毁，修到自身最大耐久的 30% 才脱离摧毁，
 * 与是否关键无关；关键与否只决定"归零是否判整机摧毁"。
 * </p>
 * <p>
 * 本模块只有耐久权重：再生速率、容量与弹种都属于子系统静态属性，不复制到模块上。
 * </p>
 */
public class FeedModuleAttr extends AbstractModuleAttr {

    public static final MapCodec<FeedModuleAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("durability_weight", 1.0f).forGetter(AbstractModuleAttr::durabilityWeight)
    ).apply(instance, FeedModuleAttr::new));

    /** 默认实例：耐久权重 1.0（与弹仓各占 {@code basic_durability} 的一半） */
    public static final FeedModuleAttr DEFAULT = new FeedModuleAttr(1.0f);

    public FeedModuleAttr(float durabilityWeight) {
        super(durabilityWeight, false); // 机构非关键，硬编码
    }
}
