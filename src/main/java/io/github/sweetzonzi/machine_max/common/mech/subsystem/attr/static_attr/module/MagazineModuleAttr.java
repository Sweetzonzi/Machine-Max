package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractModuleAttr;

/**
 * 弹仓模块属性——可用的储存结构，是装弹机唯一的模块。
 * <p>
 * 关键模块（{@code critical=true} 硬编码）：耐久归零即整机摧毁，停止存入、再生与供给；
 * 子系统摧毁边沿按库存贡献决定是否殉爆。恢复沿关键模块规则，需要回到 30% 以上并完成恢复锁存解除。
 * </p>
 * <p>
 * 本模块只有耐久权重：容量与弹链都属于子系统静态属性与部件实例配置，不复制到模块上。
 * </p>
 */
public class MagazineModuleAttr extends AbstractModuleAttr {

    public static final MapCodec<MagazineModuleAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("durability_weight", 1.0f).forGetter(AbstractModuleAttr::durabilityWeight)
    ).apply(instance, MagazineModuleAttr::new));

    /** 默认实例：耐久权重 1.0（与机构各占 {@code basic_durability} 的一半） */
    public static final MagazineModuleAttr DEFAULT = new MagazineModuleAttr(1.0f);

    public MagazineModuleAttr(float durabilityWeight) {
        super(durabilityWeight, true); // 弹仓关键，硬编码
    }
}
