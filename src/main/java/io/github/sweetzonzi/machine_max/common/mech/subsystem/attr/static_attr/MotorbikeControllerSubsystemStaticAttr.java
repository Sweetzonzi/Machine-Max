package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import lombok.Getter;

import java.util.List;
import java.util.TreeMap;

@Getter
public class MotorbikeControllerSubsystemStaticAttr extends CarControllerSubsystemStaticAttr {
    public final float maxAngle; // 倾斜角超过此角度视作失去平衡，不再修正姿态
    public final float parkingAngle; // 停车时的目标倾斜角度
    public final float correctionForceMultiplier; // 修正力倍率，用于整体缩放摩托车控制系统的平衡调节力

    public static final MapCodec<MotorbikeControllerSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.FLOAT.optionalFieldOf("min_steering_radius", 5.0f).forGetter(MotorbikeControllerSubsystemStaticAttr::getMinSteeringRadius),
            CarControllerSubsystemStaticAttr.STEERING_RADIUS_CODEC.optionalFieldOf("lateral_acceleration", CarControllerSubsystemStaticAttr.createDefaultLateralAccelerationMap()).forGetter(MotorbikeControllerSubsystemStaticAttr::getLateralAccelerationMap),
            CarControllerSubsystemStaticAttr.STEERING_RADIUS_CODEC.optionalFieldOf("max_drift_angular_velocity", CarControllerSubsystemStaticAttr.createDefaultMaxDriftAngularVelocityMap()).forGetter(MotorbikeControllerSubsystemStaticAttr::getMaxDriftAngularVelocityMap),
            Codec.BOOL.optionalFieldOf("manual_gear_shift", false).forGetter(MotorbikeControllerSubsystemStaticAttr::isManualGearShift),
            Codec.BOOL.optionalFieldOf("auto_hand_brake", true).forGetter(MotorbikeControllerSubsystemStaticAttr::isAutoHandBrake),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("move_input_p0", "move_input_p1", "move_input_p2", "move_input_p3", "move_input")).forGetter(MotorbikeControllerSubsystemStaticAttr::getControlInputKeys),
            Codec.FLOAT.optionalFieldOf("max_angle", 30f).forGetter(MotorbikeControllerSubsystemStaticAttr::getMaxAngle),
            Codec.FLOAT.optionalFieldOf("parking_angle", 5f).forGetter(MotorbikeControllerSubsystemStaticAttr::getParkingAngle),
            Codec.FLOAT.optionalFieldOf("correction_force_multiplier", 1.0f).forGetter(MotorbikeControllerSubsystemStaticAttr::getCorrectionForceMultiplier),
            HandBrakeSoundAttr.CODEC.optionalFieldOf("sounds", HandBrakeSoundAttr.DEFAULT).forGetter(MotorbikeControllerSubsystemStaticAttr::getSounds)
    ).apply(instance, MotorbikeControllerSubsystemStaticAttr::new));

    public MotorbikeControllerSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr,
            float minSteeringRadius,
            TreeMap<Float, Float> lateralAccelerationMap,
            TreeMap<Float, Float> maxDriftAngularVelocityMap,
            boolean manualGearShift,
            boolean autoHandBrake,
            List<String> controlInputKeys,
            float maxAngle,
            float parkingAngle,
            float correctionForceMultiplier,
            HandBrakeSoundAttr sounds
    ) {
        super(basicAttr,
                minSteeringRadius,
                lateralAccelerationMap,
                maxDriftAngularVelocityMap,
                manualGearShift,
                autoHandBrake,
                false,
                controlInputKeys,
                sounds);
        this.maxAngle = maxAngle;
        this.parkingAngle = parkingAngle;
        this.correctionForceMultiplier = correctionForceMultiplier;
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