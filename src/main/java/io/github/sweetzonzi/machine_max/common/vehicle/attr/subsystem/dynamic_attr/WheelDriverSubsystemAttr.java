package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.WheelDriverSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.WheelDriverSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

@Getter
public class WheelDriverSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final WheelDriverSubsystemStaticAttr staticAttribute;
    public final String controlledConnector;
    public final Map<String, List<String>> rollingSpeedOutputs;
    public final Map<String, List<String>> steeringAngleOutputs;

    public static final MapCodec<WheelDriverSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.fieldOf("connector").forGetter(WheelDriverSubsystemAttr::getControlledConnector),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("roll_speed_outputs", Map.of()).forGetter(WheelDriverSubsystemAttr::getRollingSpeedOutputs),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("steering_angle_outputs", Map.of()).forGetter(WheelDriverSubsystemAttr::getSteeringAngleOutputs)
    ).apply(instance, WheelDriverSubsystemAttr::new
    ));

    public WheelDriverSubsystemAttr(
            ResourceLocation modelName,
            String controlledConnector,
            Map<String, List<String>> rollingSpeedOutputs,
            Map<String, List<String>> steeringAngleOutputs) {
        super(modelName);
        this.staticAttribute = (WheelDriverSubsystemStaticAttr) getStaticAttr();
        this.controlledConnector = controlledConnector;
        this.rollingSpeedOutputs = rollingSpeedOutputs;
        this.steeringAngleOutputs = steeringAngleOutputs;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.WHEEL;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new WheelDriverSubsystem(owner, name, this);
    }
}
