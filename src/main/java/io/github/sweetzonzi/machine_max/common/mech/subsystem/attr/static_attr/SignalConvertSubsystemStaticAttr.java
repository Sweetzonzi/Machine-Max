package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import lombok.Getter;

@Getter
public class SignalConvertSubsystemStaticAttr extends BasicSubsystemStaticAttr {
    protected SignalConvertSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr) {
        super(basicAttr, BasicSoundAttr.DEFAULT);
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
