package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.MotorAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import lombok.Getter;

@Getter
public class TurretDriverSubsystemStaticAttr extends AbstractSubsystemStaticAttr {
    public final MotorAttr pitchAxis;
    public final MotorAttr yawAxis;

    public static final MapCodec<TurretDriverSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(AbstractSubsystemStaticAttr::getBasicDurability),
            MotorAttr.CODEC.fieldOf("roll").forGetter(TurretDriverSubsystemStaticAttr::getPitchAxis),
            MotorAttr.CODEC.fieldOf("steering").forGetter(TurretDriverSubsystemStaticAttr::getYawAxis)
    ).apply(instance, TurretDriverSubsystemStaticAttr::new
    ));

    public TurretDriverSubsystemStaticAttr(
            float basicDurability,
            MotorAttr pitchAxis,
            MotorAttr yawAxis) {
        super(basicDurability);
        this.pitchAxis = pitchAxis;
        this.yawAxis = yawAxis;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.TURRET;
    }

}
