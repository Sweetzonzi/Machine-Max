package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.MapCodec;

public class TurretControllerSubsystemAttr extends AbstractSubsystemAttr{
    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return null;
    }

    @Override
    public SubsystemType getType() {
        return null;
    }
}
