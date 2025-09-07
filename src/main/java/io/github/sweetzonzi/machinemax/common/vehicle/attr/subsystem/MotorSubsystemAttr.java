package io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.MotorSubsystem;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class MotorSubsystemAttr extends AbstractSubsystemAttr {
    public final String particleLocator;
    public final float maxPower;
    public final float maxTorque;
    public final double inertia;//电机系统转动惯量(kg·m²)
    public final List<Double> dampingFactors;//电机系统各阶阻力系数，分别为一次项，二次项，…递增
    public final List<String> throttleInputKeys;//优先级从高至低
    public final String powerOutputTarget;
    public final Map<String, List<String>> rpmOutputTargets;
    public final float generatorEfficiency; // 发电效率（0-1）

    public static final Codec<Map<String, List<String>>> RPM_OUTPUT_TARGETS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf());

    public static final MapCodec<MotorSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("hit_box", "").forGetter(AbstractSubsystemAttr::getHitBox),
            Codec.STRING.optionalFieldOf("particle_locator", "").forGetter(MotorSubsystemAttr::getParticleLocator),
            Codec.FLOAT.fieldOf("max_power").forGetter(MotorSubsystemAttr::getMaxPower),
            Codec.FLOAT.optionalFieldOf("max_torque", 100f).forGetter(MotorSubsystemAttr::getMaxTorque),
            Codec.DOUBLE.optionalFieldOf("inertia", 100.0).forGetter(MotorSubsystemAttr::getInertia),
            Codec.DOUBLE.listOf().optionalFieldOf("damping_factors", List.of(0.003, 0.00002)).forGetter(MotorSubsystemAttr::getDampingFactors),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("motor_control", "move_control")).forGetter(MotorSubsystemAttr::getThrottleInputKeys),
            Codec.STRING.fieldOf("power_output").forGetter(MotorSubsystemAttr::getPowerOutputTarget),
            RPM_OUTPUT_TARGETS_CODEC.optionalFieldOf("speed_outputs", Map.of()).forGetter(MotorSubsystemAttr::getRpmOutputTargets),
            Codec.FLOAT.optionalFieldOf("generator_efficiency", 0.85f).forGetter(MotorSubsystemAttr::getGeneratorEfficiency)
    ).apply(instance, MotorSubsystemAttr::new));

    public MotorSubsystemAttr(
            float basicDurability,
            String hitBox,
            String particleLocator,
            float maxPower,
            float maxTorque,
            double inertia,
            List<Double> dampingFactors,
            List<String> throttleInputKeys,
            String powerOutputTarget,
            Map<String, List<String>> rpmOutputTargets,
            float generatorEfficiency) {
        super(basicDurability, hitBox);
        this.particleLocator = particleLocator;
        this.maxPower = maxPower;
        this.maxTorque = maxTorque;
        this.inertia = inertia;
        this.dampingFactors = dampingFactors;
        this.throttleInputKeys = throttleInputKeys;
        this.powerOutputTarget = powerOutputTarget;
        this.rpmOutputTargets = rpmOutputTargets;
        this.generatorEfficiency = generatorEfficiency;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.MOTOR;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new MotorSubsystem(owner, name, this);
    }
}