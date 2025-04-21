package io.github.tt432.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.util.data.Axis;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record MotorAttr(
        @Nullable Float lowerLimit,
        @Nullable Float upperLimit,
        @Nullable Float equilibrium,
        @Nullable Float stiffness,
        @Nullable Float damping,
        boolean needsPower,
        float maxForce,
        float maxBrakeForce,
        float maxSpeed,
        List<String> targetSpeedSignalKey,
        List<String> targetPositionSignalKey,
        Map<String, List<String>> speedSignalOutputs,
        Map<String, List<String>> positionSignalOutputs
) {

    public static final Codec<Map<String, List<String>>> SIGNAL_OUTPUTS_CODEC = Codec.unboundedMap(
            Codec.STRING,
            Codec.STRING.listOf()
    );

    public static final Codec<MotorAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("lower_limit")
                    .forGetter(j -> Optional.ofNullable(j.lowerLimit)),
            Codec.FLOAT.optionalFieldOf("upper_limit")
                    .forGetter(j -> Optional.ofNullable(j.upperLimit)),
            Codec.FLOAT.optionalFieldOf("equilibrium")
                    .forGetter(j -> Optional.ofNullable(j.equilibrium)),
            Codec.FLOAT.optionalFieldOf("stiffness")
                    .forGetter(j -> Optional.ofNullable(j.stiffness)),
            Codec.FLOAT.optionalFieldOf("damping")
                    .forGetter(j -> Optional.ofNullable(j.damping)),
            Codec.BOOL.fieldOf("needs_power").forGetter(MotorAttr::needsPower),
            Codec.FLOAT.fieldOf("max_force").forGetter(MotorAttr::maxForce),
            Codec.FLOAT.fieldOf("max_brake_force").forGetter(MotorAttr::maxBrakeForce),
            Codec.FLOAT.fieldOf("max_speed").forGetter(MotorAttr::maxSpeed),
            Codec.STRING.listOf().fieldOf("target_speed_inputs").forGetter(MotorAttr::targetSpeedSignalKey),
            Codec.STRING.listOf().optionalFieldOf("target_position_inputs", List.of()).forGetter(MotorAttr::targetPositionSignalKey),
            SIGNAL_OUTPUTS_CODEC.optionalFieldOf("speed_outputs", Map.of()).forGetter(MotorAttr::speedSignalOutputs),
            SIGNAL_OUTPUTS_CODEC.optionalFieldOf("position_outputs", Map.of()).forGetter(MotorAttr::positionSignalOutputs)
    ).apply(instance, (lower, upper, equal, stiff, damp, needPower, power, brake,maxSpd, targetSpd, targetPos, speedOut, posOut) -> new MotorAttr(
            lower.orElse(null),
            upper.orElse(null),
            equal.orElse(null),
            stiff.orElse(null),
            damp.orElse(null),
            needPower,
            power,
            brake,
            maxSpd,
            targetSpd,
            targetPos,
            speedOut,
            posOut
    )));

    public static final Codec<Map<Integer, MotorAttr>> MAP_CODEC = Codec.unboundedMap(
            Axis.CODEC,//x,y,z代表x,y,z轴平移，xr,yr,zr代表x,y,z轴旋转
            CODEC//关节属性
    );
}
