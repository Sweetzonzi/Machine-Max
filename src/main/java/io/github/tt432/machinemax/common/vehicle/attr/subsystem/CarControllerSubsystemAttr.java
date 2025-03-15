package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.MapCodec;

public class CarControllerSubsystemAttr extends AbstractSubsystemAttr{
    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return null;
    }

    @Override
    public SubsystemType getType() {
        return null;
    }
}
