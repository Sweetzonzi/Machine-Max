package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.MotorAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import io.github.sweetzonzi.machine_max.util.data.Axis;
import lombok.Getter;

import java.util.Map;

@Getter
public class JointDriverSubsystemStaticAttr extends AbstractSubsystemStaticAttr {
    public final String controlledConnector;
    public final String rotationOrder;
    public final Map<Axis, MotorAttr> axisParams;

    public static final MapCodec<JointDriverSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(AbstractSubsystemStaticAttr::getBasicDurability),
            Codec.STRING.fieldOf("locator").forGetter(JointDriverSubsystemStaticAttr::getControlledConnector),
            Codec.STRING.fieldOf("rotation_order").forGetter(JointDriverSubsystemStaticAttr::getRotationOrder),
            MotorAttr.MAP_CODEC.fieldOf("axes").forGetter(JointDriverSubsystemStaticAttr::getAxisParams)
    ).apply(instance, JointDriverSubsystemStaticAttr::new
    ));

    public JointDriverSubsystemStaticAttr(
            float basicDurability, 
            String controlledConnector,
            String rotationOrder,
            Map<Axis, MotorAttr> axisParams) {
        super(basicDurability);
        this.controlledConnector = controlledConnector;
        this.rotationOrder = rotationOrder;
        this.axisParams = axisParams;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.JOINT  ;
    }

}