package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import lombok.Getter;
import net.minecraft.world.phys.Vec3;

import java.util.List;

@Getter
public class CarControllerSubsystemStaticAttr extends AbstractSubsystemStaticAttr {
    public final Vec3 steeringCenter;
    public final float steeringRadius;
    public final boolean manualGearShift;
    public final boolean autoHandBrake;
    public final List<String> controlInputKeys;
    public final boolean absEnabled;
    public final float absTargetSlipRatio;
    public final float absWheelRadius;

    public static final MapCodec<CarControllerSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(AbstractSubsystemStaticAttr::getBasicDurability),
            Vec3.CODEC.optionalFieldOf("steering_center", Vec3.ZERO).forGetter(CarControllerSubsystemStaticAttr::getSteeringCenter),
            Codec.FLOAT.optionalFieldOf("steering_radius", 5.0f).forGetter(CarControllerSubsystemStaticAttr::getSteeringRadius),
            Codec.BOOL.optionalFieldOf("manual_gear_shift", false).forGetter(CarControllerSubsystemStaticAttr::isManualGearShift),
            Codec.BOOL.optionalFieldOf("auto_hand_brake", true).forGetter(CarControllerSubsystemStaticAttr::isAutoHandBrake),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("move_control")).forGetter(CarControllerSubsystemStaticAttr::getControlInputKeys),
            Codec.BOOL.optionalFieldOf("abs_enabled", true).forGetter(CarControllerSubsystemStaticAttr::isAbsEnabled),
            Codec.FLOAT.optionalFieldOf("abs_target_slip_ratio", 0.15f).forGetter(CarControllerSubsystemStaticAttr::getAbsTargetSlipRatio),
            Codec.FLOAT.optionalFieldOf("abs_wheel_radius", 0.5f).forGetter(CarControllerSubsystemStaticAttr::getAbsWheelRadius)
    ).apply(instance, CarControllerSubsystemStaticAttr::new));

    public CarControllerSubsystemStaticAttr(
            float basicDurability,
            Vec3 steeringCenter,
            float steeringRadius,
            boolean manualGearShift,
            boolean autoHandBrake,
            List<String> controlInputKeys,
            boolean absEnabled,
            float absTargetSlipRatio,
            float absWheelRadius) {
        super(basicDurability);
        this.steeringCenter = steeringCenter;
        this.steeringRadius = steeringRadius;
        this.manualGearShift = manualGearShift;
        this.autoHandBrake = autoHandBrake;
        this.controlInputKeys = controlInputKeys;
        this.absEnabled = absEnabled;
        this.absTargetSlipRatio = absTargetSlipRatio;
        this.absWheelRadius = absWheelRadius;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.CAR_CTRL;
    }

}
