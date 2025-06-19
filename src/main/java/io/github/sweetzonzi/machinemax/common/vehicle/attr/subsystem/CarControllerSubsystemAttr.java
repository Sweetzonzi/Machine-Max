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
    public final float throttleSensitivity;
    public final float brakeSensitivity;
    public final float handBrakeSensitivity;
    public final float steeringSensitivity;
    public final Map<String, List<String>> engineControlOutputTargets;//信号频道和目标名称列表，下同 Signal channels and target hitBoxName list, etc.
    public final Map<String, List<String>> wheelControlOutputTargets;
    public final Map<String, List<String>> gearboxControlOutputTargets;

    public static final MapCodec<CarControllerSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("hit_box", "").forGetter(AbstractSubsystemAttr::getHitBox),
            Codec.STRING.listOf().fieldOf("control_inputs").forGetter(CarControllerSubsystemAttr::getControlInputKeys),
            Vec3.CODEC.optionalFieldOf("steering_center", Vec3.ZERO).forGetter(CarControllerSubsystemAttr::getSteeringCenter),
            Codec.FLOAT.optionalFieldOf("steering_radius", 5.0f).forGetter(CarControllerSubsystemAttr::getSteeringRadius),
            Codec.BOOL.optionalFieldOf("manual_gear_shift", false).forGetter(CarControllerSubsystemAttr::isManualGearShift),
            Codec.BOOL.optionalFieldOf("auto_hand_brake", true).forGetter(CarControllerSubsystemAttr::isAutoHandBrake),
            Codec.FLOAT.optionalFieldOf("throttle_sensitivity", 20f).forGetter(CarControllerSubsystemAttr::getThrottleSensitivity),
            Codec.FLOAT.optionalFieldOf("brake_sensitivity", 20f).forGetter(CarControllerSubsystemAttr::getBrakeSensitivity),
            Codec.FLOAT.optionalFieldOf("hand_brake_sensitivity", 20f).forGetter(CarControllerSubsystemAttr::getHandBrakeSensitivity),
            Codec.FLOAT.optionalFieldOf("steering_sensitivity", 20f).forGetter(CarControllerSubsystemAttr::getSteeringSensitivity),
            SIGNAL_TARGETS_CODEC.fieldOf("engine_outputs").forGetter(CarControllerSubsystemAttr::getEngineControlOutputTargets),
            SIGNAL_TARGETS_CODEC.fieldOf("wheel_outputs").forGetter(CarControllerSubsystemAttr::getWheelControlOutputTargets),
            SIGNAL_TARGETS_CODEC.fieldOf("gearbox_outputs").forGetter(CarControllerSubsystemAttr::getGearboxControlOutputTargets)
    ).apply(instance, CarControllerSubsystemAttr::new));

    public CarControllerSubsystemAttr(
            float basicDurability,
            String hitBox,
            List<String> controlInputKeys,
            Vec3 steeringCenter,
            float steeringRadius,
            boolean manualGearShift,
            boolean autoHandBrake,
            float throttleSensitivity,
            float brakeSensitivity,
            float handBrakeSensitivity,
            float steeringSensitivity,
            Map<String, List<String>> engineControlOutputTargets,
            Map<String, List<String>> wheelControlOutputTargets,
            Map<String, List<String>> gearboxControlOutputTargets) {
        super(basicDurability, hitBox);
        this.controlInputKeys = controlInputKeys;
        this.steeringCenter = steeringCenter;
        this.steeringRadius = steeringRadius;
        this.manualGearShift = manualGearShift;
        this.autoHandBrake = autoHandBrake;
        this.throttleSensitivity = throttleSensitivity/100;
        this.brakeSensitivity = brakeSensitivity/100;
        this.handBrakeSensitivity = handBrakeSensitivity/100;
        this.steeringSensitivity = steeringSensitivity/100;
        this.engineControlOutputTargets = engineControlOutputTargets;
        this.wheelControlOutputTargets = wheelControlOutputTargets;
        this.gearboxControlOutputTargets = gearboxControlOutputTargets;
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
