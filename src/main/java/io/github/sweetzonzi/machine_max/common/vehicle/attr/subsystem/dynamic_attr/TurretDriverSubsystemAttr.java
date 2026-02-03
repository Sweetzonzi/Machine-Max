package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.MotorAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.TurretDriverSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

//TODO
@Getter
public class TurretDriverSubsystemAttr extends AbstractSubsystemAttr {
    public final TurretDriverSubsystemStaticAttr staticAttribute;
    public String controlledConnector;
    public final MotorAttr pitchAxis;
    public final MotorAttr yawAxis;

    public static final MapCodec<TurretDriverSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.fieldOf("locator").forGetter(TurretDriverSubsystemAttr::getControlledConnector),
            MotorAttr.CODEC.fieldOf("roll").forGetter(TurretDriverSubsystemAttr::getPitchAxis),
            MotorAttr.CODEC.fieldOf("steering").forGetter(TurretDriverSubsystemAttr::getYawAxis)
    ).apply(instance, TurretDriverSubsystemAttr::new
    ));

    public TurretDriverSubsystemAttr(
            ResourceLocation modelName,
            String controlledConnector,
            MotorAttr pitchAxis,
            MotorAttr yawAxis) {
        super(modelName);
        this.staticAttribute = (TurretDriverSubsystemStaticAttr) getStaticAttr();
        this.controlledConnector = controlledConnector;
        this.pitchAxis = pitchAxis;
        this.yawAxis = yawAxis;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.TURRET;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return null;
    }
}
