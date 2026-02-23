package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import lombok.Getter;

@Getter
public class SignalConvertSubsystemStaticAttr extends BasicSubsystemStaticAttr {
    protected SignalConvertSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr) {
        super(basicAttr);
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return null;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.SIGNAL_CONVERT;
    }

}
