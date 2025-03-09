package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 传动系统属性，将输入的动力按权重分流至各个输出端，再将各个输出端的运行速度反馈加权平均汇总至输入端。
 */
@Getter
public class TransmissionSubsystemAttr extends AbstractSubsystemAttr {
    public final String speedInputKey;//接收的转速信号名称
    public final List<String> speedFeedbackInputKeys;//接收的反馈转速信号名称列表
    public final Map<String, List<String>> speedFeedbackOutputTargets;//反馈转速信号合一后的输出名称及目标列表
    public final Map<String, Pair<String, Float>> rotationOutputs;//旋转输出目标，及输出转速信号名称和权重

    public static final Codec<Map<String, List<String>>> SIGNAL_OUTPUTS_CODEC = Codec.unboundedMap(
            Codec.STRING,
            Codec.STRING.listOf()
    );

    public static final Codec<Pair<String, Float>> ROTATION_OUTPUT_CODEC = Codec.pair(
            Codec.STRING.fieldOf("speed_signal_output").codec(),
            Codec.floatRange(0f, Float.MAX_VALUE).fieldOf("power_weight").codec()
    );

    public static final Codec<Map<String, Pair<String, Float>>> ROTATION_OUTPUTS_CODEC = Codec.unboundedMap(
            Codec.STRING,
            ROTATION_OUTPUT_CODEC
    );

    public static final MapCodec<TransmissionSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("speed_input").forGetter(TransmissionSubsystemAttr::getSpeedInputKey),
            Codec.STRING.listOf().optionalFieldOf("speed_feedback_inputs",List.of()).forGetter(TransmissionSubsystemAttr::getSpeedFeedbackInputKeys),
            SIGNAL_OUTPUTS_CODEC.optionalFieldOf("speed_feedback_outputs", Map.of()).forGetter(TransmissionSubsystemAttr::getSpeedFeedbackOutputTargets),
            ROTATION_OUTPUTS_CODEC.fieldOf("power_outputs").forGetter(TransmissionSubsystemAttr::getRotationOutputs)
    ).apply(instance, TransmissionSubsystemAttr::new));

    public TransmissionSubsystemAttr(String speedInputKey, List<String> speedFeedbackInputKeys, Map<String, List<String>> speedFeedbackOutputTargets, Map<String, Pair<String, Float>> rotationOutputs) {
        this.speedInputKey = speedInputKey;
        this.speedFeedbackInputKeys = speedFeedbackInputKeys;
        this.speedFeedbackOutputTargets = speedFeedbackOutputTargets;
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
