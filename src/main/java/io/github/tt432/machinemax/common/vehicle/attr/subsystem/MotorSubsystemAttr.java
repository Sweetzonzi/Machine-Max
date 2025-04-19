package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.attr.MotorAttr;
import io.github.tt432.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.tt432.machinemax.common.vehicle.subsystem.MotorSubsystem;
import lombok.Getter;

import java.util.Map;

@Getter
public class MotorSubsystemAttr extends AbstractSubsystemAttr {
    public final String controlledConnector;
    public final String rotationOrder;
    public final Map<Integer, MotorAttr> axisParams;

    public static final MapCodec<MotorSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 100f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("hit_box", "").forGetter(AbstractSubsystemAttr::getHitBox),
            Codec.STRING.fieldOf("connector").forGetter(MotorSubsystemAttr::getControlledConnector),
            Codec.STRING.fieldOf("rotation_order").forGetter(MotorSubsystemAttr::getRotationOrder),
            MotorAttr.MAP_CODEC.fieldOf("axes").forGetter(MotorSubsystemAttr::getAxisParams)
    ).apply(instance, MotorSubsystemAttr::new
    ));

    public MotorSubsystemAttr(
            float basicDurability,
            String hitBox,
            String controlledConnector,
            String rotationOrder,
            Map<Integer, MotorAttr> axisParams) {
        super(basicDurability, hitBox);
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
        return SubsystemType.MOTOR;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new MotorSubsystem(owner, name, this);
    }
}