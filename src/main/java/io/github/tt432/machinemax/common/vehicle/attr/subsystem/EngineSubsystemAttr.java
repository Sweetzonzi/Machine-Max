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
    public final float maxTorqueRpm;
    public final float maxRpm;
    public final double inertia;//发动机系统转动惯量(kg·m²)
    public final List<Double> dampingFactors;//发动机系统各阶阻力系数，分别为常数项，一次项…递增
    public final List<String> throttleInputKeys;//优先级从高至低
    public final String powerOutputTarget;
    public final Map<String, List<String>> rpmOutputTargets;

    public static final Codec<Map<String, List<String>>> RPM_OUTPUT_TARGETS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf());

    public static final MapCodec<EngineSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.fieldOf("max_power").forGetter(EngineSubsystemAttr::getMaxPower),
            Codec.FLOAT.optionalFieldOf("base_rpm", 500f).forGetter(EngineSubsystemAttr::getBaseRpm),
            Codec.FLOAT.optionalFieldOf("max_torque_rpm", 5000f).forGetter(EngineSubsystemAttr::getMaxTorqueRpm),
            Codec.FLOAT.optionalFieldOf("max_rpm", 7000f).forGetter(EngineSubsystemAttr::getMaxRpm),
            Codec.DOUBLE.optionalFieldOf("inertia", 0.1).forGetter(EngineSubsystemAttr::getInertia),
            Codec.DOUBLE.listOf().optionalFieldOf("damping_factors", List.of(5.0, 0.005, 0.00003)).forGetter(EngineSubsystemAttr::getDampingFactors),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("engine_control", "move_control")).forGetter(EngineSubsystemAttr::getThrottleInputKeys),
            Codec.STRING.fieldOf("power_output").forGetter(EngineSubsystemAttr::getPowerOutputTarget),
            RPM_OUTPUT_TARGETS_CODEC.optionalFieldOf("speed_outputs", Map.of()).forGetter(EngineSubsystemAttr::getRpmOutputTargets)
    ).apply(instance, EngineSubsystemAttr::new));

    public EngineSubsystemAttr(
            float maxPower,
            float baseRpm,
            float maxTorqueRpm,
            float maxRpm,
            double inertia,
            List<Double> dampingFactors,
            List<String> throttleInputKeys,
            String powerOutputTarget,
            Map<String, List<String>> rpmOutputTargets) {
        this.maxPower = maxPower;
        this.baseRpm = baseRpm;
        this.maxTorqueRpm = maxTorqueRpm;
        this.maxRpm = maxRpm;
        this.inertia = inertia;
        this.dampingFactors = dampingFactors;
        this.throttleInputKeys = throttleInputKeys;
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
