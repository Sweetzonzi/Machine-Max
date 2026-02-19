package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import lombok.Getter;

import java.util.List;

@Getter
public class WheelDriverSubsystemStaticAttr extends AbstractSubsystemStaticAttr {
    public final List<String> controlSignalKeys;
    public final StaticWheelRollingAxisAttr rollingAxis;
    public final StaticWheelSteeringAxisAttr steeringAxis;

    public static final MapCodec<WheelDriverSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(AbstractSubsystemStaticAttr::getBasicDurability),
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
            )).forGetter(WheelDriverSubsystemStaticAttr::getSteeringAxis)
    ).apply(instance, WheelDriverSubsystemStaticAttr::new
    ));

    public WheelDriverSubsystemStaticAttr(
            float basicDurability,
            List<String> controlSignalKeys,
            StaticWheelRollingAxisAttr rollingAxis,
            StaticWheelSteeringAxisAttr steeringAxis) {
        super(basicDurability);
        this.controlSignalKeys = controlSignalKeys;
        this.rollingAxis = rollingAxis;
        this.steeringAxis = steeringAxis;
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
