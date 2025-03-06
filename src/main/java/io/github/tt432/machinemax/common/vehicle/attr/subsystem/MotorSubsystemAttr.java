package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Getter
public class MotorSubsystemAttr extends AbstractSubsystemAttr {
    public final String controlledConnector;
    public final String axis;
    public final String rotationOrder;
    public final List<String> targetSpeedSignalKey;
    public final List<String> targetPositionSignalKey;
    @Nullable
    public final Float lowerLimit;
    @Nullable
    public final Float upperLimit;
    @Nullable
    public final Float equilibrium;
    @Nullable
    public final Float stiffness;
    @Nullable
    public final Float damping;
    public final Map<String, String> speedSignalOutputs;
    public final Map<String, String> positionSignalOutputs;

    public static final Codec<Map<String, String>> SIGNAL_OUTPUTS_CODEC = Codec.unboundedMap(
            Codec.STRING,
            Codec.STRING
    );

    public static final MapCodec<MotorSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("connector").forGetter(MotorSubsystemAttr::getControlledConnector),
            Codec.STRING.fieldOf("axis").forGetter(MotorSubsystemAttr::getAxis),
            Codec.STRING.fieldOf("rotation_order").forGetter(MotorSubsystemAttr::getRotationOrder),
            Codec.STRING.listOf().optionalFieldOf("speed_control_inputs", List.of())
                    .forGetter(MotorSubsystemAttr::getTargetSpeedSignalKey),
            Codec.STRING.listOf().optionalFieldOf("position_control_inputs", List.of())
                    .forGetter(MotorSubsystemAttr::getTargetPositionSignalKey),
            Codec.FLOAT.optionalFieldOf("lower_limit")
                    .forGetter(j -> Optional.ofNullable(j.getLowerLimit())),
            Codec.FLOAT.optionalFieldOf("upper_limit")
                    .forGetter(j -> Optional.ofNullable(j.getUpperLimit())),
            Codec.FLOAT.optionalFieldOf("equilibrium")
                    .forGetter(j -> Optional.ofNullable(j.getEquilibrium())),
            Codec.FLOAT.optionalFieldOf("stiffness")
                    .forGetter(j -> Optional.ofNullable(j.getStiffness())),
            Codec.FLOAT.optionalFieldOf("damping")
                    .forGetter(j -> Optional.ofNullable(j.getDamping())),
            SIGNAL_OUTPUTS_CODEC.optionalFieldOf("speed_outputs", Map.of()).forGetter(MotorSubsystemAttr::getSpeedSignalOutputs),
            SIGNAL_OUTPUTS_CODEC.optionalFieldOf("position_outputs", Map.of()).forGetter(MotorSubsystemAttr::getPositionSignalOutputs)
    ).apply(instance, (controlled_connector,axis, rotation_order, target_speed, target_pos, lower, upper, equi, stiff, damp, speed_outputs, pos_outputs) ->
            new MotorSubsystemAttr(
                    controlled_connector,
                    axis,
                    rotation_order,
                    target_speed,
                    target_pos,
                    lower.orElse(null),
                    upper.orElse(null),
                    equi.orElse(null),
                    stiff.orElse(null),
                    damp.orElse(null),
                    speed_outputs,
                    pos_outputs
            )
    ));

    public MotorSubsystemAttr(
            String controlledConnector,
            String axis,
            String rotationOrder,
            List<String> targetSpeedSignalKey,
            List<String> targetPositionSignalKey,
            @Nullable Float lowerLimit,
            @Nullable Float upperLimit,
            @Nullable Float equilibrium,
            @Nullable Float stiffness,
            @Nullable Float damping,
            Map<String, String> speedSignalOutputs,
            Map<String, String> positionSignalOutputs) {
        this.controlledConnector = controlledConnector;
        this.axis = axis;
        this.rotationOrder = rotationOrder;
        this.targetSpeedSignalKey = targetSpeedSignalKey;
        this.targetPositionSignalKey = targetPositionSignalKey;
        this.lowerLimit = lowerLimit;
        this.upperLimit = upperLimit;
        this.equilibrium = equilibrium;
        this.stiffness = stiffness;
        this.damping = damping;
        this.speedSignalOutputs = speedSignalOutputs;
        this.positionSignalOutputs = positionSignalOutputs;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.MOTOR;
    }
}