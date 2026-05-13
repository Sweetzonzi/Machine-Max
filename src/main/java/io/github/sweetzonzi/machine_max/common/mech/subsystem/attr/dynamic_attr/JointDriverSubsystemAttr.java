package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.MotorAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.JointDriverSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.JointDriverSubsystem;
import io.github.sweetzonzi.machine_max.util.data.Axis;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

@Getter
public class JointDriverSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final JointDriverSubsystemStaticAttr staticAttribute;
    public final String controlledConnector;
    public final String rotationOrder;
    public final Map<Axis, MotorAttr> axisParams;

    public static final MapCodec<JointDriverSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.fieldOf("locator").forGetter(JointDriverSubsystemAttr::getControlledConnector),
            Codec.STRING.fieldOf("rotation_order").forGetter(JointDriverSubsystemAttr::getRotationOrder),
            MotorAttr.MAP_CODEC.fieldOf("axes").forGetter(JointDriverSubsystemAttr::getAxisParams)
    ).apply(instance, JointDriverSubsystemAttr::new
    ));

    public JointDriverSubsystemAttr(
            ResourceLocation modelName,
            String controlledConnector,
            String rotationOrder,
            Map<Axis, MotorAttr> axisParams) {
        super(modelName);
        this.staticAttribute = (JointDriverSubsystemStaticAttr) getStaticAttr();
        this.controlledConnector = controlledConnector;
        this.rotationOrder = rotationOrder;
        this.axisParams = axisParams;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.JOINT  ;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new JointDriverSubsystem(owner, name, this);
    }
}