package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;

public class TurretControllerSubsystemStaticAttr extends BasicSubsystemStaticAttr {
    protected TurretControllerSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr) {
        super(basicAttr, BasicSoundAttr.DEFAULT);
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return null;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.TURRET_CTRL;
    }

}
