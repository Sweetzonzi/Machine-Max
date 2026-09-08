package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.MotorbikeControllerSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.MotorbikeControllerSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

@Getter
public class MotorbikeControllerSubsystemAttr extends CarControllerSubsystemAttr {
    public final MotorbikeControllerSubsystemStaticAttr staticAttribute;

    public static final MapCodec<MotorbikeControllerSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.fieldOf("control_outputs").forGetter(MotorbikeControllerSubsystemAttr::getControlOutputTargets),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("speed_outputs", Map.of("vehicle_speed", List.of("local", "global"))).forGetter(MotorbikeControllerSubsystemAttr::getSpeedOutputTargets),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("throttle_outputs", Map.of("throttle", List.of("local", "global"))).forGetter(MotorbikeControllerSubsystemAttr::getThrottleOutputTargets),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("steering_outputs", Map.of("steering", List.of("local", "global"))).forGetter(MotorbikeControllerSubsystemAttr::getSteeringOutputTargets),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("brake_outputs", Map.of("brake", List.of("local", "global"))).forGetter(MotorbikeControllerSubsystemAttr::getBrakeOutputTargets),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("handbrake_outputs", Map.of("handbrake", List.of("local", "global"))).forGetter(MotorbikeControllerSubsystemAttr::getHandbrakeOutputTargets)
    ).apply(instance, MotorbikeControllerSubsystemAttr::new));

    public MotorbikeControllerSubsystemAttr(
            ResourceLocation modelName,
            Map<String, List<String>> controlOutputTargets,
            Map<String, List<String>> speedOutputTargets,
            Map<String, List<String>> throttleOutputTargets,
            Map<String, List<String>> steeringOutputTargets,
            Map<String, List<String>> brakeOutputTargets,
            Map<String, List<String>> handbrakeOutputTargets) {
        super(modelName,
                controlOutputTargets,
                speedOutputTargets,
                throttleOutputTargets,
                steeringOutputTargets,
                brakeOutputTargets,
                handbrakeOutputTargets);
        this.staticAttribute = (MotorbikeControllerSubsystemStaticAttr) getStaticAttr();
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.MOTORBIKE_CTRL;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new MotorbikeControllerSubsystem(owner, name, this);
    }
}
