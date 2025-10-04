package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.MotorAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.JointDriverSubsystem;
import lombok.Getter;

import java.util.Map;

@Getter
public class JointDriverSubsystemAttr extends AbstractSubsystemAttr {
    public final String controlledConnector;
    public final String rotationOrder;
    public final Map<Integer, MotorAttr> axisParams;

    public static final MapCodec<JointDriverSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.fieldOf("locator").forGetter(JointDriverSubsystemAttr::getControlledConnector),
            Codec.STRING.fieldOf("rotation_order").forGetter(JointDriverSubsystemAttr::getRotationOrder),
            MotorAttr.MAP_CODEC.fieldOf("axes").forGetter(JointDriverSubsystemAttr::getAxisParams)
    ).apply(instance, JointDriverSubsystemAttr::new
    ));

    public JointDriverSubsystemAttr(
            float basicDurability, 
            String controlledConnector,
            String rotationOrder,
            Map<Integer, MotorAttr> axisParams) {
        super(basicDurability);
        this.controlledConnector = controlledConnector;
        this.rotationOrder = rotationOrder;
        this.axisParams = axisParams;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.JOINT  ;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new JointDriverSubsystem(owner, name, this);
    }
}