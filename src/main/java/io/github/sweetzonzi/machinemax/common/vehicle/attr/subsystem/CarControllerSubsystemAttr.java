package io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.CarControllerSubsystem;
import lombok.Getter;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

//TODO:渐进油门，根据油门开度调整换挡时机
@Getter
public class CarControllerSubsystemAttr extends AbstractSubsystemAttr {
    public final List<String> controlInputKeys;
    public final Vec3 steeringCenter;
    public final float steeringRadius;
    public final boolean manualGearShift;
    public final boolean autoHandBrake;
    public final Map<String, List<String>> engineControlOutputTargets;//信号频道和目标名称列表，下同 Signal channels and target hitBoxName list, etc.
    public final Map<String, List<String>> wheelControlOutputTargets;
    public final Map<String, List<String>> gearboxControlOutputTargets;
    public final Map<String, List<String>> speedOutputTargets;
    public final Map<String, List<String>> throttleOutputTargets;
    public final Map<String, List<String>> steeringOutputTargets;
    public final Map<String, List<String>> brakeOutputTargets;
    public final Map<String, List<String>> handbrakeOutputTargets;

    public static final MapCodec<CarControllerSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("hit_box", "").forGetter(AbstractSubsystemAttr::getHitBox),
            Codec.STRING.listOf().fieldOf("control_inputs").forGetter(CarControllerSubsystemAttr::getControlInputKeys),
            Vec3.CODEC.optionalFieldOf("steering_center", Vec3.ZERO).forGetter(CarControllerSubsystemAttr::getSteeringCenter),
            Codec.FLOAT.optionalFieldOf("steering_radius", 5.0f).forGetter(CarControllerSubsystemAttr::getSteeringRadius),
            Codec.BOOL.optionalFieldOf("manual_gear_shift", false).forGetter(CarControllerSubsystemAttr::isManualGearShift),
            Codec.BOOL.optionalFieldOf("auto_hand_brake", true).forGetter(CarControllerSubsystemAttr::isAutoHandBrake),
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
            float basicDurability,
            String hitBox,
            List<String> controlInputKeys,
            Vec3 steeringCenter,
            float steeringRadius,
            boolean manualGearShift,
            boolean autoHandBrake,
            Map<String, List<String>> engineControlOutputTargets,
            Map<String, List<String>> wheelControlOutputTargets,
            Map<String, List<String>> gearboxControlOutputTargets,
            Map<String, List<String>> speedOutputTargets,
            Map<String, List<String>> throttleOutputTargets,
            Map<String, List<String>> steeringOutputTargets,
            Map<String, List<String>> brakeOutputTargets,
            Map<String, List<String>> handbrakeOutputTargets) {
        super(basicDurability, hitBox);
        this.controlInputKeys = controlInputKeys;
        this.steeringCenter = steeringCenter;
        this.steeringRadius = steeringRadius;
        this.manualGearShift = manualGearShift;
        this.autoHandBrake = autoHandBrake;
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
    public SubsystemType getType() {
        return SubsystemType.CAR_CTRL;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new CarControllerSubsystem(owner, name, this);
    }
}
