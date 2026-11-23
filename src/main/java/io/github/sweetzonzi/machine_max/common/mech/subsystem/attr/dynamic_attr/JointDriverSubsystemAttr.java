package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.JointDriverSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.JointDriverSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

/** 安装到子零件上的动画关节执行器，绑定该子零件上的一个高级连接点。 */
@Getter
public class JointDriverSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final JointDriverSubsystemStaticAttr staticAttribute;
    public final String controlledConnector;

    public static final MapCodec<JointDriverSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            com.mojang.serialization.Codec.STRING.fieldOf("connector").forGetter(JointDriverSubsystemAttr::getControlledConnector)
    ).apply(instance, JointDriverSubsystemAttr::new));

    public JointDriverSubsystemAttr(ResourceLocation modelName, String controlledConnector) {
        super(modelName);
        this.staticAttribute = (JointDriverSubsystemStaticAttr) getStaticAttr();
        this.controlledConnector = controlledConnector;
    }

    @Override
    public void validate(VariantAttr variant, String subPartName, String subsystemName) {
        requireAdvancedConnector(variant, subPartName, subsystemName, controlledConnector);
        SubPartAttr subPart = variant.getSubParts().get(subPartName);
        String locator = subPart.getConnectors().get(controlledConnector).getLocatorName();
        if (!hasLocator(variant, subPartName, locator)) {
            throw locatorNotFound(variant, subPartName, subsystemName, locator);
        }
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public JointDriverSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new JointDriverSubsystem(owner, name, this);
    }
}
