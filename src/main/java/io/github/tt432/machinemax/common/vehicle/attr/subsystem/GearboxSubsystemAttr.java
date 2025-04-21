package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.tt432.machinemax.common.vehicle.subsystem.GearboxSubsystem;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class GearboxSubsystemAttr extends AbstractSubsystemAttr {
    public final float finalRatio;//最终减速比，用于整体缩放减速比
    public final List<Float> ratios;//减速比，升序排列
    public final float switchTime;//换挡时间(秒)
    public final List<String> ratioControlSignalKeys;//换挡控制信号，指定选用的减速比
    public final String powerOutputTarget;//动力输出端名
    public final Map<String, List<String>> gearOutputTargets;//输出反馈信号名，输出当前所处挡位供其他地方使用

    public GearboxSubsystemAttr(
            float basicDurability,
            String hitBox,
            float finalRatio,
            List<Float> ratios,
            float switchTime,
            List<String> ratioControlSignalKeys,
            String powerOutputTarget,
            Map<String, List<String>> gearOutputTargets) {
        super(basicDurability, hitBox);
        this.finalRatio = finalRatio;
        this.ratios = ratios;
        this.switchTime = switchTime;
        this.ratioControlSignalKeys = ratioControlSignalKeys;
        this.powerOutputTarget = powerOutputTarget;
        this.gearOutputTargets = gearOutputTargets;
    }

    public static final MapCodec<GearboxSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 100f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("hit_box", "").forGetter(AbstractSubsystemAttr::getHitBox),
            Codec.FLOAT.optionalFieldOf("final_ratio", 5f).forGetter(GearboxSubsystemAttr::getFinalRatio),
            Codec.list(Codec.FLOAT).optionalFieldOf("ratios", List.of(-3.5f, 3.5f, 2f, 1.3f, 1.0f, 0.8f)).forGetter(GearboxSubsystemAttr::getRatios),
            Codec.FLOAT.optionalFieldOf("switch_time", 0.5f).forGetter(GearboxSubsystemAttr::getSwitchTime),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("gearbox_control")).forGetter(GearboxSubsystemAttr::getRatioControlSignalKeys),
            Codec.STRING.fieldOf("power_output").forGetter(GearboxSubsystemAttr::getPowerOutputTarget),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("gear_outputs", Map.of()).forGetter(GearboxSubsystemAttr::getGearOutputTargets)
    ).apply(instance, GearboxSubsystemAttr::new));

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.GEARBOX;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new GearboxSubsystem(owner, name, this);
    }
}
