package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;

import java.util.Map;

/**
 * 传动系统属性，将输入的动力按权重分流至各个输出端，再将各个输出端的运行速度反馈加权平均汇总至输入端。
 */
@Getter
public class TransmissionSubsystemAttr extends AbstractSubsystemAttr {
    public final Map<String, Float> powerOutputs;//功率输出目标，及输出权重

    public static final Codec<Map<String, Float>> POWER_OUTPUTS_CODEC = Codec.unboundedMap(
            Codec.STRING,
            Codec.FLOAT
    );

    public static final MapCodec<TransmissionSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            POWER_OUTPUTS_CODEC.fieldOf("power_outputs").forGetter(TransmissionSubsystemAttr::getPowerOutputs)
    ).apply(instance, TransmissionSubsystemAttr::new));

    public TransmissionSubsystemAttr(Map<String, Float> powerOutputs) {
        this.powerOutputs = powerOutputs;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.TRANSMISSION;
    }
}
