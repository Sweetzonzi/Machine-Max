package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

//TODO
@Getter
public class SignalConvertSubsystemAttr extends BasicSubsystemDynamicAttr{
    protected SignalConvertSubsystemAttr(
            ResourceLocation modelName) {
        super(modelName);
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return null;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.SIGNAL_CONVERT;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return null;
    }
}
