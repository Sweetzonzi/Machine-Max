package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.tt432.machinemax.common.vehicle.subsystem.WheelDriverSubsystem;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class WheelDriverSubsystemAttr extends AbstractSubsystemAttr {
    public final String controlledConnector;
    public final List<String> controlSignalKeys;
    public final WheelRollingAxisAttr rollingAxis;
    public final WheelSteeringAxisAttr steeringAxis;

    public static final MapCodec<WheelDriverSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 100f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("hit_box", "").forGetter(AbstractSubsystemAttr::getHitBox),
            Codec.STRING.fieldOf("connector").forGetter(WheelDriverSubsystemAttr::getControlledConnector),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("wheel_control", "move_control")).forGetter(WheelDriverSubsystemAttr::getControlSignalKeys),
            WheelRollingAxisAttr.CODEC.optionalFieldOf("roll", new WheelRollingAxisAttr(
                    10000f,
                    1500f,
                    3140f,
                    Map.of()
            )).forGetter(WheelDriverSubsystemAttr::getRollingAxis),
            WheelSteeringAxisAttr.CODEC.optionalFieldOf("steering", new WheelSteeringAxisAttr(
                    5000f,
                    3.14f,
                    Map.of()
            )).forGetter(WheelDriverSubsystemAttr::getSteeringAxis)
    ).apply(instance, WheelDriverSubsystemAttr::new
    ));

    public WheelDriverSubsystemAttr(
            float basicDurability,
            String hitBox,
            String controlledConnector,
            List<String> controlSignalKeys,
            WheelRollingAxisAttr rollingAxis,
            WheelSteeringAxisAttr steeringAxis) {
        super(basicDurability, hitBox);
        this.controlledConnector = controlledConnector;
        this.controlSignalKeys = controlSignalKeys;
        this.rollingAxis = rollingAxis;
        this.steeringAxis = steeringAxis;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.WHEEL;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new WheelDriverSubsystem(owner, name, this);
    }

    public record WheelRollingAxisAttr(
            float maxForce,
            float maxBrakeForce,
            float maxSpeed,
            Map<String, List<String>> speedSignalOutputs
    ) {
        public static final Codec<WheelRollingAxisAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("max_drive_force", 10000f).forGetter(WheelRollingAxisAttr::maxForce),
                Codec.FLOAT.optionalFieldOf("max_brake_force", 1500f).forGetter(WheelRollingAxisAttr::maxBrakeForce),
                Codec.FLOAT.optionalFieldOf("max_speed", 3140f).forGetter(WheelRollingAxisAttr::maxSpeed),
                SIGNAL_TARGETS_CODEC.optionalFieldOf("speed_outputs", Map.of()).forGetter(WheelRollingAxisAttr::speedSignalOutputs)
        ).apply(instance, WheelRollingAxisAttr::new));
    }

    public record WheelSteeringAxisAttr(
            float maxForce,
            float maxSpeed,
            Map<String, List<String>> positionSignalOutputs
    ) {
        public static final Codec<WheelSteeringAxisAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("max_force", 5000f).forGetter(WheelSteeringAxisAttr::maxForce),
                Codec.FLOAT.optionalFieldOf("max_speed", 3.14f).forGetter(WheelSteeringAxisAttr::maxSpeed),
                SIGNAL_TARGETS_CODEC.optionalFieldOf("position_outputs", Map.of()).forGetter(WheelSteeringAxisAttr::positionSignalOutputs)
        ).apply(instance, WheelSteeringAxisAttr::new));
    }
}
