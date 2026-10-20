package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import lombok.Getter;

import java.util.List;

/**
 * 履带驱动静态属性。扭矩单位为 N·m，{@code max_speed} 单位为 rad/s。
 */
@Getter
public class TrackDriveSubsystemStaticAttr extends BasicSubsystemStaticAttr {

    public final List<String> controlInputKeys;
    public final float maxDriveTorque;
    public final float maxBrakeTorque;
    public final float maxHandBrakeTorque;
    public final float maxSpeed;

    public static final MapCodec<TrackDriveSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT)
                    .forGetter(BasicSubsystemStaticAttr::getSoundAttr),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("track_control"))
                    .forGetter(TrackDriveSubsystemStaticAttr::getControlInputKeys),
            Codec.FLOAT.optionalFieldOf("max_drive_torque", 30000f)
                    .forGetter(TrackDriveSubsystemStaticAttr::getMaxDriveTorque),
            Codec.FLOAT.optionalFieldOf("max_brake_torque", 20000f)
                    .forGetter(TrackDriveSubsystemStaticAttr::getMaxBrakeTorque),
            Codec.FLOAT.optionalFieldOf("max_hand_brake_torque", 20000f)
                    .forGetter(TrackDriveSubsystemStaticAttr::getMaxHandBrakeTorque),
            Codec.FLOAT.optionalFieldOf("max_speed", 120f)
                    .forGetter(TrackDriveSubsystemStaticAttr::getMaxSpeed)
    ).apply(instance, TrackDriveSubsystemStaticAttr::new));

    public TrackDriveSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr,
            BasicSubsystemStaticAttr.BasicSoundAttr soundAttr,
            List<String> controlInputKeys,
            float maxDriveTorque,
            float maxBrakeTorque,
            float maxHandBrakeTorque,
            float maxSpeed) {
        super(basicAttr, soundAttr);
        this.controlInputKeys = controlInputKeys;
        this.maxDriveTorque = maxDriveTorque;
        this.maxBrakeTorque = maxBrakeTorque;
        this.maxHandBrakeTorque = maxHandBrakeTorque;
        this.maxSpeed = maxSpeed;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.TRACK_DRIVE;
    }
}
