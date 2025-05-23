package io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;

public class TurretControllerSubsystemAttr extends AbstractSubsystemAttr{
    protected TurretControllerSubsystemAttr(
            float basicDurability,
            String hitBox) {
        super(basicDurability, hitBox);
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return null;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.TURRET_CTRL;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return null;
    }
}
