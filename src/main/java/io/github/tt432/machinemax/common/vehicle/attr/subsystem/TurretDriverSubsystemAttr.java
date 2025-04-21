package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.attr.MotorAttr;
import io.github.tt432.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import lombok.Getter;

@Getter
public class TurretDriverSubsystemAttr extends AbstractSubsystemAttr {
    public final String controlledConnector;
    public final MotorAttr pitchAxis;
    public final MotorAttr yawAxis;

    public static final MapCodec<TurretDriverSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 100f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("hit_box", "").forGetter(AbstractSubsystemAttr::getHitBox),
            Codec.STRING.fieldOf("connector").forGetter(TurretDriverSubsystemAttr::getControlledConnector),
            MotorAttr.CODEC.fieldOf("roll").forGetter(TurretDriverSubsystemAttr::getPitchAxis),
            MotorAttr.CODEC.fieldOf("steering").forGetter(TurretDriverSubsystemAttr::getYawAxis)
    ).apply(instance, TurretDriverSubsystemAttr::new
    ));

    public TurretDriverSubsystemAttr(
            float basicDurability,
            String hitBox,
            String controlledConnector,
            MotorAttr pitchAxis,
            MotorAttr yawAxis) {
        super(basicDurability, hitBox);
        this.controlledConnector = controlledConnector;
        this.pitchAxis = pitchAxis;
        this.yawAxis = yawAxis;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.TURRET;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return null;
    }
}
