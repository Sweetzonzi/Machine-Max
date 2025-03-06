package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.MapCodec;
import lombok.Getter;

@Getter
public class SignalConvertSubsystemAttr extends AbstractSubsystemAttr{
    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return null;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.SIGNAL_CONVERT;
    }
}
