package io.github.sweetzonzi.machinemax.common.vehicle.data.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machinemax.common.registry.MMDataRegistries;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.AbstractSubsystemAttr;

import java.util.Map;
import java.util.function.Function;

abstract public class AbstractSubsystemData {
    protected AbstractSubsystemData(){}

    public abstract MapCodec<? extends AbstractSubsystemData> codec();

    public abstract AbstractSubsystemAttr.SubsystemType getType();

    public static final Codec<AbstractSubsystemData> CODEC = MMDataRegistries.getSUBSYSTEM_DATA_CODEC().byNameCodec()
            .dispatch(
                    AbstractSubsystemData::codec,
                    Function.identity()
            );

    public static final Codec<Map<String, AbstractSubsystemData>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//子系统名称
            CODEC//子系统属性
    );
}
