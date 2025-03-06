package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.datafixers.util.Pair;
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
    public final String powerInputKey;//接收的转速信号名称
    public final String speedFeedbackOutputKey;//反馈的转速信号名称
    public final Map<String, Pair<String, Float>> rotationOutputs;//旋转输出目标，及输出转速信号名称和权重

    public static final Codec<Pair<String, Float>> ROTATION_OUTPUT_CODEC = Codec.pair(Codec.STRING, Codec.FLOAT);

    public static final Codec<Map<String, Pair<String, Float>>> ROTATION_OUTPUTS_CODEC = Codec.unboundedMap(
            Codec.STRING,
            ROTATION_OUTPUT_CODEC
    );

    public static final MapCodec<TransmissionSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("power_input").forGetter(TransmissionSubsystemAttr::getPowerInputKey),
            Codec.STRING.fieldOf("speed_feedback_output").forGetter(TransmissionSubsystemAttr::getSpeedFeedbackOutputKey),
            ROTATION_OUTPUTS_CODEC.fieldOf("power_outputs").forGetter(TransmissionSubsystemAttr::getRotationOutputs)
    ).apply(instance, TransmissionSubsystemAttr::new));

    public TransmissionSubsystemAttr(String powerInputKey, String speedFeedbackOutputKey, Map<String, Pair<String, Float>> rotationOutputs) {
        this.powerInputKey = powerInputKey;
        this.speedFeedbackOutputKey = speedFeedbackOutputKey;
        this.rotationOutputs = rotationOutputs;
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
