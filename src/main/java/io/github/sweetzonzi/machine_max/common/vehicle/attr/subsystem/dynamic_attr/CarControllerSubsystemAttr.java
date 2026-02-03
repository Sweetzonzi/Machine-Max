package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.CarControllerSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.CarControllerSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

//TODO:渐进油门，根据油门开度调整换挡时机
@Getter
public class CarControllerSubsystemAttr extends AbstractSubsystemAttr {
    public final CarControllerSubsystemStaticAttr staticAttribute;
    public final Map<String, List<String>> engineControlOutputTargets;//信号频道和目标名称列表，下同 Signal channels and target hitBoxName list, etc.
    public final Map<String, List<String>> wheelControlOutputTargets;
    public final Map<String, List<String>> gearboxControlOutputTargets;
    public final Map<String, List<String>> speedOutputTargets;
    public final Map<String, List<String>> throttleOutputTargets;
    public final Map<String, List<String>> steeringOutputTargets;
    public final Map<String, List<String>> brakeOutputTargets;
    public final Map<String, List<String>> handbrakeOutputTargets;

    public static final MapCodec<CarControllerSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            SIGNAL_TARGETS_CODEC.fieldOf("engine_outputs").forGetter(CarControllerSubsystemAttr::getEngineControlOutputTargets),
            SIGNAL_TARGETS_CODEC.fieldOf("wheel_outputs").forGetter(CarControllerSubsystemAttr::getWheelControlOutputTargets),
            SIGNAL_TARGETS_CODEC.fieldOf("gearbox_outputs").forGetter(CarControllerSubsystemAttr::getGearboxControlOutputTargets),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("speed_outputs", Map.of("vehicle_speed", List.of("part", "vehicle"))).forGetter(CarControllerSubsystemAttr::getSpeedOutputTargets),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("throttle_outputs", Map.of("throttle", List.of("part", "vehicle"))).forGetter(CarControllerSubsystemAttr::getThrottleOutputTargets),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("steering_outputs", Map.of("steering", List.of("part", "vehicle"))).forGetter(CarControllerSubsystemAttr::getSteeringOutputTargets),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("brake_outputs", Map.of("brake", List.of("part", "vehicle"))).forGetter(CarControllerSubsystemAttr::getBrakeOutputTargets),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("handbrake_outputs", Map.of("handbrake", List.of("part", "vehicle"))).forGetter(CarControllerSubsystemAttr::getHandbrakeOutputTargets)
    ).apply(instance, CarControllerSubsystemAttr::new));

    public CarControllerSubsystemAttr(
            ResourceLocation modelName,
            Map<String, List<String>> engineControlOutputTargets,
            Map<String, List<String>> wheelControlOutputTargets,
            Map<String, List<String>> gearboxControlOutputTargets,
            Map<String, List<String>> speedOutputTargets,
            Map<String, List<String>> throttleOutputTargets,
            Map<String, List<String>> steeringOutputTargets,
            Map<String, List<String>> brakeOutputTargets,
            Map<String, List<String>> handbrakeOutputTargets) {
        super(modelName);
        this.staticAttribute = (CarControllerSubsystemStaticAttr) getStaticAttr();
        this.engineControlOutputTargets = engineControlOutputTargets;
        this.wheelControlOutputTargets = wheelControlOutputTargets;
        this.gearboxControlOutputTargets = gearboxControlOutputTargets;
        this.speedOutputTargets = speedOutputTargets;
        this.throttleOutputTargets = throttleOutputTargets;
        this.steeringOutputTargets = steeringOutputTargets;
        this.brakeOutputTargets = brakeOutputTargets;
        this.handbrakeOutputTargets = handbrakeOutputTargets;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.CAR_CTRL;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new CarControllerSubsystem(owner, name, this);
    }
}
