package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class EngineSubsystemAttr extends AbstractSubsystemAttr {
    public final float maxPower;
    public final float baseRpm;
    public final float maxRpm;
    public final float inertia;//发动机系统转动惯量(kg·m²)
    public final float damping;
    public final List<String> throttleInputKeys;//优先级从高至低
    public final String speedFeedbackInputKey;//输出功率的速度反馈输入
    public final String powerOutputTarget;
    public final Map<String, String> rpmOutputTargets;//目标与信号名

    public static final Codec<Map<String,String>> RPM_OUTPUT_TARGETS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING);

    public static final MapCodec<EngineSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.fieldOf("max_power").forGetter(EngineSubsystemAttr::getMaxPower),
            Codec.FLOAT.fieldOf("base_rpm").forGetter(EngineSubsystemAttr::getBaseRpm),
            Codec.FLOAT.fieldOf("max_rpm").forGetter(EngineSubsystemAttr::getMaxRpm),
            Codec.FLOAT.optionalFieldOf("inertia",50f).forGetter(EngineSubsystemAttr::getInertia),
            Codec.FLOAT.optionalFieldOf("damping", 0.5f).forGetter(EngineSubsystemAttr::getDamping),
            Codec.STRING.listOf().fieldOf("throttle_inputs").forGetter(EngineSubsystemAttr::getThrottleInputKeys),
            Codec.STRING.fieldOf("speed_feedback_input").forGetter(EngineSubsystemAttr::getSpeedFeedbackInputKey),
            Codec.STRING.fieldOf("power_output_target").forGetter(EngineSubsystemAttr::getPowerOutputTarget),
            RPM_OUTPUT_TARGETS_CODEC.fieldOf("rpm_output_targets").forGetter(EngineSubsystemAttr::getRpmOutputTargets)
    ).apply(instance, EngineSubsystemAttr::new));

    public EngineSubsystemAttr(
            float maxPower,
            float baseRpm,
            float maxRpm,
            float inertia,
            float damping,
            List<String> throttleInputKeys,
            String speedFeedbackInputKey,
            String powerOutputTarget,
            Map<String, String> rpmOutputTargets) {
        this.maxPower = maxPower;
        this.baseRpm = baseRpm;
        this.maxRpm = maxRpm;
        this.inertia = inertia;
        this.damping = damping;
        this.throttleInputKeys = throttleInputKeys;
        this.speedFeedbackInputKey = speedFeedbackInputKey;
        this.powerOutputTarget = powerOutputTarget;
        this.rpmOutputTargets = rpmOutputTargets;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.ENGINE;
    }
}
