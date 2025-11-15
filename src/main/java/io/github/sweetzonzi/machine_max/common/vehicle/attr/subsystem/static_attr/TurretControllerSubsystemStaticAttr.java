package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;

public class TurretControllerSubsystemStaticAttr extends AbstractSubsystemStaticAttr {
    protected TurretControllerSubsystemStaticAttr(
            float basicDurability) {
        super(basicDurability);
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
