package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import lombok.Getter;

import java.util.List;

@Getter
public class GearboxSubsystemStaticAttr extends BasicSubsystemStaticAttr {
    public final float finalRatio;//最终减速比，用于整体缩放减速比
    public final List<Float> ratios;//减速比，升序排列
    public final float switchTime;//换挡时间(秒)
    public final List<String> ratioControlSignalKeys;//换挡控制信号，指定选用的减速比 TODO:似乎暂时无效，需要检查

    public GearboxSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr,
            float finalRatio,
            List<Float> ratios,
            float switchTime,
            List<String> ratioControlSignalKeys) {
        super(basicAttr);
        this.finalRatio = finalRatio;
        this.ratios = ratios;
        this.switchTime = switchTime;
        this.ratioControlSignalKeys = ratioControlSignalKeys;
    }

    public static final MapCodec<GearboxSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.FLOAT.optionalFieldOf("final_ratio", 10f).forGetter(GearboxSubsystemStaticAttr::getFinalRatio),
            Codec.list(Codec.FLOAT).optionalFieldOf("ratios", List.of(-3.5f, 3.5f, 2.5f, 1.7f, 1.4f, 1.1f)).forGetter(GearboxSubsystemStaticAttr::getRatios),
            Codec.FLOAT.optionalFieldOf("switch_time", 0.3f).forGetter(GearboxSubsystemStaticAttr::getSwitchTime),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("gearbox_control")).forGetter(GearboxSubsystemStaticAttr::getRatioControlSignalKeys)
    ).apply(instance, GearboxSubsystemStaticAttr::new));

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.GEARBOX;
    }

}
