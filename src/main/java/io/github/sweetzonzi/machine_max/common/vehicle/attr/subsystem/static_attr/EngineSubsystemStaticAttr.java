package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class EngineSubsystemStaticAttr extends AbstractSubsystemStaticAttr {
    public final String particleLocator;
    public final float maxPower;
    public final float baseRpm;
    public final float maxTorqueRpm;
    public final float maxRpm;
    public final double inertia;//发动机系统转动惯量(kg·m²)
    public final List<Double> dampingFactors;//发动机系统各阶阻力系数，分别为一次项，二次项，…递增
    public final List<String> throttleInputKeys;//优先级从高至低

    public static final Codec<Map<String, List<String>>> RPM_OUTPUT_TARGETS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf());

    public static final MapCodec<EngineSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(AbstractSubsystemStaticAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("particle_locator", "").forGetter(EngineSubsystemStaticAttr::getParticleLocator),
            Codec.FLOAT.fieldOf("max_power").forGetter(EngineSubsystemStaticAttr::getMaxPower),
            Codec.FLOAT.optionalFieldOf("base_rpm", 500f).forGetter(EngineSubsystemStaticAttr::getBaseRpm),
            Codec.FLOAT.optionalFieldOf("max_torque_rpm", 5500f).forGetter(EngineSubsystemStaticAttr::getMaxTorqueRpm),
            Codec.FLOAT.optionalFieldOf("max_rpm", 7500f).forGetter(EngineSubsystemStaticAttr::getMaxRpm),
            Codec.DOUBLE.optionalFieldOf("inertia", 500.0).forGetter(EngineSubsystemStaticAttr::getInertia),
            Codec.DOUBLE.listOf().optionalFieldOf("damping_factors", List.of(0.005, 0.00003)).forGetter(EngineSubsystemStaticAttr::getDampingFactors),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("engine_control", "move_control")).forGetter(EngineSubsystemStaticAttr::getThrottleInputKeys)
    ).apply(instance, EngineSubsystemStaticAttr::new));

    public EngineSubsystemStaticAttr(
            float basicDurability, 
            String particleLocator,
            float maxPower,
            float baseRpm,
            float maxTorqueRpm,
            float maxRpm,
            double inertia,
            List<Double> dampingFactors,
            List<String> throttleInputKeys) {
        super(basicDurability);
        this.particleLocator = particleLocator;
        this.maxPower = maxPower;
        this.baseRpm = baseRpm;
        this.maxTorqueRpm = maxTorqueRpm;
        this.maxRpm = maxRpm;
        this.inertia = inertia;
        this.dampingFactors = dampingFactors;
        this.throttleInputKeys = throttleInputKeys;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.ENGINE;
    }

}
