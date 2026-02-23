package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import lombok.Getter;

import java.util.List;

@Getter
public class WheelDriverSubsystemStaticAttr extends BasicSubsystemStaticAttr {
    public final List<String> controlSignalKeys;
    public final StaticWheelRollingAxisAttr rollingAxis;
    public final StaticWheelSteeringAxisAttr steeringAxis;
    public final boolean absEnabled;
    public final float absTargetSlipRatio;
    public final float absWheelRadius;

    public static final MapCodec<WheelDriverSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("wheel_control", "move_control")).forGetter(WheelDriverSubsystemStaticAttr::getControlSignalKeys),
            StaticWheelRollingAxisAttr.CODEC.optionalFieldOf("roll", new StaticWheelRollingAxisAttr(
                    10000f,
                    3500f,
                    0f,
                    3140f
            )).forGetter(WheelDriverSubsystemStaticAttr::getRollingAxis),
            StaticWheelSteeringAxisAttr.CODEC.optionalFieldOf("steering", new StaticWheelSteeringAxisAttr(
                    2000f,
                    3.14f
            )).forGetter(WheelDriverSubsystemStaticAttr::getSteeringAxis),
            Codec.BOOL.optionalFieldOf("abs_enabled", false).forGetter(WheelDriverSubsystemStaticAttr::isAbsEnabled),
            Codec.FLOAT.optionalFieldOf("abs_target_slip_ratio", 0.15f).forGetter(WheelDriverSubsystemStaticAttr::getAbsTargetSlipRatio),
            Codec.FLOAT.optionalFieldOf("abs_wheel_radius", 0.3f).forGetter(WheelDriverSubsystemStaticAttr::getAbsWheelRadius)
    ).apply(instance, WheelDriverSubsystemStaticAttr::new
    ));

    public WheelDriverSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr,
            List<String> controlSignalKeys,
            StaticWheelRollingAxisAttr rollingAxis,
            StaticWheelSteeringAxisAttr steeringAxis,
            boolean absEnabled,
            float absTargetSlipRatio,
            float absWheelRadius) {
        super(basicAttr);
        this.controlSignalKeys = controlSignalKeys;
        this.rollingAxis = rollingAxis;
        this.steeringAxis = steeringAxis;
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
        return SubsystemTypes.WHEEL;
    }

    public record StaticWheelRollingAxisAttr(
            float maxForce,
            float maxBrakeForce,
            float maxHandBrakeForce,
            float maxSpeed
    ) {
        public static final Codec<StaticWheelRollingAxisAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("max_drive_force", 10000f).forGetter(StaticWheelRollingAxisAttr::maxForce),
                Codec.FLOAT.optionalFieldOf("max_brake_force", 3500f).forGetter(StaticWheelRollingAxisAttr::maxBrakeForce),
                Codec.FLOAT.optionalFieldOf("max_hand_brake_force", 0f).forGetter(StaticWheelRollingAxisAttr::maxHandBrakeForce),
                Codec.FLOAT.optionalFieldOf("max_speed", 3140f).forGetter(StaticWheelRollingAxisAttr::maxSpeed)
        ).apply(instance, StaticWheelRollingAxisAttr::new));
    }

    public record StaticWheelSteeringAxisAttr(
            float maxForce,
            float maxSpeed
    ) {
        public static final Codec<StaticWheelSteeringAxisAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("max_force", 2000f).forGetter(StaticWheelSteeringAxisAttr::maxForce),
                Codec.FLOAT.optionalFieldOf("max_speed", 3.14f).forGetter(StaticWheelSteeringAxisAttr::maxSpeed)
        ).apply(instance, StaticWheelSteeringAxisAttr::new));
    }
}
