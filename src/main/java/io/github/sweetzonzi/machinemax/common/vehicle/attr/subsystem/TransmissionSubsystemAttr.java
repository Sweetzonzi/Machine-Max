package io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.TransmissionSubsystem;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 传动系统属性，将输入的动力按权重分流至各个输出端，再将各个输出端的运行速度反馈加权平均汇总至输入端。
 */
@Getter
public class TransmissionSubsystemAttr extends AbstractSubsystemAttr {
    public final Map<String, Float> powerOutputs;//功率输出目标，及输出权重
    public final List<String> controlInputKeys;//控制信号名
    public static final Codec<Map<String, Float>> POWER_OUTPUTS_CODEC = Codec.unboundedMap(
            Codec.STRING,
            Codec.FLOAT
    );

    public static final MapCodec<TransmissionSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 100f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("hit_box", "").forGetter(AbstractSubsystemAttr::getHitBox),
            POWER_OUTPUTS_CODEC.fieldOf("power_outputs").forGetter(TransmissionSubsystemAttr::getPowerOutputs),
            Codec.STRING.listOf().optionalFieldOf("distribute_inputs", List.of()).forGetter(TransmissionSubsystemAttr::getControlInputKeys)
    ).apply(instance, TransmissionSubsystemAttr::new));

    public TransmissionSubsystemAttr(
            float basicDurability,
            String hitBox,
            Map<String, Float> powerOutputs,
            List<String> controlInputKeys) {
        super(basicDurability, hitBox);
        this.powerOutputs = powerOutputs;
        this.controlInputKeys = controlInputKeys;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.TRANSMISSION;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new TransmissionSubsystem(owner, name, this);
    }
}
