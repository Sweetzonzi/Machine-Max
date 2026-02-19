package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import lombok.Getter;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.TreeMap;

@Getter
public class MotorbikeControllerSubsystemStaticAttr extends CarControllerSubsystemStaticAttr {
    public final float maxAngle;
    public final float parkingAngle;

    public static final MapCodec<MotorbikeControllerSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(AbstractSubsystemStaticAttr::getBasicDurability),
            Vec3.CODEC.optionalFieldOf("steering_center", Vec3.ZERO).forGetter(MotorbikeControllerSubsystemStaticAttr::getSteeringCenter),
            CarControllerSubsystemStaticAttr.STEERING_RADIUS_CODEC.optionalFieldOf("steering_radius", CarControllerSubsystemStaticAttr.createDefaultSteeringRadiusMap()).forGetter(MotorbikeControllerSubsystemStaticAttr::getSteeringRadiusMap),
            Codec.BOOL.optionalFieldOf("manual_gear_shift", false).forGetter(MotorbikeControllerSubsystemStaticAttr::isManualGearShift),
            Codec.BOOL.optionalFieldOf("auto_hand_brake", true).forGetter(MotorbikeControllerSubsystemStaticAttr::isAutoHandBrake),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("move_control")).forGetter(MotorbikeControllerSubsystemStaticAttr::getControlInputKeys),
            Codec.BOOL.optionalFieldOf("abs_enabled", true).forGetter(MotorbikeControllerSubsystemStaticAttr::isAbsEnabled),
            Codec.FLOAT.optionalFieldOf("abs_target_slip_ratio", 0.15f).forGetter(MotorbikeControllerSubsystemStaticAttr::getAbsTargetSlipRatio),
            Codec.FLOAT.optionalFieldOf("abs_wheel_radius", 0.5f).forGetter(MotorbikeControllerSubsystemStaticAttr::getAbsWheelRadius),
            Codec.FLOAT.optionalFieldOf("max_angle", 30f).forGetter(MotorbikeControllerSubsystemStaticAttr::getMaxAngle),
            Codec.FLOAT.optionalFieldOf("parking_angle", 5f).forGetter(MotorbikeControllerSubsystemStaticAttr::getParkingAngle)
    ).apply(instance, MotorbikeControllerSubsystemStaticAttr::new));

    public MotorbikeControllerSubsystemStaticAttr(
            float basicDurability,
            Vec3 steeringCenter,
            TreeMap<Float, Float> steeringRadiusMap,
            boolean manualGearShift,
            boolean autoHandBrake,
            List<String> controlInputKeys,
            boolean absEnabled,
            float absTargetSlipRatio,
            float absWheelRadius,
            float maxAngle,
            float parkingAngle) {
        super(basicDurability,
                steeringCenter,
                steeringRadiusMap,
                manualGearShift,
                autoHandBrake,
                controlInputKeys,
                absEnabled,
                absTargetSlipRatio,
                absWheelRadius);
        this.maxAngle = maxAngle;
        this.parkingAngle = parkingAngle;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.MOTORBIKE_CTRL;
    }
}