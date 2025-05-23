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
    public final float maxPower;
    public final double inertia;//发动机系统转动惯量(kg·m²)
    public final List<Double> dampingFactors;//发动机系统各阶阻力系数，分别为常数项，一次项…递增
    public final List<String> throttleInputKeys;//优先级从高至低
    public final String powerOutputTarget;
    public final Map<String, List<String>> rpmOutputTargets;

    public static final Codec<Map<String, List<String>>> RPM_OUTPUT_TARGETS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf());

    public static final MapCodec<MotorSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 100f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("hit_box", "").forGetter(AbstractSubsystemAttr::getHitBox),
            Codec.FLOAT.fieldOf("max_power").forGetter(MotorSubsystemAttr::getMaxPower),
            Codec.DOUBLE.optionalFieldOf("inertia", 0.1).forGetter(MotorSubsystemAttr::getInertia),
            Codec.DOUBLE.listOf().optionalFieldOf("damping_factors", List.of(5.0, 0.005, 0.00003)).forGetter(MotorSubsystemAttr::getDampingFactors),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("engine_control", "move_control")).forGetter(MotorSubsystemAttr::getThrottleInputKeys),
            Codec.STRING.fieldOf("power_output").forGetter(MotorSubsystemAttr::getPowerOutputTarget),
            RPM_OUTPUT_TARGETS_CODEC.optionalFieldOf("speed_outputs", Map.of()).forGetter(MotorSubsystemAttr::getRpmOutputTargets)
    ).apply(instance, MotorSubsystemAttr::new));

    public MotorSubsystemAttr(
            float basicDurability,
            String hitBox,
            float maxPower,
            double inertia,
            List<Double> dampingFactors,
            List<String> throttleInputKeys,
            String powerOutputTarget,
            Map<String, List<String>> rpmOutputTargets) {
        super(basicDurability, hitBox);
        this.maxPower = maxPower;
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
        return SubsystemType.MOTOR;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new MotorSubsystem(owner, name, this);
    }
}
