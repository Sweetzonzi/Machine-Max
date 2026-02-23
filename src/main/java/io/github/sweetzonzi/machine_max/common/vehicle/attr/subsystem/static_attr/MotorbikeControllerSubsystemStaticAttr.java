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
    public final float maxAngle; // 倾斜角超过此角度视作失去平衡，不再修正姿态
    public final float parkingAngle; // 停车时的目标倾斜角度
    public final float correctionForceMultiplier; // 修正力倍率，用于整体缩放摩托车控制系统的平衡调节力

    public static final MapCodec<MotorbikeControllerSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Vec3.CODEC.optionalFieldOf("steering_center", Vec3.ZERO).forGetter(MotorbikeControllerSubsystemStaticAttr::getSteeringCenter),
            CarControllerSubsystemStaticAttr.STEERING_RADIUS_CODEC.optionalFieldOf("steering_radius", CarControllerSubsystemStaticAttr.createDefaultSteeringRadiusMap()).forGetter(MotorbikeControllerSubsystemStaticAttr::getSteeringRadiusMap),
            Codec.BOOL.optionalFieldOf("manual_gear_shift", false).forGetter(MotorbikeControllerSubsystemStaticAttr::isManualGearShift),
            Codec.BOOL.optionalFieldOf("auto_hand_brake", true).forGetter(MotorbikeControllerSubsystemStaticAttr::isAutoHandBrake),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("move_control")).forGetter(MotorbikeControllerSubsystemStaticAttr::getControlInputKeys),
            Codec.FLOAT.optionalFieldOf("max_angle", 30f).forGetter(MotorbikeControllerSubsystemStaticAttr::getMaxAngle),
            Codec.FLOAT.optionalFieldOf("parking_angle", 5f).forGetter(MotorbikeControllerSubsystemStaticAttr::getParkingAngle),
            Codec.FLOAT.optionalFieldOf("correction_force_multiplier", 1.0f).forGetter(MotorbikeControllerSubsystemStaticAttr::getCorrectionForceMultiplier)
    ).apply(instance, MotorbikeControllerSubsystemStaticAttr::new));

    public MotorbikeControllerSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr,
            Vec3 steeringCenter,
            TreeMap<Float, Float> steeringRadiusMap,
            boolean manualGearShift,
            boolean autoHandBrake,
            List<String> controlInputKeys,
            float maxAngle,
            float parkingAngle,
            float correctionForceMultiplier) {
        super(basicAttr,
                steeringCenter,
                steeringRadiusMap,
                manualGearShift,
                autoHandBrake,
                controlInputKeys);
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